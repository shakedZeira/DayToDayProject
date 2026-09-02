import pdfParse from "pdf-parse/lib/pdf-parse.js";
import { readPdfFile } from "./storage";

export async function extractPdfText(fileName: string): Promise<string> {
  const data = await pdfParse(readPdfFile(fileName));
  return data.text ?? "";
}
