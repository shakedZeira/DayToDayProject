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