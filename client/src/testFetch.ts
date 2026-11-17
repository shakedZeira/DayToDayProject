import { vi } from "vitest";

export interface MockCall {
  method: string;
  path: string;
  body: unknown;
}

type Handler = (call: MockCall) => unknown | Response;

/**
 * Install a fetch mock routed by "METHOD /path" (query string ignored).
 * Handlers return a JSON-serialisable body (200) or a Response. Unmatched
 * requests return 404 {"error":"no mock"} and are recorded in `calls`.
 */
export function mockApi(routes: Record<string, Handler | unknown>) {
  const calls: MockCall[] = [];
  const fn = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const path = String(input).split("?")[0];
    const method = (init.method ?? "GET").toUpperCase();
    const call: MockCall = { method, path, body: init.body ? JSON.parse(String(init.body)) : undefined };
    calls.push(call);
    const route = routes[`${method} ${path}`];
    if (route === undefined) {
      return new Response(JSON.stringify({ error: "no mock" }), { status: 404 });
    }
    const out = typeof route === "function" ? (route as Handler)(call) : route;
    if (out instanceof Response) return out;
    return new Response(JSON.stringify(out), { status: 200, headers: { "Content-Type": "application/json" } });
  });
  vi.stubGlobal("fetch", fn);
  return { calls, fn };
}
