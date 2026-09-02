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
