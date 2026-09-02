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