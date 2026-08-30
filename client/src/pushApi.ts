import { authedFetch } from "./auth";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export interface VapidKeyResponse {
  publicKey: string;
}

export async function getVapidKey(token: string): Promise<VapidKeyResponse> {
  const res = await authedFetch(token, "/api/push/vapid-key");
  return json<VapidKeyResponse>(res);
}

export async function subscribePush(token: string, subscription: PushSubscription): Promise<{ ok: boolean }> {
  const jsonSub = subscription.toJSON() as {
    endpoint: string;
    keys?: { auth?: string; p256dh?: string };
  };
  const res = await authedFetch(token, "/api/push/subscribe", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      endpoint: jsonSub.endpoint,
      keys: {
        auth: jsonSub.keys?.auth ?? "",
        p256dh: jsonSub.keys?.p256dh ?? "",
      },
    }),
  });
  return json<{ ok: boolean }>(res);
}

export async function unsubscribePush(token: string, endpoint: string): Promise<{ ok: boolean }> {
  const res = await authedFetch(token, "/api/push/unsubscribe", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ endpoint }),
  });
  return json<{ ok: boolean }>(res);
}
