declare module "pdf-parse/lib/pdf-parse.js" {
  interface PdfParseResult {
    numpages: number;
    numrender: number;
    info: unknown;
    metadata: unknown;
    version: string;
    text: string;
  }
  interface PdfParseOptions {
    version?: string;
    max?: number;
    pagerender?: (pageData: unknown) => string | Promise<string>;
  }
  function PdfParse(dataBuffer: Buffer, options?: PdfParseOptions): Promise<PdfParseResult>;
  export = PdfParse;
}
