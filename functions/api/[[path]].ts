// Cloudflare Pages Function: proxies /api/* to the Render API so the browser stays same-origin.
// (Pages `_redirects` cannot proxy to an external host.) Set API_ORIGIN in Pages env vars to override.
//
// Render's free tier sleeps after ~15 min idle and takes up to ~1 minute to wake. While it wakes, the
// first requests fail with 502/503/504 or a connection error before reaching the app, so the request
// was never processed and is safe to retry, including POST /auth/login. We retry for ~50 seconds.
const DEFAULT_API_ORIGIN = "https://dtd-api-dsqk.onrender.com";
const RETRYABLE = new Set([502, 503, 504]);
const DELAYS_MS = [1000, 2000, 3000, 4000, 5000, 5000, 5000, 5000, 5000, 5000, 5000];

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export const onRequest = async (context: {
  request: Request;
  env: { API_ORIGIN?: string };
}): Promise<Response> => {
  const incoming = new URL(context.request.url);
  const origin = context.env.API_ORIGIN || DEFAULT_API_ORIGIN;
  const target = new URL(incoming.pathname + incoming.search, origin).toString();
  // Buffer the body once so it can be re-sent on a retry.
  const hasBody = !["GET", "HEAD"].includes(context.request.method);
  const body = hasBody ? await context.request.arrayBuffer() : undefined;

  let last: Response | null = null;
  for (let attempt = 0; attempt <= DELAYS_MS.length; attempt++) {
    try {
      const res = await fetch(target, {
        method: context.request.method,
        headers: context.request.headers,
        body,
        redirect: "manual",
      });
      if (!RETRYABLE.has(res.status)) return res;
      last = res;
    } catch {
      last = null;
    }
    if (attempt < DELAYS_MS.length) await sleep(DELAYS_MS[attempt]);
  }
  return (
    last ??
    new Response(JSON.stringify({ error: "server is waking up, please try again in a minute" }), {
      status: 503,
      headers: { "Content-Type": "application/json" },
    })
  );
};
