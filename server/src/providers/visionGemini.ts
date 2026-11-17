import type { VisionEstimate, VisionProvider } from "./vision";

export const GEMINI_DEFAULT_MODEL = "gemini-2.5-flash";
export const GEMINI_MAX_IMAGE_BYTES = 7 * 1024 * 1024;
const MAX_ITEMS = 20;
const MAX_GRAMS = 5000;
const MAX_ITEM_CALORIES = 5000;

export class VisionError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = "VisionError";
  }
}

export type GeminiFetch = (
  url: string,
  init: { method: string; headers: Record<string, string>; body: string; signal?: AbortSignal },
) => Promise<{ ok: boolean; status: number; text(): Promise<string> }>;

export interface GeminiOptions {
  apiKey: string;
  model?: string;
  fetchImpl?: GeminiFetch;
  timeoutMs?: number;
}

const PROMPT =
  'You are a nutrition assistant. Identify each distinct food in the photo and estimate its weight in grams and calories. ' +
  'Reply with ONLY JSON of the form {"items":[{"foodName":string,"estimatedGrams":number,"estimatedCalories":number}]}. ' +
  'If there is no food, return {"items":[]}.';

export function mimeFromFilename(filename: string, buf: Buffer): string | null {
  if (buf.length > 3 && buf[0] === 0xff && buf[1] === 0xd8) return "image/jpeg";
  if (buf.length > 8 && buf.subarray(1, 4).toString("latin1") === "PNG") return "image/png";
  if (buf.length > 12 && buf.subarray(8, 12).toString("latin1") === "WEBP") return "image/webp";
  const ext = filename.toLowerCase().split(".").pop();
  if (ext === "jpg" || ext === "jpeg") return "image/jpeg";
  if (ext === "png") return "image/png";
  if (ext === "webp") return "image/webp";
  if (ext === "heic" || ext === "heif") return "image/" + ext;
  return null;
}

function num(v: unknown, max: number): number {
  const n = typeof v === "number" ? v : typeof v === "string" ? Number(v) : NaN;
  if (!Number.isFinite(n) || n < 0) return 0;
  return Math.min(Math.round(n), max);
}

/** Extracts and validates items from model text (tolerates code fences / prose). */
export function parseGeminiItems(text: string): VisionEstimate["items"] {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) throw new VisionError("vision response was not JSON", 502);
  let parsed: unknown;
  try {
    parsed = JSON.parse(text.slice(start, end + 1));
  } catch {
    throw new VisionError("vision response was not valid JSON", 502);
  }
  const raw = (parsed as { items?: unknown })?.items;
  if (!Array.isArray(raw)) throw new VisionError("vision response had no items", 502);
  const items: VisionEstimate["items"] = [];
  for (const r of raw.slice(0, MAX_ITEMS)) {
    if (!r || typeof r !== "object") continue;
    const o = r as Record<string, unknown>;
    const foodName = typeof o.foodName === "string" ? o.foodName.trim().slice(0, 80) : "";
    if (!foodName) continue;
    items.push({
      foodName,
      estimatedGrams: num(o.estimatedGrams, MAX_GRAMS),
      estimatedCalories: num(o.estimatedCalories, MAX_ITEM_CALORIES),
    });
  }
  return items;
}

export function createGeminiProvider(opts: GeminiOptions): VisionProvider {
  const model = opts.model || GEMINI_DEFAULT_MODEL;
  const doFetch: GeminiFetch = opts.fetchImpl ?? ((url, init) => fetch(url, init));
  return {
    name: "gemini",
    async estimateCalories(imageBuffer, filename): Promise<VisionEstimate> {
      if (imageBuffer.length === 0) throw new VisionError("empty image", 400);
      if (imageBuffer.length > GEMINI_MAX_IMAGE_BYTES) throw new VisionError("image too large for vision provider", 413);
      const mime = mimeFromFilename(filename, imageBuffer);
      if (!mime) throw new VisionError("unsupported image type", 415);
      const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
      const body = JSON.stringify({
        contents: [{ parts: [{ text: PROMPT }, { inline_data: { mime_type: mime, data: imageBuffer.toString("base64") } }] }],
        generationConfig: { temperature: 0.2, responseMimeType: "application/json" },
      });
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), opts.timeoutMs ?? 20000);
      let res;
      try {
        res = await doFetch(url, {
          method: "POST",
          headers: { "content-type": "application/json", "x-goog-api-key": opts.apiKey },
          body,
          signal: ctrl.signal,
        });
      } catch (e) {
        const aborted = (e as Error)?.name === "AbortError";
        throw new VisionError(aborted ? "vision provider timed out" : "vision provider unreachable", aborted ? 504 : 502);
      } finally {
        clearTimeout(timer);
      }
      const raw = await res.text();
      if (!res.ok) {
        if (res.status === 429) throw new VisionError("vision provider rate limited", 503);
        throw new VisionError(`vision provider error (${res.status})`, 502);
      }
      let text = "";
      try {
        const j = JSON.parse(raw) as { candidates?: { content?: { parts?: { text?: string }[] } }[] };
        text = (j.candidates?.[0]?.content?.parts ?? []).map((p) => p.text ?? "").join("");
      } catch {
        throw new VisionError("vision provider returned invalid response", 502);
      }
      if (!text) throw new VisionError("vision provider returned no content", 502);
      const items = parseGeminiItems(text);
      return {
        provider: "gemini",
        items,
        totalCalories: items.reduce((s, i) => s + i.estimatedCalories, 0),
        disclaimer: "AI estimate from a photo (Gemini); portions and calories are approximate. Adjust before logging.",
      };
    },
  };
}
