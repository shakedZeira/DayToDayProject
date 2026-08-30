/// <reference lib="webworker" />
import { precacheAndRoute } from "workbox-precaching";

const sw = self as unknown as ServiceWorkerGlobalScope;

precacheAndRoute((self as unknown as ServiceWorkerGlobalScope).__WB_MANIFEST);

interface PushPayload {
  title?: string;
  body?: string;
  url?: string;
  icon?: string;
}

sw.addEventListener("push", (event: PushEvent) => {
  let data: PushPayload | null = null;
  try {
    data = event.data ? (event.data.json() as PushPayload) : null;
  } catch {
    data = null;
  }
  const title = data?.title ?? "Day To Day";
  const options: NotificationOptions = {
    body: data?.body ?? "You have a new notification.",
    icon: data?.icon ?? "/favicon.svg",
    badge: "/favicon.svg",
  };
  event.waitUntil(sw.registration.showNotification(title, options));
});

sw.addEventListener("notificationclick", (event: NotificationEvent) => {
  event.notification.close();
  const payload = event.notification.data as PushPayload | undefined;
  const url = payload?.url ?? "/";
  event.waitUntil(
    sw.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ("focus" in client && client.url.includes(url)) {
          return client.focus();
        }
      }
      return sw.clients.openWindow(url);
    })
  );
});

sw.addEventListener("install", () => {
  sw.skipWaiting();
});
