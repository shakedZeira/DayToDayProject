import { authedFetch } from "./auth";
import type { FlashcardOut, QuizOut } from "shared";

export interface FlashcardRow extends FlashcardOut {
  id: string;
  createdAt: string;
}
export interface QuizOptionRow { id: string; text: string; }
export interface QuizRow extends QuizOut {
  id: string;
  options: QuizOptionRow[];
  createdAt: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getPdfText(token: string, pdfId: string): Promise<string> {
  const res = await authedFetch(token, `/api/pdfs/${pdfId}/text`);
  const body = await json<{ text: string }>(res);
  return body.text;
}

export async function generateStudy(
  token: string,
  pdfId: string,
  kind: "flashcards" | "quiz",
  count = 5,
  useFullText = false
): Promise<{ cards?: FlashcardOut[]; questions?: QuizOut[] }> {
  const res = await authedFetch(token, `/api/pdfs/${pdfId}/study?kind=${kind}&count=${count}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ useFullText })
  });
  return json<{ cards?: FlashcardOut[]; questions?: QuizOut[] }>(res);
}

export async function getStudy(
  token: string,
  pdfId: string
): Promise<{ cards: FlashcardRow[]; quizzes: QuizRow[] }> {
  return json<{ cards: FlashcardRow[]; quizzes: QuizRow[] }>(
    await authedFetch(token, `/api/pdfs/${pdfId}/study`)
  );
}
