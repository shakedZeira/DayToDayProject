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

  const del = await request(app).delete(`/api/pdfs/${id}`).set("Authorization", `Bearer ${token}`);
  expect(del.status).toBe(204);

  const afterDel = await request(app).get("/api/pdfs").set("Authorization", `Bearer ${token}`);
  expect(afterDel.body).toHaveLength(0);
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
