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
  return fetch(path, {
    ...init,
    headers: { ...init.headers, Authorization: `Bearer ${token}` }
  });
}