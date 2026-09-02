import { PDFDocument } from "pdf-lib";
import { beforeEach, test, expect } from "vitest";
import request from "supertest";
import { app } from "./index";
import { prisma } from "./db";

let token = "";
let pdfId = "";

beforeEach(async () => {
  await prisma.quizOption.deleteMany({});
  await prisma.quiz.deleteMany({});
  await prisma.flashcard.deleteMany({});
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
