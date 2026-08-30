import { Router } from "express";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { prisma } from "./db";

export const authRouter = Router();

const JWT_SECRET = process.env.JWT_SECRET || "dev-secret-change-me";

interface UserPayload {
  id: string;
  email: string;
}

function signToken(user: UserPayload): string {
  return jwt.sign({ id: user.id }, JWT_SECRET, { expiresIn: "7d" });
}

authRouter.post("/register", async (req, res) => {
  const { email, password } = req.body ?? {};
  if (typeof email !== "string" || typeof password !== "string" || password.length < 8) {
    return res.status(400).json({ error: "email and password (min 8 chars) required" });
  }
  const existing = await prisma.user.findUnique({ where: { email } });
  if (existing) return res.status(409).json({ error: "email already registered" });

  const hash = await bcrypt.hash(password, 10);
  const user = await prisma.user.create({ data: { email, password: hash } });
  res.status(201).json({ token: signToken({ id: user.id, email: user.email }), user: { id: user.id, email: user.email } });
});

authRouter.post("/login", async (req, res) => {
  const { email, password } = req.body ?? {};
  if (typeof email !== "string" || typeof password !== "string") {
    return res.status(400).json({ error: "email and password required" });
  }
  const user = await prisma.user.findUnique({ where: { email } });
  if (!user || !(await bcrypt.compare(password, user.password))) {
    return res.status(401).json({ error: "invalid credentials" });
  }
  res.json({ token: signToken({ id: user.id, email: user.email }), user: { id: user.id, email: user.email } });
});
