// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from "vitest";
import { authedFetch } from "./auth";
import { getOutbox } from "./outboxRuntime";

afterEach(() => { vi.unstubAllGlobals(); });

describe("authedFetch offline queue", () => {
  it("queues a task mutation on network failure and returns 202", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    const before = getOutbox().getState().pending;
    const res = await authedFetch("tok", "/api/tasks", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title: "x" }),
    });
    expect(res.status).toBe(202);
    await getOutbox().refresh();
    expect(getOutbox().getState().pending).toBe(before + 1);
  });

  it("rethrows for reads and non-queueable writes", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    await expect(authedFetch("tok", "/api/tasks")).rejects.toThrow();
    await expect(authedFetch("tok", "/api/workouts", { method: "POST", body: "{}" })).rejects.toThrow();
  });

  it("passes through server responses untouched", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 400 })));
    const res = await authedFetch("tok", "/api/tasks", { method: "POST", body: "{}" });
    expect(res.status).toBe(400);
  });
});
