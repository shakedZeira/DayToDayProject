import { authedFetch } from "./auth";
import type { VisionEstimate } from "shared";

export async function analyzePhoto(token: string, file: File): Promise<VisionEstimate> {
  const form = new FormData();
  form.append("image", file);
  const res = await authedFetch(token, "/api/nutrition/analyze-photo", {
    method: "POST",
    body: form,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}
