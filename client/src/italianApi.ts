import { authedFetch } from "./auth";
import type { ItalianLesson, ItalianCorrection } from "shared";

interface TranslateResult {
  italian: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getTodaysLesson(
  token: string,
  options?: { force?: boolean }
): Promise<ItalianLesson> {
  const qs = options?.force ? "?force=1" : "";
  const res = await authedFetch(token, `/api/italian/lesson/today${qs}`);
  return json<ItalianLesson>(res);
}

export async function checkAttempt(
  token: string,
  input: { dateKey: string; phraseIndex: number; userAttempt: string }
): Promise<ItalianCorrection> {
  const res = await authedFetch(token, "/api/italian/check", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  return json<ItalianCorrection>(res);
}

export async function translateText(token: string, text: string): Promise<string> {
  const res = await authedFetch(token, "/api/italian/translate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text })
  });
  const result = await json<TranslateResult>(res);
  return result.italian;
}
