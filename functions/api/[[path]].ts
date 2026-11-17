// Cloudflare Pages Function: proxies /api/* to the Render API so the browser stays same-origin.
// (Pages `_redirects` cannot proxy to an external host.) Set API_ORIGIN in Pages env vars to override.
const DEFAULT_API_ORIGIN = "https://dtd-api-dsqk.onrender.com";

export const onRequest = async (context: {
  request: Request;
  env: { API_ORIGIN?: string };
}): Promise<Response> => {
  const incoming = new URL(context.request.url);
  const origin = context.env.API_ORIGIN || DEFAULT_API_ORIGIN;
  const target = new URL(incoming.pathname + incoming.search, origin);
  return fetch(new Request(target.toString(), context.request));
};
