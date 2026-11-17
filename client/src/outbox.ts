// Offline write queue ("outbox") for the PWA.
//
// authedFetch (auth.ts) is the single choke point: when a queueable mutation fails
// at the network level it is stored here and a synthetic 202 response is returned.
// The queue is replayed in FIFO order when connectivity returns. The auth token is
// never persisted in the queue; it is read from storage at replay time.

export interface OutboxItem {
  id: number;
  method: string;
  path: string;
  body: string | null;
  /** Non-secret headers only (Content-Type, x-tz-offset). Authorization is added at replay. */
  headers: Record<string, string>;
  createdAt: number;
}

export type NewOutboxItem = Omit<OutboxItem, "id">;

export interface OutboxStore {
  add(item: NewOutboxItem): Promise<number>;
  all(): Promise<OutboxItem[]>; // ascending id
  remove(id: number): Promise<void>;
}

export interface OutboxState {
  pending: number;
  flushing: boolean;
  /** "auth" when replay stopped because the session expired. */
  paused: "auth" | null;
  /** Visible notes about dropped (poison) items. */
  notes: string[];
}

export const QUEUED_HEADER = "x-dtd-queued";

const QUEUEABLE: Array<{ methods: string[]; re: RegExp }> = [
  { methods: ["POST"], re: /^\/api\/tasks$/ },
  { methods: ["PATCH", "DELETE"], re: /^\/api\/tasks\/[^/?]+$/ },
  { methods: ["POST"], re: /^\/api\/foods\/meals$/ },
  { methods: ["DELETE"], re: /^\/api\/foods\/meals\/[^/?]+$/ },
  { methods: ["POST"], re: /^\/api\/recipes\/[^/?]+\/log$/ },
  { methods: ["POST"], re: /^\/api\/workouts\/[^/?]+\/sets$/ },
  { methods: ["POST"], re: /^\/api\/workouts\/[^/?]+\/exercises\/[^/?]+\/sets$/ },
];

export function isQueueable(path: string, method: string | undefined): boolean {
  const m = (method ?? "GET").toUpperCase();
  const p = path.split("?")[0];
  return QUEUEABLE.some((q) => q.methods.includes(m) && q.re.test(p));
}

/** fetch() rejects with TypeError on network failure (offline, DNS, refused). */
export function isNetworkError(err: unknown): boolean {
  return err instanceof TypeError;
}

export function queuedResponse(): Response {
  return new Response(JSON.stringify({ queued: true }), {
    status: 202,
    headers: { "Content-Type": "application/json", [QUEUED_HEADER]: "1" },
  });
}

export function createMemoryStore(): OutboxStore {
  let next = 1;
  const items: OutboxItem[] = [];
  return {
    async add(item) {
      const id = next++;
      items.push({ ...item, id });
      return id;
    },
    async all() {
      return items.map((i) => ({ ...i }));
    },
    async remove(id) {
      const i = items.findIndex((x) => x.id === id);
      if (i >= 0) items.splice(i, 1);
    },
  };
}

export function createIndexedDbStore(idb: IDBFactory = indexedDB, name = "dtd-outbox"): OutboxStore {
  let dbp: Promise<IDBDatabase> | null = null;
  const open = () =>
    (dbp ??= new Promise<IDBDatabase>((resolve, reject) => {
      const req = idb.open(name, 1);
      req.onupgradeneeded = () => req.result.createObjectStore("items", { keyPath: "id", autoIncrement: true });
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    }));
  const run = async <T>(mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> => {
    const db = await open();
    return new Promise<T>((resolve, reject) => {
      const tx = db.transaction("items", mode);
      const req = fn(tx.objectStore("items"));
      tx.oncomplete = () => resolve(req.result);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  };
  return {
    add: (item) => run("readwrite", (s) => s.add(item)).then((k) => Number(k)),
    all: async () => ((await run("readonly", (s) => s.getAll())) as OutboxItem[]).sort((a, b) => a.id - b.id),
    remove: (id) => run("readwrite", (s) => s.delete(id)).then(() => undefined),
  };
}

export interface OutboxDeps {
  store: OutboxStore;
  fetchFn: (input: string, init: RequestInit) => Promise<Response>;
  /** Current auth token, or null when signed out. */
  getToken: () => string | null;
  now?: () => number;
}

export interface Outbox {
  enqueue(item: NewOutboxItem): Promise<void>;
  flush(): Promise<void>;
  refresh(): Promise<void>;
  dismissNotes(): void;
  /** Drop every queued item (used on logout so writes never replay under another account). */
  clear(): Promise<void>;
  getState(): OutboxState;
  subscribe(fn: (s: OutboxState) => void): () => void;
}

/** 4xx means the server will never accept this request: drop it (except 401 and transient 408/425/429). */
export function isPoison(status: number): boolean {
  return status >= 400 && status < 500 && status !== 401 && status !== 408 && status !== 425 && status !== 429;
}

export function createOutbox(deps: OutboxDeps): Outbox {
  let state: OutboxState = { pending: 0, flushing: false, paused: null, notes: [] };
  const listeners = new Set<(s: OutboxState) => void>();
  let running: Promise<void> | null = null;

  const set = (patch: Partial<OutboxState>) => {
    state = { ...state, ...patch };
    listeners.forEach((l) => l(state));
  };

  const refresh = async () => set({ pending: (await deps.store.all()).length });

  async function describe(item: OutboxItem, res: Response): Promise<string> {
    let detail = "";
    try {
      detail = ((await res.json()) as { error?: string }).error ?? "";
    } catch {
      /* ignore */
    }
    return `Dropped offline change (${item.method} ${item.path}): ${detail || `server said ${res.status}`}`;
  }

  async function run() {
    set({ flushing: true });
    try {
      for (const item of await deps.store.all()) {
        const token = deps.getToken();
        if (!token) {
          set({ paused: "auth" });
          return;
        }
        let res: Response;
        try {
          res = await deps.fetchFn(item.path, {
            method: item.method,
            body: item.body,
            headers: { ...item.headers, Authorization: `Bearer ${token}` },
          });
        } catch {
          return; // still offline: keep order, retry later
        }
        if (res.status === 401) {
          set({ paused: "auth" });
          return;
        }
        if (res.ok) {
          await deps.store.remove(item.id);
        } else if (isPoison(res.status)) {
          const note = await describe(item, res);
          await deps.store.remove(item.id);
          set({ notes: [...state.notes, note] });
        } else {
          return; // 5xx / 408 / 429: retry later, preserve order
        }
        await refresh();
      }
      set({ paused: null });
    } finally {
      await refresh();
      set({ flushing: false });
    }
  }

  return {
    async enqueue(item) {
      await deps.store.add({ ...item, createdAt: item.createdAt ?? (deps.now ?? Date.now)() });
      await refresh();
    },
    flush() {
      return (running ??= run().finally(() => {
        running = null;
      }));
    },
    refresh,
    dismissNotes: () => set({ notes: [] }),
    async clear() {
      for (const queued of await deps.store.all()) await deps.store.remove(queued.id);
      await refresh();
    },
    getState: () => state,
    subscribe(fn) {
      listeners.add(fn);
      fn(state);
      return () => void listeners.delete(fn);
    },
  };
}
