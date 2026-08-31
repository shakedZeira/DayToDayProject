import { authedFetch } from "./auth";
import type { WeeklyGoalView } from "shared";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export interface WeeklyGoalInput {
  title: string;
  targetCount: number;
  unit?: string | null;
  category?: string | null;
  autoSource?: string | null;
}

export async function listGoals(token: string): Promise<WeeklyGoalView[]> {
  const res = await authedFetch(token, "/api/goals");
  return json<WeeklyGoalView[]>(res);
}

export async function createGoal(token: string, input: WeeklyGoalInput): Promise<WeeklyGoalView> {
  const res = await authedFetch(token, "/api/goals", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<WeeklyGoalView>(res);
}

export async function updateGoal(token: string, id: string, patch: Partial<WeeklyGoalInput>): Promise<WeeklyGoalView> {
  const res = await authedFetch(token, `/api/goals/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(patch),
  });
  return json<WeeklyGoalView>(res);
}

export async function deleteGoal(token: string, id: string): Promise<void> {
  const res = await authedFetch(token, `/api/goals/${id}`, { method: "DELETE" });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
}

export async function checkoffGoal(token: string, id: string): Promise<WeeklyGoalView> {
  const res = await authedFetch(token, `/api/goals/${id}/checkoff`, { method: "POST" });
  return json<WeeklyGoalView>(res);
}

export async function undoGoal(token: string, id: string): Promise<WeeklyGoalView> {
  const res = await authedFetch(token, `/api/goals/${id}/undo`, { method: "POST" });
  return json<WeeklyGoalView>(res);
}
