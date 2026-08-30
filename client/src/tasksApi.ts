import { authedFetch } from "./auth";
import type { TaskCreateInput } from "shared";

export interface Task {
  id: string;
  ownerId: string;
  title: string;
  notes: string | null;
  status: "PENDING" | "DONE";
  completedAt: string | null;
  dueAt: string | null;
  recurrence: string;
  category: string | null;
  createdAt: string;
  updatedAt: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function listTasks(token: string): Promise<Task[]> {
  const res = await authedFetch(token, "/api/tasks");
  return json<Task[]>(res);
}

export async function createTask(token: string, input: TaskCreateInput): Promise<Task> {
  const res = await authedFetch(token, "/api/tasks", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<Task>(res);
}

export async function updateTask(token: string, id: string, patch: Partial<TaskCreateInput> & { status?: "DONE" | "PENDING" }): Promise<Task> {
  const res = await authedFetch(token, `/api/tasks/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(patch),
  });
  return json<Task>(res);
}

export async function deleteTask(token: string, id: string): Promise<void> {
  const res = await authedFetch(token, `/api/tasks/${id}`, { method: "DELETE" });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
}
