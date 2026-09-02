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
