import { isNetworkError, isQueueable, queuedResponse } from "./outbox";
import { getOutbox, requestBackgroundSync } from "./outboxRuntime";

export interface AuthUser {
  id: string;
  email: string;
}
export interface AuthResponse {
  token: string;
  user: AuthUser;
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export const register = (email: string, password: string) =>
  post<AuthResponse>("/api/auth/register", { email, password });

export const login = (email: string, password: string) =>
  post<AuthResponse>("/api/auth/login", { email, password });

export async function authedFetch(token: string, path: string, init: RequestInit = {}): Promise<Response> {
  // The server computes "this week" against the *caller's* local Monday. It can
  // only do that if it knows our UTC offset — otherwise a server running in UTC
  // (Render/Docker/Vercel/Termux) drops checkoffs made in the first hours of our
  // Monday. `getTimezoneOffset()` is minutes *west* of UTC, so negate it.
  const tzOffsetMinutes = -new Date().getTimezoneOffset();
  const baseHeaders = { ...(init.headers as Record<string, string> | undefined), "x-tz-offset": String(tzOffsetMinutes) };
  try {
    return await fetch(path, { ...init, headers: { ...baseHeaders, Authorization: `Bearer ${token}` } });
  } catch (err) {
    // Offline write queue: park supported mutations and replay them later.
    if (isNetworkError(err) && isQueueable(path, init.method) && (init.body == null || typeof init.body === "string")) {
      const outbox = getOutbox();
      await outbox.enqueue({
        method: (init.method ?? "GET").toUpperCase(),
        path,
        body: (init.body as string | null | undefined) ?? null,
        headers: baseHeaders,
        createdAt: Date.now(),
      });
      requestBackgroundSync();
      return queuedResponse();
    }
    throw err;
  }
}
