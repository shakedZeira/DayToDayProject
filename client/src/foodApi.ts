import { authedFetch } from "./auth";
import type { FoodRecord, MealLogInput } from "shared";

export interface MealLogRecord {
  id: string;
  foodId: string;
  grams: number;
  date: string;
  name?: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function searchFoods(token: string, q: string): Promise<FoodRecord[]> {
  const res = await authedFetch(token, "/api/foods/search", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ q }),
  });
  return json<FoodRecord[]>(res);
}

export async function logMeal(token: string, input: MealLogInput): Promise<MealLogRecord> {
  const res = await authedFetch(token, "/api/foods/meals", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<MealLogRecord>(res);
}

export async function deleteMeal(token: string, id: string): Promise<void> {
  const res = await authedFetch(token, `/api/foods/meals/${id}`, { method: "DELETE" });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
}