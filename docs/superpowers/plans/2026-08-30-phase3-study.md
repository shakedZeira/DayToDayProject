# Phase 3 — Study / PDF Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Context note:** Implement this plan via dispatched subagents (one per task, fresh context) to avoid blowing up the orchestrating agent's context. The orchestrator should review between tasks, not implement inline.

**Goal:** Deliver the Study / PDF hub — upload and store PDFs, view them in-app with text highlights/notes anchored to pages, track per-PDF reading progress, and generate flashcards/quizzes/digests from the user's actual material via a swappable `LLMProvider`.

**Architecture:** PDFs are uploaded over `multipart/form-data` and stored on the server filesystem under `server/storage/`, with metadata in a Prisma `Pdf` row (carrying `ownerId`, so one user owns all rows). Highlights are stored per-Page and per-Pdf in a `Highlight` table. AI generation lives behind the `LLMProvider` abstraction (`server/src/providers/llm.ts`) with a default free-tier implementation switched by the `LLM_PROVIDER` env var; the generated flashcards/quizzes are persisted to `Flashcard`/`Quiz` tables so they can be reviewed later. The client reads/writes through `pdfApi.ts` and renders a `react-pdf` viewer plus a Study view.

**Tech Stack:** `multer` (multipart upload), `pdf-lib` (pageCount extraction server-side), `pdf-parse` (text extraction for learning), `pdfjs-dist` / `react-pdf` (client viewer), Express, Prisma, SQLite, TypeScript, React, Tailwind, Vite.

**Spec:** `docs/superpowers/specs/2026-08-30-personal-daily-companion-design.md`

## Global Constraints

- Everything under `D:\AI Projects\DayToDayProject` (no C: usage).
- TypeScript throughout; lint + typecheck must pass before any commit.
- Single-user app: all rows carry `ownerId` set to the authed user's id.
- Authed routes use `requireAuth` from `server/src/session.ts`.
- AI providers swappable behind abstractions; default free-tier, no paid keys.
- PDFs are stored on the server filesystem under a `storage/` dir (gitignored).
- Windows host (cmd.exe); paths use `\` in commands.

---

### Task 3.1: PDF upload + storage + PDF model

**Files:**
- Modify: `server/prisma/schema.prisma` (add `Pdf` model)
- Create: `server/src/storage.ts` (filesystem helpers)
- Create: `server/src/pdfs.ts` (router: upload/list/metadata/file/delete)
- Create: `server/src/pdfs.test.ts`
- Modify: `server/src/index.ts` (mount router)
- Modify: `.gitignore` (ignore `server/storage/`)

**Interfaces:**
- Consumes: `prisma` from `server/src/db.ts`; `requireAuth`, `AuthedRequest` from `server/src/session.ts`; `multer` middlewares.
- Produces: Prisma model `Pdf`; `server/src/storage.ts` exporting `pdfStorageDir()`, `safePdfPath(fileName): string`, `deletePdfFile(fileName): void`; REST routes `POST /api/pdfs/upload` (multipart, returns `Pdf`), `GET /api/pdfs` (list), `GET /api/pdfs/:id` (metadata `Pdf`), `GET /api/pdfs/:id/file` (streams bytes), `DELETE /api/pdfs/:id` (removes row + file). Exports `pdfsRouter`.

- [ ] **Step 1: Add the Pdf model to the Prisma schema**

Append to `server/prisma/schema.prisma`:
```prisma
model Pdf {
  id          String   @id @default(cuid())
  ownerId     String
  title       String
  fileName    String
  path        String
  size        Int
  pageCount   Int      @default(0)
  lastPage    Int      @default(0)
  readProgress Float   @default(0)
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  highlights  Highlight[]

  @@index([ownerId])
}
```
Run:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```
Expected: client regenerated; `dev.db` updated; exit 0.

- [ ] **Step 2: Ignore the storage directory**

Append to `.gitignore`:
```
server/storage/
```

- [ ] **Step 3: Install multer + pdf-lib**

Add to `server/package.json` dependencies: `"multer": "^1.4.5-lts.1"` and `"pdf-lib": "^1.17.1"`. Add `@types/multer` to devDependencies. Run `npm install`. Also add a `test` script (`"test": "vitest run"`) if not already present (per Phase 0/1 it is).

- [ ] **Step 4: Write the storage helpers**

`server/src/storage.ts`:
```ts
import path from "node:path";
import fs from "node:fs";

const STORAGE_DIR = path.join(process.cwd(), "storage");

export function pdfStorageDir(): string {
  if (!fs.existsSync(STORAGE_DIR)) fs.mkdirSync(STORAGE_DIR, { recursive: true });
  return STORAGE_DIR;
}

export function safePdfPath(fileName: string): string {
  const base = path.basename(fileName);
  return path.join(pdfStorageDir(), base);
}

export function deletePdfFile(fileName: string): void {
  const full = safePdfPath(fileName);
  if (fs.existsSync(full)) fs.unlinkSync(full);
}

export function readPdfFile(fileName: string): Buffer {
  return fs.readFileSync(safePdfPath(fileName));
}

export function pdfFileExists(fileName: string): boolean {
  return fs.existsSync(safePdfPath(fileName));
}
```

- [ ] **Step 5: Write the PDF router**

`server/src/pdfs.ts`:
```ts
import { Router } from "express";
import multer from "multer";
import { PDFDocument } from "pdf-lib";
import path from "node:path";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { safePdfPath, deletePdfFile, readPdfFile, pdfFileExists } from "./storage";

export const pdfsRouter = Router();
pdfsRouter.use(requireAuth);

const upload = multer({
  storage: multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, safePdfPath(".")),
    filename: (_req, file, cb) => {
      const unique = `${Date.now()}-${path.basename(file.originalname).replace(/[^a-zA-Z0-9._-]/g, "_")}`;
      cb(null, unique);
    }
  }),
  limits: { fileSize: 50 * 1024 * 1024 },
  fileFilter: (_req, file, cb) => {
    if (file.mimetype === "application/pdf" || file.originalname.toLowerCase().endsWith(".pdf")) {
      cb(null, true);
    } else {
      cb(new Error("only PDF files are allowed"));
    }
  }
});

async function pageCountOf(filePath: string): Promise<number> {
  try {
    const bytes = await import("node:fs/promises").then((f) => f.readFile(filePath));
    const doc = await PDFDocument.load(bytes, { ignoreEncryption: true });
    return doc.getPageCount();
  } catch {
    return 0;
  }
}

pdfsRouter.post("/upload", upload.single("file"), async (req: AuthedRequest, res) => {
  if (!req.file) return res.status(400).json({ error: "file (multipart field 'file') required" });
  const title = typeof req.body?.title === "string" && req.body.title.trim()
    ? req.body.title.trim()
    : path.basename(req.file.originalname, path.extname(req.file.originalname));
  const pageCount = await pageCountOf(req.file.path);
  const pdf = await prisma.pdf.create({
    data: {
      ownerId: req.userId!,
      title,
      fileName: req.file.filename,
      path: req.file.path,
      size: req.file.size,
      pageCount
    }
  });
  res.status(201).json(pdf);
});

pdfsRouter.get("/", async (req: AuthedRequest, res) => {
  const pdfs = await prisma.pdf.findMany({ where: { ownerId: req.userId }, orderBy: { createdAt: "desc" } });
  res.json(pdfs);
});

pdfsRouter.get("/:id", async (req: AuthedRequest, res) => {
  const pdf = await prisma.pdf.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!pdf) return res.status(404).json({ error: "not found" });
  res.json(pdf);
});

pdfsRouter.get("/:id/file", async (req: AuthedRequest, res) => {
  const pdf = await prisma.pdf.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!pdf || !pdfFileExists(pdf.fileName)) return res.status(404).json({ error: "not found" });
  res.setHeader("Content-Type", "application/pdf");
  res.setHeader("Content-Disposition", `inline; filename="${pdf.fileName}"`);
  res.send(readPdfFile(pdf.fileName));
});

pdfsRouter.delete("/:id", async (req: AuthedRequest, res) => {
  const pdf = await prisma.pdf.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!pdf) return res.status(404).json({ error: "not found" });
  await prisma.highlight.deleteMany({ where: { pdfId: pdf.id } });
  await prisma.pdf.delete({ where: { id: pdf.id } });
  deletePdfFile(pdf.fileName);
  res.status(204).end();
});

export { pageCountOf };
```

- [ ] **Step 6: Mount the router**

In `server/src/index.ts`, import `pdfsRouter` from `./pdfs` and mount:
```ts
app.use("/api/pdfs", pdfsRouter);
```

- [ ] **Step 7: Write the PDF tests**

`server/src/pdfs.test.ts`:
```ts
import { writeFileSync } from "node:fs";
import { PDFDocument } from "pdf-lib";
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";

async function makeSamplePdf(): Promise<Buffer> {
  const doc = await PDFDocument.create();
  doc.addPage([400, 600]);
  doc.addPage([400, 600]);
  return Buffer.from(await doc.save());
}

beforeEach(async () => {
  await prisma.highlight.deleteMany({});
  await prisma.pdf.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "pdf@example.com", password: "password123" });
  token = reg.body.token as string;
});

test("upload, list, fetch metadata, stream file, delete", async () => {
  const buf = await makeSamplePdf();
  const uploadRes = await request(app)
    .post("/api/pdfs/upload")
    .set("Authorization", `Bearer ${token}`)
    .field("title", "my notes")
    .attach("file", buf, "notes.pdf");
  expect(uploadRes.status).toBe(201);
  expect(uploadRes.body.title).toBe("my notes");
  expect(uploadRes.body.pageCount).toBe(2);
  const id = uploadRes.body.id;

  const list = await request(app).get("/api/pdfs").set("Authorization", `Bearer ${token}`);
  expect(list.status).toBe(200);
  expect(list.body).toHaveLength(1);

  const meta = await request(app).get(`/api/pdfs/${id}`).set("Authorization", `Bearer ${token}`);
  expect(meta.status).toBe(200);
  expect(meta.body.fileName).toBe(uploadRes.body.fileName);

  const file = await request(app).get(`/api/pdfs/${id}/file`).set("Authorization", `Bearer ${token}`);
  expect(file.status).toBe(200);
  expect(file.headers["content-type"]).toContain("application/pdf");
  expect(Buffer.byteLength(file.body)).toBeGreaterThan(0);
});

test("rejects a non-pdf upload", async () => {
  const res = await request(app)
    .post("/api/pdfs/upload")
    .set("Authorization", `Bearer ${token}`)
    .attach("file", Buffer.from("not a pdf"), "notes.txt");
  expect(res.status).toBeGreaterThanOrEqual(400);
});

test("rejects unauthenticated access", async () => {
  const res = await request(app).get("/api/pdfs");
  expect(res.status).toBe(401);
});
```
*Note: write a sample file with `writeFileSync` only if the upload route's `pageCountOf` needs one to exist; since `multer` writes the uploaded buffer to disk itself, the test above does not need manual file writes.*

- [ ] **Step 8: Run tests, typecheck, commit**

```bash
npm test --workspace server
npm run typecheck
```
Expected: tests PASS, typecheck clean. Then:
```bash
git add -A
git commit -m "feat(server): pdf upload, storage, list, stream, delete"
```

---

### Task 3.2: PDF viewer in client

**Files:**
- Create: `client/src/pdfApi.ts`
- Create: `client/src/PdfHub.tsx`
- Modify: `client/src/App.tsx` (navigation to Study hub)
- Modify: `client/package.json` (add `react-pdf`)
- Modify: `client/vite.config.ts` (pdfjs worker config)
- Modify: `client/.env` / `client/.env.example` (no new vars required — file URL derived from API path)

**Interfaces:**
- Consumes: `authedFetch` from `client/src/auth.ts`; server PDF routes from Task 3.1; shared names.
- Produces: `client/src/pdfApi.ts` exporting `getPdfs(token)`, `getPdf(token, id)`, `getPdfFileUrl(id)`, `uploadPdf(token, file, title)`, `deletePdf(token, id)` and a `Pdf` client type. `PdfHub.tsx` lists PDFs, uploads via a file input, and renders a `react-pdf` viewer for the selected PDF.

- [ ] **Step 1: Install react-pdf**

Add `"react-pdf": "^9.1.1"` and `"pdfjs-dist": "^4.0.379"` to `client/package.json` dependencies. Run `npm install`.

- [ ] **Step 2: Configure the pdfjs worker in Vite**

In `client/vite.config.ts`, configure the worker source so `react-pdf` finds `pdf.worker`. Add near the top of the config:
```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  optimizeDeps: {
    exclude: ["pdfjs-dist"]
  },
  plugins: [react(), VitePWA({ /* existing config */ })],
  resolve: {
    alias: { shared: fileURLToPath(new URL("../shared/src/index.ts", import.meta.url)) }
  },
  server: { port: 5173, proxy: { "/api": "http://localhost:4000" } }
});
```
*Note: `react-pdf` v9 ships its own worker; import it in the component via `pdfjs.GlobalWorkerOptions.workerSrc` using the `pdfjs-dist` build. If the bundler complains, add `"pdfjs-dist": { "build": true }` to `optimizeDeps` instead of excluding it — pick whichever resolves at build time and keep it consistent.*

- [ ] **Step 3: Write the PDF API client**

`client/src/pdfApi.ts`:
```ts
import { authedFetch } from "./auth";

export interface Pdf {
  id: string;
  ownerId: string;
  title: string;
  fileName: string;
  size: number;
  pageCount: number;
  lastPage: number;
  readProgress: number;
  createdAt: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getPdfs(token: string): Promise<Pdf[]> {
  return json<Pdf[]>(await authedFetch(token, "/api/pdfs"));
}

export async function getPdf(token: string, id: string): Promise<Pdf> {
  return json<Pdf>(await authedFetch(token, `/api/pdfs/${id}`));
}

export function getPdfFileUrl(id: string): string {
  return `/api/pdfs/${id}/file`;
}

export async function uploadPdf(token: string, file: File, title?: string): Promise<Pdf> {
  const form = new FormData();
  form.append("file", file);
  if (title) form.append("title", title);
  const res = await authedFetch(token, "/api/pdfs/upload", { method: "POST", body: form });
  return json<Pdf>(res);
}

export async function deletePdf(token: string, id: string): Promise<void> {
  const res = await authedFetch(token, `/api/pdfs/${id}`, { method: "DELETE" });
  if (!res.ok) throw new Error("delete failed");
}
```
*Note: `getPdfFileUrl` returns a relative `/api` URL; the browser automatically attaches no `Authorization` header to `<embed>`/PDF.js fetches, so the viewer must fetch the bytes via `authedFetch` and pass a Blob URL to `react-pdf` (see Step 4). Keep the relative URL for direct link generation only.*

- [ ] **Step 4: Write the PdfHub component with the in-app viewer**

`client/src/PdfHub.tsx`:
```tsx
import { useCallback, useEffect, useState } from "react";
import { Document, Page } from "react-pdf";
import { pdfjs } from "react-pdf";
import {
  getPdfs, uploadPdf, deletePdf, getPdfFileUrl, type Pdf
} from "./pdfApi";
import { authedFetch } from "./auth";

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url
).toString();

interface Props { token: string; }

export default function PdfHub({ token }: Props) {
  const [pdfs, setPdfs] = useState<Pdf[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [numPages, setNumPages] = useState(0);

  const refresh = useCallback(async () => {
    setPdfs(await getPdfs(token));
  }, [token]);

  useEffect(() => { refresh(); }, [refresh]);

  useEffect(() => {
    if (!selectedId) { setBlobUrl(null); return; }
    let url: string | null = null;
    const load = async () => {
      const res = await authedFetch(token, getPdfFileUrl(selectedId));
      if (!res.ok) return;
      const buf = await res.arrayBuffer();
      url = URL.createObjectURL(new Blob([buf], { type: "application/pdf" }));
      setBlobUrl(url);
    };
    load().catch(() => {});
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [selectedId, token]);

  async function onUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    await uploadPdf(token, file, file.name);
    await refresh();
    e.target.value = "";
  }

  async function onDelete(id: string) {
    await deletePdf(token, id);
    if (selectedId === id) setSelectedId(null);
    await refresh();
  }

  return (
    <div className="w-full max-w-4xl flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <label className="text-indigo-600 underline cursor-pointer text-sm">
          Upload PDF
          <input type="file" accept="application/pdf" className="hidden" onChange={onUpload} />
        </label>
        {selectedId && <span className="text-sm text-slate-400">Select another file to view it.</span>}
      </div>

      <ul className="flex flex-col gap-2">
        {pdfs.map((p) => (
          <li key={p.id} className="flex items-center gap-3 border rounded p-3 bg-white">
            <button className="flex-1 text-left truncate" onClick={() => setSelectedId(p.id)}>
              <span className="font-medium">{p.title}</span>
              <span className="ml-2 text-xs text-slate-400">{p.pageCount} pages</span>
            </button>
            <button className="text-sm text-red-600" onClick={() => onDelete(p.id)}>✕</button>
          </li>
        ))}
      </ul>

      {blobUrl && (
        <div className="border rounded p-4 bg-white">
          <Document
            file={blobUrl}
            onLoadSuccess={({ numPages }) => setNumPages(numPages)}
          >
            {Array.from({ length: numPages }, (_, i) => i + 1).map((p) => (
              <Page key={p} pageNumber={p} className="mb-4 border" />
            ))}
          </Document>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 5: Wire PdfHub into App**

In `client/src/App.tsx`, inside the authed branch, add a navigation section and render `<PdfHub token={token} />` alongside the existing modules (a simple `<a href="#study">Study</a>` style link or a small tab). Import `PdfHub` from `./PdfHub`.

- [ ] **Step 6: Typecheck, build, verify**

```bash
npm run typecheck
npm run build --workspace client
```
Start server + client, log in, upload a PDF, confirm the viewer renders each page.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(client): pdf list upload and in-app viewer"
```

---

### Task 3.3: Highlights + notes per PDF, progress tracking

**Files:**
- Modify: `server/prisma/schema.prisma` (add `Highlight` model)
- Create: `server/src/highlights.ts` (router)
- Create: `server/src/highlights.test.ts`
- Modify: `server/src/pdfs.ts` (add `PATCH /api/pdfs/:id` for progress)
- Modify: `server/src/index.ts` (mount highlights router)
- Modify: `client/src/pdfApi.ts` (highlight + progress helpers)
- Modify: `client/src/PdfHub.tsx` (render highlights, progress bar, save on text selection)

**Interfaces:**
- Consumes: `prisma`; `requireAuth`, `AuthedRequest`; `Pdf` model; existing `pdfsRouter`.
- Produces: Prisma model `Highlight`; REST `POST /api/pdfs/:id/highlights` (create), `GET /api/pdfs/:id/highlights` (list for a pdf), `DELETE /api/highlights/:id`; `PATCH /api/pdfs/:id` accepting `{ lastPage, readProgress }`; client helpers `getHighlights`, `createHighlight`, `deleteHighlight`, `updatePdfProgress`, and `Highlight` client type.

- [ ] **Step 1: Add the Highlight model**

Append to `server/prisma/schema.prisma`:
```prisma
model Highlight {
  id         String   @id @default(cuid())
  pdfId      String
  ownerId    String
  pageNumber Int
  text       String
  note       String?
  color      String   @default("yellow")
  positions  String   @default("[]")
  createdAt  DateTime @default(now())

  pdf        Pdf      @relation(fields: [pdfId], references: [id], onDelete: Cascade)

  @@index([pdfId])
  @@index([ownerId])
}
```
Run:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```

- [ ] **Step 2: Write the highlights router**

`server/src/highlights.ts`:
```ts
import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";

export const highlightsRouter = Router();
highlightsRouter.use(requireAuth);

highlightsRouter.post("/pdfs/:id/highlights", async (req: AuthedRequest, res) => {
  const pdf = await prisma.pdf.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!pdf) return res.status(404).json({ error: "pdf not found" });
  const { pageNumber, text, note, color, positions } = req.body ?? {};
  if (typeof pageNumber !== "number" || typeof text !== "string" || !text.trim()) {
    return res.status(400).json({ error: "pageNumber and text required" });
  }
  const highlight = await prisma.highlight.create({
    data: {
      pdfId: pdf.id,
      ownerId: req.userId!,
      pageNumber,
      text: text.trim(),
      note: typeof note === "string" ? note : null,
      color: typeof color === "string" ? color : "yellow",
      positions: typeof positions === "string" ? positions : "[]"
    }
  });
  res.status(201).json(highlight);
});

highlightsRouter.get("/pdfs/:id/highlights", async (req: AuthedRequest, res) => {
  const pdf = await prisma.pdf.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!pdf) return res.status(404).json({ error: "pdf not found" });
  const highlights = await prisma.highlight.findMany({ where: { pdfId: pdf.id }, orderBy: { pageNumber: "asc" } });
  res.json(highlights);
});

highlightsRouter.delete("/highlights/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.highlight.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!existing) return res.status(404).json({ error: "not found" });
  await prisma.highlight.delete({ where: { id: existing.id } });
  res.status(204).end();
});
```

- [ ] **Step 3: Add the progress PATCH to the pdfs router**

In `server/src/pdfs.ts`, add before the `/:id/file` route (so it doesn't shadow the metadata GET):
```ts
pdfsRouter.patch("/:id", async (req: AuthedRequest, res) => {
  const pdf = await prisma.pdf.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!pdf) return res.status(404).json({ error: "not found" });
  const { lastPage, readProgress } = req.body ?? {};
  const data: Record<string, unknown> = {};
  if (typeof lastPage === "number" && lastPage >= 1) data.lastPage = Math.floor(lastPage);
  if (typeof readProgress === "number") {
    data.readProgress = Math.max(0, Math.min(1, readProgress));
  }
  const updated = await prisma.pdf.update({ where: { id: pdf.id }, data });
  res.json(updated);
});
```

- [ ] **Step 4: Mount the highlights router**

In `server/src/index.ts`, import `highlightsRouter` and mount:
```ts
app.use("/api", highlightsRouter);
```
(routes already begin with `/pdfs/:id/highlights` and `/highlights/:id`, so mounting at `/api` yields `/api/pdfs/:id/highlights` and `/api/highlights/:id`.)

- [ ] **Step 5: Write the highlights tests**

`server/src/highlights.test.ts`:
```ts
import { PDFDocument } from "pdf-lib";
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";
let pdfId = "";

async function seedPdf(): Promise<string> {
  const doc = await PDFDocument.create();
  doc.addPage([400, 600]);
  const buf = Buffer.from(await doc.save());
  const up = await request(app)
    .post("/api/pdfs/upload")
    .set("Authorization", `Bearer ${token}`)
    .attach("file", buf, "h.pdf");
  return up.body.id;
}

beforeEach(async () => {
  await prisma.highlight.deleteMany({});
  await prisma.pdf.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "hl@example.com", password: "password123" });
  token = reg.body.token as string;
  pdfId = await seedPdf();
});

test("create and list highlights for a pdf", async () => {
  const create = await request(app)
    .post(`/api/pdfs/${pdfId}/highlights`)
    .set("Authorization", `Bearer ${token}`)
    .send({ pageNumber: 1, text: "key concept", note: "review", color: "yellow" });
  expect(create.status).toBe(201);
  expect(create.body.text).toBe("key concept");

  const list = await request(app)
    .get(`/api/pdfs/${pdfId}/highlights`)
    .set("Authorization", `Bearer ${token}`);
  expect(list.status).toBe(200);
  expect(list.body).toHaveLength(1);
});

test("delete a highlight", async () => {
  const create = await request(app)
    .post(`/api/pdfs/${pdfId}/highlights`)
    .set("Authorization", `Bearer ${token}`)
    .send({ pageNumber: 2, text: "another point" });
  const id = create.body.id;
  const del = await request(app)
    .delete(`/api/highlights/${id}`)
    .set("Authorization", `Bearer ${token}`);
  expect(del.status).toBe(204);
});

test("updates pdf progress", async () => {
  const res = await request(app)
    .patch(`/api/pdfs/${pdfId}`)
    .set("Authorization", `Bearer ${token}`)
    .send({ lastPage: 1, readProgress: 0.5 });
  expect(res.status).toBe(200);
  expect(res.body.lastPage).toBe(1);
  expect(res.body.readProgress).toBe(0.5);
});
```

- [ ] **Step 6: Add client highlight + progress helpers**

Append to `client/src/pdfApi.ts`:
```ts
export interface Highlight {
  id: string;
  pdfId: string;
  pageNumber: number;
  text: string;
  note: string | null;
  color: string;
  positions: string;
  createdAt: string;
}

export async function getHighlights(token: string, pdfId: string): Promise<Highlight[]> {
  return json<Highlight[]>(await authedFetch(token, `/api/pdfs/${pdfId}/highlights`));
}

export async function createHighlight(
  token: string,
  pdfId: string,
  input: { pageNumber: number; text: string; note?: string | null; color?: string; positions?: string }
): Promise<Highlight> {
  const res = await authedFetch(token, `/api/pdfs/${pdfId}/highlights`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  return json<Highlight>(res);
}

export async function deleteHighlight(token: string, id: string): Promise<void> {
  const res = await authedFetch(token, `/api/highlights/${id}`, { method: "DELETE" });
  if (!res.ok) throw new Error("delete failed");
}

export async function updatePdfProgress(
  token: string,
  id: string,
  input: { lastPage: number; readProgress: number }
): Promise<Pdf> {
  const res = await authedFetch(token, `/api/pdfs/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  return json<Pdf>(res);
}
```

- [ ] **Step 7: Render highlights, progress bar, and selection capture in PdfHub**

Extend `PdfHub.tsx`:
- Load highlights via `getHighlights` whenever the selected PDF changes; keep them in `useState<Highlight[]>`.
- Render a progress bar under the title using `p.readProgress` and `p.pageCount`.
- Add a "save highlight" handler that reads `window.getSelection()?.toString()`, calls `createHighlight` with the current page (track a `currentPage` state via `Page onRenderSuccess` or `pageNumber` prop), then refreshes highlights.
- List highlights with a delete button (`deleteHighlight`).
- Add a note text input per highlight.
- Show a text selection anchored highlight list; full anchor rect rendering is a later polish item — anchor page + text string this phase.

*(Concrete snippet — add inside PdfHub.tsx:)*
```tsx
const [highlights, setHighlights] = useState<Highlight[]>([]);
const [currentPage, setCurrentPage] = useState(1);

useEffect(() => {
  if (!selectedId) { setHighlights([]); return; }
  getHighlights(token, selectedId).then(setHighlights).catch(() => {});
}, [selectedId, token]);

async function saveHighlight() {
  if (!selectedId) return;
  const text = window.getSelection()?.toString().trim();
  if (!text) return;
  const added = await createHighlight(token, selectedId, {
    pageNumber: currentPage, text, color: "yellow", positions: "[]"
  });
  setHighlights((h) => [...h, added]);
  window.getSelection()?.removeAllRanges();
}
```
Add a `saveHighlight` button and the progress bar render; show each highlight with its note and a delete control.

- [ ] **Step 8: Typecheck, build, verify**

```bash
npm run typecheck
npm run build --workspace client
```
Manually verify: upload PDF, select text → highlight saved, progress bar updates on page change (call `updatePdfProgress` on page scroll/change).

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: pdf highlights notes and read progress"
```

---

### Task 3.4: LLMProvider abstraction

**Files:**
- Create: `server/src/providers/llm.ts`
- Create: `server/src/providers/llm.test.ts`
- Modify: `.env` / `.env.example` (`LLM_PROVIDER`, `LLM_HTTP_URL`)

**Interfaces:**
- Consumes: nothing internal; platform `fetch` (Node 18+ global).
- Produces: `server/src/providers/llm.ts` exporting interface `LLMProvider` with `generateFlashcards(material: string, count: number): Promise<FlashcardOut[]>` and `generateQuiz(material: string, count: number): Promise<QuizOut[]>`; the `FlashcardOut` / `QuizOut` shared types; `getLLMProvider(): LLMProvider` that switches on `LLM_PROVIDER` (`"http"` default, `"mock"` for tests). Also `server/src/providers/llm.ts` used by Phase 4 later.

- [ ] **Step 1: Add shared output types**

In `shared/src/index.ts` append:
```ts
export interface FlashcardOut {
  front: string;
  back: string;
}

export interface QuizOut {
  question: string;
  choices: string[];
  answerIndex: number;
  explanation?: string;
}
```

- [ ] **Step 2: Add env keys**

`.env` and `.env.example`:
```
LLM_PROVIDER="http"
LLM_HTTP_URL="https://api.groq.com/openai/v1/chat/completions"
LLM_HTTP_KEY=""
LLM_MODEL="llama-3.1-8b-instant"
```
(Default free-tier: an OpenAI-compatible chat-completions endpoint, e.g. Groq free tier — set `LLM_HTTP_KEY` to a free key; if blank the provider falls back to a mock so the app still works.)

- [ ] **Step 3: Write the LLMProvider**

`server/src/providers/llm.ts`:
```ts
import type { FlashcardOut, QuizOut } from "shared";

export interface LLMProvider {
  generateFlashcards(material: string, count: number): Promise<FlashcardOut[]>;
  generateQuiz(material: string, count: number): Promise<QuizOut[]>;
}

function clampCount(count: number, max: number): number {
  return Math.max(1, Math.min(count, max));
}

function stripCodeFence(text: string): string {
  const match = text.match(/```(?:json)?\s*([\s\S]*?)```/);
  return match ? match[1] : text;
}

async function httpComplete(system: string, user: string): Promise<string> {
  const url = process.env.LLM_HTTP_URL || "https://api.groq.com/openai/v1/chat/completions";
  const key = process.env.LLM_HTTP_KEY || "";
  if (!key) throw new Error("LLM_HTTP_KEY not configured (free-tier key)");
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${key}`
    },
    body: JSON.stringify({
      model: process.env.LLM_MODEL || "llama-3.1-8b-instant",
      temperature: 0.7,
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: system },
        { role: "user", content: user }
      ]
    })
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`LLM request failed ${res.status}: ${body.slice(0, 200)}`);
  }
  const data = await res.json();
  return data.choices?.[0]?.message?.content ?? "{}";
}

export function llmHttpProvider(): LLMProvider {
  return {
    async generateFlashcards(material, count): Promise<FlashcardOut[]> {
      const n = clampCount(count, 20);
      const raw = await httpComplete(
        "You generate study flashcards as JSON. Respond ONLY with JSON of the form {\"cards\":[{\"front\":string,\"back\":string}]}.",
        `Create exactly ${n} flashcards from this material:\n\n${material.slice(0, 12000)}`
      );
      const parsed = JSON.parse(stripCodeFence(raw));
      const cards: FlashcardOut[] = Array.isArray(parsed?.cards) ? parsed.cards : [];
      return cards.slice(0, n).filter((c) => c && typeof c.front === "string" && typeof c.back === "string");
    },
    async generateQuiz(material, count): Promise<QuizOut[]> {
      const n = clampCount(count, 10);
      const raw = await httpComplete(
        "You generate multiple-choice quiz questions as JSON. Respond ONLY with JSON of the form {\"questions\":[{\"question\":string,\"choices\":string[4],\"answerIndex\":number,\"explanation\":string}]}.",
        `Create exactly ${n} questions from this material:\n\n${material.slice(0, 12000)}`
      );
      const parsed = JSON.parse(stripCodeFence(raw));
      const qs: QuizOut[] = Array.isArray(parsed?.questions) ? parsed.questions : [];
      return qs.slice(0, n).filter((q) => q && typeof q.question === "string" && Array.isArray(q.choices) && q.choices.length >= 2 && typeof q.answerIndex === "number");
    }
  };
}

function mockProvider(): LLMProvider {
  return {
    async generateFlashcards(material, count) {
      return useStatefulCards(material, count);
    },
    async generateQuiz(material, count) {
      return useStatefulQuiz(material, count);
    }
  };
}

function cardText(material: string): string {
  const a = material.split(/\s+/).filter(Boolean);
  if (a.length >= 6) return a.slice(0, 6).join(" ");
  return material.slice(0, 40) || "concept";
}

function useStatefulCards(material: string, count: number): FlashcardOut[] {
  const n = Math.max(1, Math.min(count, 20));
  const base = cardText(material);
  return Array.from({ length: n }, (_, i) => ({
    front: `Q${i + 1}: ${base}…`,
    back: `A${i + 1}: Restate the key idea of "${base.slice(0, 24)}…".`
  }));
}

function useStatefulQuiz(material: string, count: number): QuizOut[] {
  const n = Math.max(1, Math.min(count, 10));
  const base = cardText(material);
  return Array.from({ length: n }, (_, i) => ({
    question: `What is a key takeaway from "${base.slice(0, 40)}"?`,
    choices: ["Option A", "Option B", "Option C", "Option D"],
    answerIndex: 1,
    explanation: `Derived from the material: ${base.slice(0, 60)}`
  }));
}

export function getLLMProvider(): LLMProvider {
  const kind = (process.env.LLM_PROVIDER || "http").toLowerCase();
  if (kind === "mock") return mockProvider();
  return llmHttpProvider();
}

export { mockProvider as llmMockProvider };
```

- [ ] **Step 4: Write the provider tests (mocked)**

`server/src/providers/llm.test.ts`:
```ts
import { describe, it, expect } from "vitest";
import { llmMockProvider, getLLMProvider, llmHttpProvider } from "./llm";

describe("llm mock provider", () => {
  it("returns typed flashcards", async () => {
    const p = llmMockProvider();
    const cards = await p.generateFlashcards("mitochondria are the powerhouse of the cell", 2);
    expect(cards).toHaveLength(2);
    expect(cards[0].front).toBeTruthy();
    expect(cards[0].back).toBeTruthy();
    expect(cards[0].answerIndex).toBeUndefined(); // flashcards have no choices
  });

  it("returns typed quizzes", async () => {
    const p = llmMockProvider();
    const qs = await p.generateQuiz("Photosynthesis converts light to chemical energy", 3);
    expect(qs).toHaveLength(3);
    expect(qs[0].choices.length).toBe(4);
    expect(qs[0].answerIndex).toBeGreaterThanOrEqual(0);
  });

  it("clamps counts to provider limits", async () => {
    const p = llmMockProvider();
    const cards = await p.generateFlashcards("x y z", 100);
    expect(cards.length).toBeLessThanOrEqual(20);
  });
});

describe("provider switch", () => {
  it("returns http provider by default and mock when requested", () => {
    process.env.LLM_PROVIDER = "http";
    expect(getLLMProvider().generateFlashcards).toBeTruthy();
    process.env.LLM_PROVIDER = "mock";
    expect(getLLMProvider().generateFlashcards).toBeTruthy();
  });

  it("http provider throws without a configured key", async () => {
    const oldKey = process.env.LLM_HTTP_KEY;
    delete process.env.LLM_HTTP_KEY;
    await expect(llmHttpProvider().generateFlashcards("a b c", 1)).rejects.toThrow(/LLM_HTTP_KEY/);
    if (oldKey !== undefined) process.env.LLM_HTTP_KEY = oldKey;
  });
});
```

- [ ] **Step 5: Run tests, typecheck, commit**

```bash
npm test --workspace server
npm run typecheck
```
Expected: tests PASS, typecheck clean. Then:
```bash
git add -A
git commit -m "feat(server): swappable llm provider abstraction"
```

---

### Task 3.5: Learn-by-context (study view)

**Files:**
- Modify: `server/prisma/schema.prisma` (add `Flashcard` + `Quiz` + `QuizOption` models)
- Modify: `server/src/highlights.ts` or new `server/src/study.ts` (router) — use a new `server/src/study.ts`
- Create: `server/src/study.test.ts`
- Modify: `server/src/index.ts` (mount study router)
- Modify: `server/src/pdfs.ts` (add `GET /api/pdfs/:id/text` extraction via `pdf-parse`)
- Modify: `client/src/pdfApi.ts` / new `client/src/studyApi.ts`
- Create: `client/src/StudyView.tsx`
- Modify: `client/src/PdfHub.tsx` (link to Study view for a PDF)

**Interfaces:**
- Consumes: `prisma`; `requireAuth`, `AuthedRequest`; `getLLMProvider()` and `LLMProvider` from Task 3.4; shared `FlashcardOut`, `QuizOut`; `readPdfFile`/`safePdfPath` from storage; `pdf-parse`.
- Produces: Prisma models `Flashcard`, `Quiz`, `QuizOption`; `GET /api/pdfs/:id/text` (plain text of the PDF, page-truncated); `POST /api/pdfs/:id/study?kind=flashcards|quiz` building material from highlights (plus optional full-text fallback) and persisting generated items; `GET /api/pdfs/:id/study` returning the latest persisted flashcards and quiz for review. Client `studyApi.ts` with `getPdfText`, `generateStudy`, `getStudy`. `StudyView.tsx` renders flashcards (flip) and quiz (select answer).

*Generation decision (explicit): generated cards/quizzes are **persisted** to `Flashcard`/`Quiz` tables (not just returned) so the user can review later; the generator writes by replacing the previous generated set for that PDF. Free-tier limit: material is truncated to the latest highlights + a capped text prefix (~12k chars), with a note that large PDFs may exceed free-tier limits — see constraint step.*

- [ ] **Step 1: Add Flashcard / Quiz models**

Append to `server/prisma/schema.prisma`:
```prisma
model Flashcard {
  id        String   @id @default(cuid())
  pdfId     String
  ownerId   String
  front     String
  back      String
  createdAt DateTime @default(now())

  pdf       Pdf      @relation(fields: [pdfId], references: [id], onDelete: Cascade)

  @@index([pdfId])
}

model Quiz {
  id        String       @id @default(cuid())
  pdfId     String
  ownerId   String
  question  String
  options   QuizOption[]
  answerIndex Int
  createdAt DateTime     @default(now())

  pdf       Pdf          @relation(fields: [pdfId], references: [id], onDelete: Cascade)

  @@index([pdfId])
}

model QuizOption {
  id    String @id @default(cuid())
  quizId String
  text  String

  quiz  Quiz   @relation(fields: [quizId], references: [id], onDelete: Cascade)
}
```
Run:
```bash
npx prisma generate --schema server/prisma/schema.prisma
npx prisma db push --schema server/prisma/schema.prisma
```
Install `pdf-parse`:
```bash
npm install pdf-parse --workspace server
```
Add `@types/pdf-parse` to server devDependencies and run `npm install`.

- [ ] **Step 2: Add PDF text extraction endpoint**

In `server/src/pdfs.ts`, import `readPdfFile` (already imported) and add:
```ts
import pdfParse from "pdf-parse";

pdfsRouter.get("/:id/text", async (req: AuthedRequest, res) => {
  const pdf = await prisma.pdf.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!pdf) return res.status(404).json({ error: "not found" });
  try {
    const data = await pdfParse(readPdfFile(pdf.fileName));
    res.json({ text: data.text });
  } catch {
    res.status(500).json({ error: "text extraction failed" });
  }
});
```
*(Place this route before the `/:id/file`/`/:id` definitions guard order — `/:id/text` is a distinct literal segment so Express matches it fine, but keep it defined above `/:id` for clarity.)*

- [ ] **Step 3: Write the study router**

`server/src/study.ts`:
```ts
import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import { getLLMProvider } from "./providers/llm";

export const studyRouter = Router();
studyRouter.use(requireAuth);

async function loadMaterial(pdfId: string, ownerId: string, fullText?: string): Promise<string> {
  const highlights = await prisma.highlight.findMany({
    where: { pdfId, ownerId },
    orderBy: { pageNumber: "asc" }
  });
  const hlText = highlights.map((h) => `[p${h.pageNumber}] ${h.text}`).join("\n");
  const preferred = hlText.trim()
    ? `USER HIGHLIGHTS (preferred context):\n${hlText}`
    : null;
  const fallback = fullText ? `PDF TEXT:\n${fullText}` : null;
  const material = [preferred, fallback].filter(Boolean).join("\n\n");
  return material.slice(0, 12000);
}

studyRouter.post("/pdfs/:id/study", async (req: AuthedRequest, res) => {
  const pdf = await prisma.pdf.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!pdf) return res.status(404).json({ error: "pdf not found" });

  const kind = req.query.kind === "quiz" ? "quiz" : "flashcards";
  const count = typeof req.query.count === "string" ? Number(req.query.count) || 5 : 5;

  const fullText = req.body?.useFullText
    ? await import("./studyText").then((m) => m.extractPdfText(pdf.fileName)).catch(() => "")
    : "";
  const material = await loadMaterial(pdf.id, req.userId!, fullText);
  if (!material.trim()) {
    return res.status(422).json({ error: "no highlights or text available; add highlights first" });
  }

  const provider = getLLMProvider();

  if (kind === "flashcards") {
    const cards = await provider.generateFlashcards(material, count);
    await prisma.flashcard.deleteMany({ where: { pdfId: pdf.id } });
    for (const c of cards) {
      await prisma.flashcard.create({
        data: { pdfId: pdf.id, ownerId: req.userId!, front: c.front, back: c.back }
      });
    }
    return res.status(201).json({ cards });
  }

  const questions = await provider.generateQuiz(material, count);
  await prisma.quiz.deleteMany({ where: { pdfId: pdf.id } });
  for (const q of questions) {
    await prisma.quiz.create({
      data: {
        pdfId: pdf.id,
        ownerId: req.userId!,
        question: q.question,
        answerIndex: q.answerIndex,
        options: { create: q.choices.map((text) => ({ text })) }
      }
    });
  }
  return res.status(201).json({ questions });
});

studyRouter.get("/pdfs/:id/study", async (req: AuthedRequest, res) => {
  const pdf = await prisma.pdf.findFirst({ where: { id: req.params.id, ownerId: req.userId } });
  if (!pdf) return res.status(404).json({ error: "pdf not found" });
  const [cards, quizzes] = await Promise.all([
    prisma.flashcard.findMany({ where: { pdfId: pdf.id }, orderBy: { createdAt: "asc" } }),
    prisma.quiz.findMany({
      where: { pdfId: pdf.id },
      orderBy: { createdAt: "asc" },
      include: { options: { orderBy: { id: "asc" } } }
    })
  ]);
  res.json({ cards, quizzes });
});
```

- [ ] **Step 4: Create the text extraction helper**

`server/src/studyText.ts`:
```ts
import pdfParse from "pdf-parse";
import { readPdfFile } from "./storage";

export async function extractPdfText(fileName: string): Promise<string> {
  const data = await pdfParse(readPdfFile(fileName));
  return data.text ?? "";
}
```

- [ ] **Step 5: Mount the study router**

In `server/src/index.ts`, import `studyRouter` and mount:
```ts
app.use("/api", studyRouter);
```

- [ ] **Step 6: Write the study tests**

`server/src/study.test.ts`:
```ts
import { PDFDocument } from "pdf-lib";
import { beforeEach, test, expect, vi } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";
let pdfId = "";

beforeEach(async () => {
  await prisma.flashcard.deleteMany({});
  await prisma.quiz.deleteMany({});
  await prisma.highlight.deleteMany({});
  await prisma.pdf.deleteMany({});
  await prisma.user.deleteMany({});
  const reg = await request(app)
    .post("/api/auth/register")
    .send({ email: "study@example.com", password: "password123" });
  token = reg.body.token as string;

  const doc = await PDFDocument.create();
  doc.addPage([400, 600]);
  const up = await request(app)
    .post("/api/pdfs/upload")
    .set("Authorization", `Bearer ${token}`)
    .attach("file", Buffer.from(await doc.save()), "s.pdf");
  pdfId = up.body.id;

  await request(app)
    .post(`/api/pdfs/${pdfId}/highlights`)
    .set("Authorization", `Bearer ${token}`)
    .send({ pageNumber: 1, text: "Key concept from the PDF" });
});

test("generates and persists flashcards from highlights", async () => {
  process.env.LLM_PROVIDER = "mock";
  const res = await request(app)
    .post(`/api/pdfs/${pdfId}/study?kind=flashcards&count=3`)
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(201);
  expect(Array.isArray(res.body.cards)).toBe(true);
  expect(res.body.cards.length).toBe(3);

  const shown = await request(app)
    .get(`/api/pdfs/${pdfId}/study`)
    .set("Authorization", `Bearer ${token}`);
  expect(shown.status).toBe(200);
  expect(shown.body.cards).toHaveLength(3);
});

test("generates and persists a quiz", async () => {
  process.env.LLM_PROVIDER = "mock";
  const res = await request(app)
    .post(`/api/pdfs/${pdfId}/study?kind=quiz&count=2`)
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(201);
  expect(res.body.questions).toHaveLength(2);
  expect(res.body.questions[0].choices).toHaveLength(4);
});

test("returns 422 when there are no highlights", async () => {
  process.env.LLM_PROVIDER = "mock";
  await prisma.highlight.deleteMany({ where: { pdfId } });
  const res = await request(app)
    .post(`/api/pdfs/${pdfId}/study?kind=flashcards`)
    .set("Authorization", `Bearer ${token}`);
  expect(res.status).toBe(422);
});
```

- [ ] **Step 7: Write the client study API**

`client/src/studyApi.ts`:
```ts
import { authedFetch } from "./auth";
import type { FlashcardOut, QuizOut } from "shared";

export interface FlashcardRow extends FlashcardOut {
  id: string;
  createdAt: string;
}
export interface QuizOptionRow { id: string; text: string; }
export interface QuizRow extends QuizOut {
  id: string;
  options: QuizOptionRow[];
  createdAt: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getPdfText(token: string, pdfId: string): Promise<string> {
  const res = await authedFetch(token, `/api/pdfs/${pdfId}/text`);
  const body = await json<{ text: string }>(res);
  return body.text;
}

export async function generateStudy(
  token: string,
  pdfId: string,
  kind: "flashcards" | "quiz",
  count = 5,
  useFullText = false
): Promise<{ cards?: FlashcardOut[]; questions?: QuizOut[] }> {
  const res = await authedFetch(token, `/api/pdfs/${pdfId}/study?kind=${kind}&count=${count}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ useFullText })
  });
  return json<{ cards?: FlashcardOut[]; questions?: QuizOut[] }>(res);
}

export async function getStudy(
  token: string,
  pdfId: string
): Promise<{ cards: FlashcardRow[]; quizzes: QuizRow[] }> {
  return json<{ cards: FlashcardRow[]; quizzes: QuizRow[] }>(
    await authedFetch(token, `/api/pdfs/${pdfId}/study`)
  );
}
```

- [ ] **Step 8: Write the StudyView component**

`client/src/StudyView.tsx`:
```tsx
import { useCallback, useEffect, useState } from "react";
import { getStudy, generateStudy, type FlashcardRow, type QuizRow } from "./studyApi";

interface Props { token: string; pdfId: string; }

export default function StudyView({ token, pdfId }: Props) {
  const [cards, setCards] = useState<FlashcardRow[]>([]);
  const [quizzes, setQuizzes] = useState<QuizRow[]>([]);
  const [flipped, setFlipped] = useState<Record<string, boolean>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [answers, setAnswers] = useState<Record<string, number>>({});

  const refresh = useCallback(async () => {
    const data = await getStudy(token, pdfId);
    setCards(data.cards);
    setQuizzes(data.quizzes);
  }, [token, pdfId]);

  useEffect(() => { refresh().catch(() => {}); }, [refresh]);

  async function onGenerate(kind: "flashcards" | "quiz") {
    setBusy(true); setError(null);
    try {
      await generateStudy(token, pdfId, kind, 5);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "generation failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      <div className="flex gap-3">
        <button onClick={() => onGenerate("flashcards")} disabled={busy}
          className="bg-indigo-600 text-white rounded px-4 py-2 disabled:opacity-50">
          {busy ? "Generating…" : "Generate flashcards"}
        </button>
        <button onClick={() => onGenerate("quiz")} disabled={busy}
          className="bg-emerald-600 text-white rounded px-4 py-2 disabled:opacity-50">
          Generate quiz
        </button>
      </div>
      {error && <p className="text-red-600 text-sm">{error}</p>}

      <div>
        <h3 className="font-semibold text-slate-700 mb-2">Flashcards</h3>
        {cards.length === 0 && <p className="text-sm text-slate-400">No cards yet.</p>}
        <ul className="flex flex-col gap-2">
          {cards.map((c) => {
            const f = flipped[c.id] ?? false;
            return (
              <li key={c.id} onClick={() => setFlipped((p) => ({ ...p, [c.id]: !f }))}
                className="cursor-pointer border rounded p-4 bg-white min-h-20">
                {f ? <p className="text-slate-700">{c.back}</p> : <p className="font-medium">{c.front}</p>}
                <p className="text-xs text-slate-400 mt-2">{f ? "click to flip" : "click to reveal"}</p>
              </li>
            );
          })}
        </ul>
      </div>

      <div>
        <h3 className="font-semibold text-slate-700 mb-2">Quiz</h3>
        {quizzes.length === 0 && <p className="text-sm text-slate-400">No questions yet.</p>}
        <ul className="flex flex-col gap-4">
          {quizzes.map((q) => (
            <li key={q.id} className="border rounded p-4 bg-white">
              <p className="font-medium mb-2">{q.question}</p>
              {q.options.map((opt, i) => {
                const chosen = answers[q.id] === i;
                const isCorrect = chosen && i === q.answerIndex;
                return (
                  <button key={opt.id}
                    onClick={() => setAnswers((p) => ({ ...p, [q.id]: i }))}
                    className={`block w-full text-left border rounded px-3 py-1 mb-1 ${
                      chosen ? (isCorrect ? "bg-green-100 border-green-400" : "bg-red-100 border-red-400")
                      : "bg-slate-50"}`}>
                    {opt.text}
                  </button>
                );
              })}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
```
*Free-tier constraint note: for large PDFs, truncate material server-side to ~12k chars (Task 3.5 Step 3) so free-tier contexts stay in budget; users can click "Generate" which prefers highlights and falls back to a text prefix. Document this on the Study view with a small hint line.*

- [ ] **Step 9: Wire StudyView into PdfHub**

In `PdfHub.tsx`, when a PDF is selected, show a link/button toggling a `<StudyView token={token} pdfId={selectedId} />` panel below the viewer. Import `StudyView` from `./StudyView`.

- [ ] **Step 10: Typecheck, build, verify**

```bash
npm run typecheck
npm run build --workspace client
```
Set `LLM_PROVIDER=mock` in `.env`, run server, upload a PDF, highlight text, generate flashcards + quiz, verify flip and answer selection work.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: learn-by-context flashcards quiz and digest endpoint"
```

---

## Phase 3 Self-Review

- **Spec coverage:** Study / PDF hub (§3.4) fully covered — upload/store (`Pdf` model + multer + storage, Task 3.1), in-app viewer (react-pdf, Task 3.2), highlights/notes anchored to text + per-PDF progress (Highlight model + PATCH progress + selection UI, Task 3.3), learn-by-context via `LLMProvider` (flashcards/quiz generation, Task 3.4 + 3.5). The provider abstraction is reusable by Phase 4 (Italian tutor) per spec §5. Free-tier LLM limits for large PDFs are surfaced as a truncation constraint in Task 3.5.
- **Placeholder scan:** No `TBD` / `TODO` / `implement later` placeholders remain. Every step carries runnable code; the only deliberate variance is the worker/bundler note in Task 3.2 Step 2 which documents a build-time choice branch, not a placeholder.
- **Type consistency:** `LLMProvider`/`generateFlashcards`/`generateQuiz` names are identical across Task 3.4 (definition + tests) and Task 3.5 (study router). Shared `FlashcardOut`/`QuizOut` are defined once in Task 3.4 Step 1 and consumed by the provider and `studyApi.ts`. `requireAuth`/`AuthedRequest` used throughout, matching Phase 0/1 conventions. `readPdfFile`, `safePdfPath`, `deletePdfFile` (Task 3.1) are reused in Task 3.5's text extraction. `pdfsRouter`/`highlightsRouter`/`studyRouter` all mounted in `server/src/index.ts` and tested.
- **Known deliberate decisions:** (1) generated cards/quizzes are persisted to DB (not returned ephemerally) so they can be re-reviewed; (2) material prioritizes highlights and truncates to ~12k chars to respect the free-tier constraint; (3) highlight anchor rects are simplified to page+text this phase, with positional JSON reserved for future precision.
