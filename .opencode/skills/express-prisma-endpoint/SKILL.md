---
name: express-prisma-endpoint
description: "Use when adding or modifying a REST API endpoint backed by a Prisma model in this project (server/). Covers the repeatable pattern: Prisma model + shared type, router with requireAuth and ownerId scoping, mounting in index.ts, and a vitest/supertest test. Front-load when writing an Express route or Prisma schema change."
---

# Express + Prisma Endpoint

## Overview

The standard, repeatable way to add a REST endpoint with a Prisma model to this project's `server/`. Every endpoint follows the same five-place pattern so new routes stay consistent, testable, and scoped to the single user.

Core principle: **one resource = one Prisma model + one router file + one test file, all ownerId-scoped, mounted under `/api`.** Do not invent a new structure per endpoint.

## When to Use

- Adding `GET/POST/PATCH/DELETE` routes for a new resource (tasks, workouts, foods, pdfs, highlights, lessons…).
- Adding or changing a model in `server/prisma/schema.prisma`.
- Writing a quick fix to a server route.

## The Pattern (5 places, in order)

### 1. Prisma model — `server/prisma/schema.prisma`

Every app-owned model carries `ownerId` + an index, and timestamps:

```prisma
model Widget {
  id        String   @id @default(cuid())
  ownerId   String
  name      String
  createdAt DateTime @default(now())
  updatedAt DateTime @updatedAt
  @@index([ownerId])
}
```

Then apply:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```

### 2. Shared input type — append to `shared/src/index.ts`

```ts
export interface WidgetCreateInput {
  name: string;
}
```

### 3. Router file — `server/src/widgets.ts`

```ts
import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";

export const widgetsRouter = Router();
widgetsRouter.use(requireAuth);

widgetsRouter.get("/", async (req: AuthedRequest, res) => {
  const rows = await prisma.widget.findMany({
    where: { ownerId: req.userId },
    orderBy: { createdAt: "desc" }
  });
  res.json(rows);
});

widgetsRouter.post("/", async (req: AuthedRequest, res) => {
  const { name } = req.body ?? {};
  if (typeof name !== "string" || !name.trim()) {
    return res.status(400).json({ error: "name required" });
  }
  const row = await prisma.widget.create({
    data: { ownerId: req.userId!, name: name.trim() }
  });
  res.status(201).json(row);
});

widgetsRouter.patch("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.widget.findFirst({
    where: { id: req.params.id, ownerId: req.userId }
  });
  if (!existing) return res.status(404).json({ error: "not found" });

  const { name } = req.body ?? {};
  const data: Record<string, unknown> = {};
  if (typeof name === "string") data.name = name.trim();

  const row = await prisma.widget.update({ where: { id: existing.id }, data });
  res.json(row);
});

widgetsRouter.delete("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.widget.findFirst({
    where: { id: req.params.id, ownerId: req.userId }
  });
  if (!existing) return res.status(404).json({ error: "not found" });
  await prisma.widget.delete({ where: { id: existing.id } });
  res.status(204).end();
});
```

### 4. Mount in `server/src/index.ts`

```ts
import { widgetsRouter } from "./widgets";
app.use("/api/widgets", widgetsRouter);
```

### 5. Test file — `server/src/widgets.test.ts`

```ts
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";

beforeEach(async () => {
  await prisma.widget.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "w@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("create + list", async () => {
  const c = await request(app)
    .post("/api/widgets")
    .set("Authorization", `Bearer ${token}`)
    .send({ name: "a widget" });
  expect(c.status).toBe(201);

  const list = await request(app)
    .get("/api/widgets")
    .set("Authorization", `Bearer ${token}`);
  expect(list.body).toHaveLength(1);
});

test("unauthenticated is rejected", async () => {
  expect((await request(app).get("/api/widgets")).status).toBe(401);
});
```

## Quick Reference

| Concern | Convention |
|---|---|
| Ownership / scoping | `where: { id, ownerId: req.userId }` on every single-row op |
| Validation | hand-validate, return `400` with `{ error }` |
| Not found | return `404` with `{ error: "not found" }` |
| Create success | `201`; reads default `200`; delete `204` |
| Auth | `router.use(requireAuth)`; `req.userId` from `AuthedRequest` |
| Run tests | `npm test --workspace server` |

## Common Mistakes

| Mistake | Fix |
|---|---|
| `findUnique` without `ownerId` (data leak / cross-user) | Always `findFirst({ where: { id, ownerId } })` |
| Editing the schema but forgetting `prisma generate` + `db push` | Run both from the server workspace |
| No index on `ownerId` | Add `@@index([ownerId])` |
| Mounting at the wrong path | Always under `/api/<resource>` |
| Writing test that asserts other users' data | Every `beforeEach` wipes tables and re-registers a fresh token |
