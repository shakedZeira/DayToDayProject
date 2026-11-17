import { randomUUID } from "node:crypto";
import type { ErrorRequestHandler, NextFunction, Request, RequestHandler, Response } from "express";

// Structured JSON logging, request ids and an error-reporting hook.
// Sentry is optional: it is only loaded when SENTRY_DSN is set.

export type ErrorReporter = (err: unknown, context?: Record<string, unknown>) => void;

type LogLevel = "info" | "warn" | "error";

/** One JSON object per line on stdout/stderr (easy to search in Render's log viewer). */
export function logJson(level: LogLevel, msg: string, fields: Record<string, unknown> = {}): void {
  if (process.env.LOG_SILENT === "true") return;
  const line = JSON.stringify({ time: new Date().toISOString(), level, msg, ...fields });
  (level === "error" ? process.stderr : process.stdout).write(line + "\n");
}

function serializeError(err: unknown) {
  if (err instanceof Error) return { name: err.name, message: err.message, stack: err.stack };
  return { message: String(err) };
}

let reporters: ErrorReporter[] = [];

/** Register an extra error reporter (used by tests and by Sentry init). */
export function addErrorReporter(r: ErrorReporter): () => void {
  reporters.push(r);
  return () => {
    reporters = reporters.filter((x) => x !== r);
  };
}

/** Log an error as JSON and forward it to every registered reporter. Never throws. */
export const reportError: ErrorReporter = (err, context = {}) => {
  logJson("error", "error", { ...context, err: serializeError(err) });
  for (const r of reporters) {
    try {
      r(err, context);
    } catch {
      /* a failing reporter must not break the request */
    }
  }
};

/** Initialise Sentry only if SENTRY_DSN is set. Returns true when enabled. */
export function initSentry(): boolean {
  const dsn = process.env.SENTRY_DSN;
  if (!dsn) return false;
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const Sentry = require("@sentry/node") as typeof import("@sentry/node");
  Sentry.init({
    dsn,
    environment: process.env.SENTRY_ENVIRONMENT || process.env.NODE_ENV || "development",
    tracesSampleRate: Number(process.env.SENTRY_TRACES_SAMPLE_RATE) || 0,
    sendDefaultPii: false,
  });
  addErrorReporter((err, ctx) => {
    Sentry.captureException(err, { extra: ctx });
  });
  logJson("info", "sentry enabled");
  return true;
}

const REQUEST_ID_RE = /^[A-Za-z0-9._-]{1,128}$/;

/** Assigns req.id (honours a sane inbound x-request-id), echoes it, and logs one line per response. */
export const requestLogger: RequestHandler = (req: Request, res: Response, next: NextFunction) => {
  const inbound = req.headers["x-request-id"];
  const id = typeof inbound === "string" && REQUEST_ID_RE.test(inbound) ? inbound : randomUUID();
  (req as Request & { id?: string }).id = id;
  res.setHeader("x-request-id", id);
  const start = process.hrtime.bigint();
  res.on("finish", () => {
    const status = res.statusCode;
    logJson(status >= 500 ? "error" : status >= 400 ? "warn" : "info", "request", {
      reqId: id,
      method: req.method,
      // path only: query strings can carry tokens or personal data
      path: req.originalUrl.split("?")[0],
      status,
      durationMs: Number(process.hrtime.bigint() - start) / 1e6,
      userId: (req as Request & { userId?: string }).userId,
    });
  });
  next();
};

/** Final Express error handler: reports, then answers 500 JSON with the request id. */
export const errorHandler: ErrorRequestHandler = (err, req, res, next) => {
  const reqId = (req as Request & { id?: string }).id;
  reportError(err, { reqId, method: req.method, path: req.originalUrl.split("?")[0] });
  if (res.headersSent) return next(err);
  const status = typeof err?.status === "number" && err.status >= 400 && err.status < 600 ? err.status : 500;
  res.status(status).json({ error: status >= 500 ? "internal error" : String(err.message ?? "error"), reqId });
};

/** Report crashes that Express cannot see (e.g. rejected promises in async route handlers). */
export function installProcessHandlers(): void {
  process.on("unhandledRejection", (reason) => reportError(reason, { source: "unhandledRejection" }));
  process.on("uncaughtException", (err) => {
    reportError(err, { source: "uncaughtException" });
    process.exitCode = 1;
    setTimeout(() => process.exit(1), 1000).unref();
  });
}
