import path from "node:path";
import os from "node:os";
import fs from "node:fs";

const BASE_DIR = process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME
  ? os.tmpdir()
  : process.cwd();

const STORAGE_DIR = path.join(BASE_DIR, "storage");

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
