import { authedFetch } from "./auth";
import type { NutritionSummary } from "shared";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getSummary(token: string, date?: string): Promise<NutritionSummary> {
  const qs = date ? `?date=${encodeURIComponent(date)}` : "";
  const res = await authedFetch(token, `/api/nutrition/summary${qs}`);
  return json<NutritionSummary>(res);
}

export async function setTarget(token: string, calories: number): Promise<void> {
  const res = await authedFetch(token, "/api/nutrition/target", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ calories }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
}