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
  await prisma.pdf.delete({ where: { id: pdf.id } });
  deletePdfFile(pdf.fileName);
  res.status(204).end();
});

export { pageCountOf };
