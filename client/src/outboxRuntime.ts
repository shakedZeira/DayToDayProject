import { createIndexedDbStore, createMemoryStore, createOutbox, type Outbox } from "./outbox";

export const SYNC_TAG = "dtd-outbox";
export const FLUSH_MESSAGE = "dtd-outbox-flush";

function readToken(): string | null {
  return sessionStorage.getItem("dtd.token") ?? localStorage.getItem("dtd.token");
}

let instance: Outbox | null = null;

/** App-wide outbox singleton (IndexedDB when available, memory otherwise). */
export function getOutbox(): Outbox {
  if (!instance) {
    instance = createOutbox({
      store: typeof indexedDB !== "undefined" ? createIndexedDbStore() : createMemoryStore(),
      fetchFn: (input, init) => fetch(input, init),
      getToken: readToken,
    });
  }
  return instance;
}

/** Ask the SW to wake us when connectivity returns (Background Sync; no-op where unsupported). */
export function requestBackgroundSync(): void {
  if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) return;
  navigator.serviceWorker.ready
    .then((reg) => (reg as unknown as { sync?: { register(tag: string): Promise<void> } }).sync?.register(SYNC_TAG))
    .catch(() => {});
}

/** Wire all flush triggers. Returns a cleanup function. */
export function startOutboxSync(outbox: Outbox = getOutbox()): () => void {
  const flush = () => void outbox.flush();
  const onMessage = (e: MessageEvent) => {
    if (e.data === FLUSH_MESSAGE) flush();
  };
  const onVisible = () => document.visibilityState === "visible" && flush();
  window.addEventListener("online", flush);
  document.addEventListener("visibilitychange", onVisible);
  navigator.serviceWorker?.addEventListener("message", onMessage);
  void outbox.refresh().then(() => navigator.onLine && flush());
  return () => {
    window.removeEventListener("online", flush);
    document.removeEventListener("visibilitychange", onVisible);
    navigator.serviceWorker?.removeEventListener("message", onMessage);
  };
}
