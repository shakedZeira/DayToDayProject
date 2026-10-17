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

export function authedFetch(token: string, path: string, init: RequestInit = {}): Promise<Response> {
  // The server computes "this week" against the *caller's* local Monday. It can
  // only do that if it knows our UTC offset — otherwise a server running in UTC
  // (Render/Docker/Vercel/Termux) drops checkoffs made in the first hours of our
  // Monday. `getTimezoneOffset()` is minutes *west* of UTC, so negate it.
  const tzOffsetMinutes = -new Date().getTimezoneOffset();
  return fetch(path, {
    ...init,
    headers: {
      ...init.headers,
      Authorization: `Bearer ${token}`,
      "x-tz-offset": String(tzOffsetMinutes),
    }
  });
}