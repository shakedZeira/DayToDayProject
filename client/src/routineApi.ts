import { authedFetch } from "./auth";
import type { RoutineView, RoutineCreateInput } from "shared";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getRoutines(token: string): Promise<RoutineView[]> {
  const res = await authedFetch(token, "/api/routines");
  return json<RoutineView[]>(res);
}

export async function createRoutine(token: string, input: RoutineCreateInput): Promise<RoutineView> {
  const res = await authedFetch(token, "/api/routines", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<RoutineView>(res);
}

export async function updateRoutine(token: string, id: string, input: RoutineCreateInput): Promise<RoutineView> {
  const res = await authedFetch(token, `/api/routines/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<RoutineView>(res);
}

export async function deleteRoutine(token: string, id: string): Promise<void> {
  const res = await authedFetch(token, `/api/routines/${id}`, {
    method: "DELETE",
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
}
