import { authedFetch } from "./auth";
import type {
  AnalyticsSummary,
  ProgressiveOverloadView,
  ProgressionUpdateInput,
} from "shared";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getAnalytics(token: string): Promise<AnalyticsSummary> {
  const res = await authedFetch(token, "/api/workouts/analytics/summary");
  return json<AnalyticsSummary>(res);
}

export async function getProgressions(token: string): Promise<ProgressiveOverloadView[]> {
  const res = await authedFetch(token, "/api/progression");
  return json<ProgressiveOverloadView[]>(res);
}

export async function setProgression(
  token: string,
  exerciseName: string,
  input: ProgressionUpdateInput
): Promise<ProgressiveOverloadView> {
  const res = await authedFetch(token, `/api/progression/${encodeURIComponent(exerciseName)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<ProgressiveOverloadView>(res);
}