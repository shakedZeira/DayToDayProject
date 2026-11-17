import { describe, it, expect, vi } from "vitest";
import { IDBFactory } from "fake-indexeddb";
import {
  createIndexedDbStore, createMemoryStore, createOutbox, isNetworkError, isPoison, isQueueable, queuedResponse,
  type NewOutboxItem,
} from "./outbox";

const item = (path: string, method = "POST", body: string | null = "{}"): NewOutboxItem => ({
  method, path, body, headers: { "Content-Type": "application/json", "x-tz-offset": "120" }, createdAt: 1,
});
const ok = (status = 200, body: unknown = {}) => new Response(JSON.stringify(body), { status });

describe("isQueueable", () => {
  it("accepts tasks, meals and workout set mutations", () => {
    expect(isQueueable("/api/tasks", "POST")).toBe(true);
    expect(isQueueable("/api/tasks/abc", "PATCH")).toBe(true);
    expect(isQueueable("/api/tasks/abc", "DELETE")).toBe(true);
    expect(isQueueable("/api/foods/meals", "POST")).toBe(true);
    expect(isQueueable("/api/recipes/r1/log", "POST")).toBe(true);
    expect(isQueueable("/api/workouts/w1/sets", "POST")).toBe(true);
    expect(isQueueable("/api/workouts/w1/exercises/x1/sets", "POST")).toBe(true);
  });
  it("rejects reads and unsupported mutations", () => {
    expect(isQueueable("/api/tasks", "GET")).toBe(false);
    expect(isQueueable("/api/tasks", undefined)).toBe(false);
    expect(isQueueable("/api/workouts", "POST")).toBe(false);
    expect(isQueueable("/api/recipes/upload", "POST")).toBe(false);
    expect(isQueueable("/api/auth/login", "POST")).toBe(false);
  });
});

describe("helpers", () => {
  it("classifies errors and statuses", () => {
    expect(isNetworkError(new TypeError("Failed to fetch"))).toBe(true);
    expect(isNetworkError(new Error("x"))).toBe(false);
    expect(isPoison(400)).toBe(true);
    expect(isPoison(404)).toBe(true);
    expect(isPoison(401)).toBe(false);
    expect(isPoison(429)).toBe(false);
    expect(isPoison(500)).toBe(false);
  });
  it("queuedResponse is a 202", async () => {
    const r = queuedResponse();
    expect(r.status).toBe(202);
    expect(await r.json()).toEqual({ queued: true });
  });
});

function setup(fetchFn: (p: string, i: RequestInit) => Promise<Response>, token: string | null = "tok") {
  const store = createMemoryStore();
  const outbox = createOutbox({ store, fetchFn, getToken: () => token });
  return { store, outbox };
}

describe("outbox clear", () => {
  it("drops every queued item so nothing replays after logout", async () => {
    const fetchFn = vi.fn().mockResolvedValue(ok());
    const { store, outbox } = setup(fetchFn);
    await outbox.enqueue(item("/api/tasks"));
    await outbox.enqueue(item("/api/tasks"));
    await outbox.clear();
    expect(await store.all()).toEqual([]);
    expect(outbox.getState().pending).toBe(0);
    await outbox.flush();
    expect(fetchFn).not.toHaveBeenCalled();
  });
});

describe("outbox replay", () => {
  it("replays in order, adds auth at replay time, and empties the queue", async () => {
    const calls: Array<[string, RequestInit]> = [];
    const { store, outbox } = setup(async (p, i) => (calls.push([p, i]), ok()));
    await outbox.enqueue(item("/api/tasks"));
    await outbox.enqueue(item("/api/tasks/1", "PATCH"));
    expect(outbox.getState().pending).toBe(2);
    await outbox.flush();
    expect(calls.map((c) => c[1].method)).toEqual(["POST", "PATCH"]);
    expect((calls[0][1].headers as Record<string, string>).Authorization).toBe("Bearer tok");
    expect(JSON.stringify(await store.all())).not.toContain("tok");
    expect(outbox.getState()).toMatchObject({ pending: 0, paused: null, flushing: false });
  });

  it("stops at the first network failure and keeps order", async () => {
    const fetchFn = vi.fn().mockRejectedValue(new TypeError("offline"));
    const { outbox } = setup(fetchFn);
    await outbox.enqueue(item("/api/tasks"));
    await outbox.enqueue(item("/api/tasks"));
    await outbox.flush();
    expect(fetchFn).toHaveBeenCalledTimes(1);
    expect(outbox.getState().pending).toBe(2);
  });

  it("keeps items on 5xx and stops", async () => {
    const fetchFn = vi.fn().mockResolvedValue(ok(503));
    const { outbox } = setup(fetchFn);
    await outbox.enqueue(item("/api/tasks"));
    await outbox.enqueue(item("/api/tasks"));
    await outbox.flush();
    expect(fetchFn).toHaveBeenCalledTimes(1);
    expect(outbox.getState().pending).toBe(2);
  });

  it("drops poison 4xx items with a note and continues", async () => {
    const fetchFn = vi.fn()
      .mockResolvedValueOnce(ok(400, { error: "title required" }))
      .mockResolvedValueOnce(ok(404))
      .mockResolvedValueOnce(ok());
    const { outbox } = setup(fetchFn);
    await outbox.enqueue(item("/api/tasks"));
    await outbox.enqueue(item("/api/tasks/gone", "DELETE", null));
    await outbox.enqueue(item("/api/tasks"));
    await outbox.flush();
    expect(fetchFn).toHaveBeenCalledTimes(3);
    const s = outbox.getState();
    expect(s.pending).toBe(0);
    expect(s.notes).toHaveLength(2);
    expect(s.notes[0]).toContain("title required");
    expect(s.notes[1]).toContain("404");
    outbox.dismissNotes();
    expect(outbox.getState().notes).toEqual([]);
  });

  it("pauses (keeping items) on 401 and resumes after re-auth", async () => {
    let status = 401;
    const { outbox } = setup(async () => ok(status));
    await outbox.enqueue(item("/api/tasks"));
    await outbox.flush();
    expect(outbox.getState()).toMatchObject({ pending: 1, paused: "auth" });
    status = 200;
    await outbox.flush();
    expect(outbox.getState()).toMatchObject({ pending: 0, paused: null });
  });

  it("pauses when signed out without calling fetch", async () => {
    const fetchFn = vi.fn();
    const { outbox } = setup(fetchFn, null);
    await outbox.enqueue(item("/api/tasks"));
    await outbox.flush();
    expect(fetchFn).not.toHaveBeenCalled();
    expect(outbox.getState()).toMatchObject({ pending: 1, paused: "auth" });
  });

  it("does not run concurrent flushes", async () => {
    let release!: () => void;
    const gate = new Promise<void>((r) => (release = r));
    const fetchFn = vi.fn(async () => (await gate, ok()));
    const { outbox } = setup(fetchFn);
    await outbox.enqueue(item("/api/tasks"));
    const a = outbox.flush();
    const b = outbox.flush();
    release();
    await Promise.all([a, b]);
    expect(fetchFn).toHaveBeenCalledTimes(1);
  });

  it("notifies subscribers", async () => {
    const { outbox } = setup(async () => ok());
    const seen: number[] = [];
    outbox.subscribe((s) => seen.push(s.pending));
    await outbox.enqueue(item("/api/tasks"));
    await outbox.flush();
    expect(seen[0]).toBe(0);
    expect(seen).toContain(1);
    expect(seen[seen.length - 1]).toBe(0);
  });
});

describe("IndexedDB store", () => {
  it("persists, orders and removes items", async () => {
    const idb = new IDBFactory();
    const a = createIndexedDbStore(idb);
    const id1 = await a.add(item("/api/tasks"));
    const id2 = await a.add(item("/api/tasks/2", "PATCH"));
    // a second store instance over the same database sees the data (survives reload)
    const b = createIndexedDbStore(idb);
    expect((await b.all()).map((i) => i.id)).toEqual([id1, id2]);
    await b.remove(id1);
    expect((await a.all()).map((i) => i.id)).toEqual([id2]);
  });
});
