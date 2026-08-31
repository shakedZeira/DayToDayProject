import { authedFetch } from "./auth";
import type { ExerciseRecord, ExerciseCreateInput } from "shared";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getExercises(token: string, q?: string): Promise<ExerciseRecord[]> {
  const path = q ? `/api/exercises?q=${encodeURIComponent(q)}` : "/api/exercises";
  const res = await authedFetch(token, path);
  return json<ExerciseRecord[]>(res);
}

export async function createExercise(token: string, input: ExerciseCreateInput): Promise<ExerciseRecord> {
  const res = await authedFetch(token, "/api/exercises", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<ExerciseRecord>(res);
}
