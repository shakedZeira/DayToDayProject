import { authedFetch } from "./auth";
import type { ProgressResponse } from "shared";

export async function getProgress(token: string): Promise<ProgressResponse> {
  const res = await authedFetch(token, "/api/progress");
  if (!res.ok) throw new Error("failed to load progress");
  return res.json();
}
