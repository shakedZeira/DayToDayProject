import { authedFetch } from "./auth";
import type { DigestResponse } from "shared";

export async function getTodayDigest(token: string): Promise<DigestResponse> {
  const res = await authedFetch(token, "/api/digest/today");
  if (!res.ok) throw new Error("failed to load digest");
  return res.json();
}
