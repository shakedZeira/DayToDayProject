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
