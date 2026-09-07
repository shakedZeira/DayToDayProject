import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { getLLMProvider, extractJson, type LLMProvider } from "./providers/llm";
import type { ItalianLesson, ItalianVocabItem, ItalianPhrase } from "shared";

export function dateKeyFor(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export interface LessonBody {
  title: string;
  tip: string;
  vocab: ItalianVocabItem[];
  phrases: ItalianPhrase[];
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function dateParam(value: unknown): string | null {
  return typeof value === "string" && DATE_RE.test(value) ? value : null;
}

export function fallbackLessonBody(): LessonBody {
  return {
    title: "Daily Italian: greetings",
    tip: "Ciao means both hello and goodbye. Roll your r's — practice 'arrivederci' slowly.",
    vocab: [
      { english: "hello / goodbye", italian: "ciao" },
      { english: "goodbye", italian: "arrivederci" },
      { english: "please", italian: "per favore" },
      { english: "thank you", italian: "grazie" }
    ],
    phrases: [
      { english: "How are you?", italian: "Come stai?" },
      { english: "My name is…", italian: "Mi chiamo…" },
      { english: "I would like a coffee, please.", italian: "Vorrei un caffè, per favore." }
    ]
  };
}

export function lessonPrompt(dateKey: string): string {
  return [
    "You are a friendly Italian tutor. Create today's Italian lesson for a beginner.",
    `Today's date key: ${dateKey}`,
    "Return ONLY a JSON object with this exact shape:",
    '{"title": string, "tip": string, "vocab": [{"english": string, "italian": string}], "phrases": [{"english": string, "italian": string}]}',
    "Use 4 vocab items and 3 example phrases in simple everyday Italian."
  ].join("\n");
}

function isVocabItem(v: unknown): v is ItalianVocabItem {
  return (
    typeof v === "object" &&
    v !== null &&
    typeof (v as ItalianVocabItem).english === "string" &&
    typeof (v as ItalianVocabItem).italian === "string"
  );
}

export function tryParseLesson(text: string): LessonBody | null {
  const json = extractJson(text);
  if (!json) return null;
  if (typeof json.title !== "string" || typeof json.tip !== "string") return null;
  if (!Array.isArray(json.vocab) || !Array.isArray(json.phrases)) return null;
  const vocab = json.vocab.filter(isVocabItem);
  const phrases = json.phrases.filter(isVocabItem);
  if (vocab.length === 0 || phrases.length === 0) return null;
  return { title: json.title, tip: json.tip, vocab, phrases };
}

export async function generateLessonBody(provider: LLMProvider, dateKey: string): Promise<LessonBody> {
  try {
    const raw = await provider.complete(lessonPrompt(dateKey));
    return tryParseLesson(raw) ?? fallbackLessonBody();
  } catch {
    return fallbackLessonBody();
  }
}

function lessonBodyToStore(body: LessonBody): { title: string; tip: string; vocab: string; phrases: string } {
  return {
    title: body.title,
    tip: body.tip,
    vocab: JSON.stringify(body.vocab),
    phrases: JSON.stringify(body.phrases)
  };
}

function lessonFromRow(row: {
  id: string;
  date: string;
  title: string;
  tip: string;
  vocab: string;
  phrases: string;
}): ItalianLesson {
  return {
    id: row.id,
    date: row.date,
    title: row.title,
    tip: row.tip,
    vocab: JSON.parse(row.vocab) as ItalianVocabItem[],
    phrases: JSON.parse(row.phrases) as ItalianPhrase[]
  };
}

async function generateAndStore(provider: LLMProvider, ownerId: string, dateKey: string) {
  const body = await generateLessonBody(provider, dateKey);
  await prisma.lesson.deleteMany({ where: { ownerId, date: dateKey } });
  return prisma.lesson.create({
    data: { ownerId, date: dateKey, ...lessonBodyToStore(body) }
  });
}

export function createItalianRouter(provider: LLMProvider = getLLMProvider()): Router {
  const router = Router();
  router.use(requireAuth);

  router.get("/lesson/today", async (req: AuthedRequest, res) => {
    const dateKey = dateParam(req.query.date) ?? dateKeyFor(new Date());
    const force = req.query.force === "1";
    const existing = force ? null : await prisma.lesson.findFirst({ where: { ownerId: req.userId, date: dateKey } });
    const row = existing ?? (await generateAndStore(provider, req.userId!, dateKey));
    res.json(lessonFromRow(row));
  });

  return router;
}

export const italianRouter = createItalianRouter();
