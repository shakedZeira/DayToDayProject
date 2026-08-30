import type { Request, Response, NextFunction } from "express";
import jwt from "jsonwebtoken";

const JWT_SECRET = process.env.JWT_SECRET || "dev-secret-change-me";

export interface AuthedRequest extends Request {
  userId?: string;
}

export function requireAuth(req: AuthedRequest, res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  if (!header?.startsWith("Bearer ")) {
    return res.status(401).json({ error: "missing bearer token" });
  }
  try {
    const payload = jwt.verify(header.slice(7), JWT_SECRET) as { id: string };
    req.userId = payload.id;
    next();
  } catch {
    return res.status(401).json({ error: "invalid token" });
  }
}
