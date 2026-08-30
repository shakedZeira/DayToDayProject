import { useEffect, useState } from "react";
import { registerSW } from "virtual:pwa-register";
import { getVapidKey, subscribePush } from "./pushApi";

function urlBase64ToUint8Array(base64: string): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - (base64.length % 4)) % 4);
  const base64Padded = (base64 + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64Padded);
  const buffer = new ArrayBuffer(raw.length);
  const array = new Uint8Array(buffer);
  for (let i = 0; i < raw.length; i++) {
    array[i] = raw.charCodeAt(i);
  }
  return array;
}

interface State {
  registered: boolean;
  error?: string;
}

export function useNotifications(token: string): State {
  const [state, setState] = useState<State>({ registered: false });

  useEffect(() => {
    let cancelled = false;
    if (!token || typeof window === "undefined") return;

    const run = async () => {
      try {
        if (!("Notification" in window)) {
          if (!cancelled) setState({ registered: false, error: "Notifications not supported" });
          return;
        }
        if (!("serviceWorker" in navigator) || !("pushManager" in navigator)) {
          if (!cancelled) setState({ registered: false, error: "Push not supported" });
          return;
        }

        registerSW();

        const permission = await Notification.requestPermission();
        if (permission !== "granted") {
          if (!cancelled) setState({ registered: false, error: "Notifications permission denied" });
          return;
        }

        const registration = await navigator.serviceWorker.ready;
        let subscription = await registration.pushManager.getSubscription();

        if (!subscription) {
          const { publicKey } = await getVapidKey(token);
          subscription = await registration.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: urlBase64ToUint8Array(publicKey),
          });
        }

        await subscribePush(token, subscription);
        if (!cancelled) setState({ registered: true });
      } catch (err) {
        if (!cancelled) {
          setState({
            registered: false,
            error: err instanceof Error ? err.message : "Failed to enable notifications",
          });
        }
      }
    };

    run();

    return () => {
      cancelled = true;
    };
  }, [token]);

  return state;
}
