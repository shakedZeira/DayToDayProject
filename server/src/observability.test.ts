import { describe, it, expect, beforeEach, vi } from "vitest";
import express from "express";
import request from "supertest";
import { addErrorReporter, errorHandler, initSentry, reportError, requestLogger } from "./observability";

function makeApp() {
  const app = express();
  app.use(requestLogger);
  app.get("/ok", (_req, res) => res.json({ ok: true }));
  app.get("/bad", (_req, res) => res.status(400).json({ error: "bad" }));
  app.get("/boom", () => {
    throw new Error("kaboom");
  });
  app.use(errorHandler);
  return app;
}

let lines: Record<string, unknown>[];
beforeEach(() => {
  lines = [];
  delete process.env.LOG_SILENT;
  vi.spyOn(process.stdout, "write").mockImplementation((s: string | Uint8Array) => {
    try {
      lines.push(JSON.parse(String(s)));
    } catch {
      /* not ours */
    }
    return true;
  });
  vi.spyOn(process.stderr, "write").mockImplementation((s: string | Uint8Array) => {
    try {
      lines.push(JSON.parse(String(s)));
    } catch {
      /* not ours */
    }
    return true;
  });
  return () => vi.restoreAllMocks();
});

describe("observability", () => {
  it("logs one JSON line per request and sets a request id header", async () => {
    const res = await request(makeApp()).get("/ok?token=secret");
    const id = res.headers["x-request-id"];
    expect(id).toBeTruthy();
    const line = lines.find((l) => l.msg === "request")!;
    expect(line).toMatchObject({ level: "info", method: "GET", path: "/ok", status: 200, reqId: id });
    expect(typeof line.durationMs).toBe("number");
    expect(JSON.stringify(line)).not.toContain("secret");
  });

  it("honours a sane inbound x-request-id and rejects a malformed one", async () => {
    const a = await request(makeApp()).get("/ok").set("x-request-id", "abc-123");
    expect(a.headers["x-request-id"]).toBe("abc-123");
    const b = await request(makeApp()).get("/ok").set("x-request-id", "bad id\twith spaces");
    expect(b.headers["x-request-id"]).not.toBe("bad id\twith spaces");
  });

  it("logs 4xx as warn", async () => {
    await request(makeApp()).get("/bad");
    expect(lines.find((l) => l.msg === "request")).toMatchObject({ level: "warn", status: 400 });
  });

  it("reports thrown errors, returns 500 with the request id and hides details", async () => {
    const seen: unknown[] = [];
    const off = addErrorReporter((e) => seen.push(e));
    const res = await request(makeApp()).get("/boom");
    off();
    expect(res.status).toBe(500);
    expect(res.body).toEqual({ error: "internal error", reqId: res.headers["x-request-id"] });
    expect(seen).toHaveLength(1);
    expect((seen[0] as Error).message).toBe("kaboom");
    expect(lines.some((l) => l.level === "error" && l.msg === "error")).toBe(true);
  });

  it("a failing reporter does not break reportError", () => {
    const off = addErrorReporter(() => {
      throw new Error("reporter down");
    });
    expect(() => reportError(new Error("x"))).not.toThrow();
    off();
  });

  it("does not enable Sentry without SENTRY_DSN", () => {
    delete process.env.SENTRY_DSN;
    expect(initSentry()).toBe(false);
  });

  it("LOG_SILENT suppresses output", async () => {
    process.env.LOG_SILENT = "true";
    await request(makeApp()).get("/ok");
    expect(lines).toHaveLength(0);
  });
});
