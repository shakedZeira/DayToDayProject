import { useCallback, useEffect, useState } from "react";
import { Document, Page } from "react-pdf";
import { pdfjs } from "react-pdf";
import {
  getPdfs, uploadPdf, deletePdf, getPdfFileUrl, type Pdf
} from "./pdfApi";
import { authedFetch } from "./auth";

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url
).toString();

interface Props { token: string; }

export default function PdfHub({ token }: Props) {
  const [pdfs, setPdfs] = useState<Pdf[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [numPages, setNumPages] = useState(0);

  const refresh = useCallback(async () => {
    setPdfs(await getPdfs(token));
  }, [token]);

  useEffect(() => { refresh(); }, [refresh]);

  useEffect(() => {
    if (!selectedId) { setBlobUrl(null); return; }
    let url: string | null = null;
    const load = async () => {
      const res = await authedFetch(token, getPdfFileUrl(selectedId));
      if (!res.ok) return;
      const buf = await res.arrayBuffer();
      url = URL.createObjectURL(new Blob([buf], { type: "application/pdf" }));
      setBlobUrl(url);
    };
    load().catch(() => {});
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [selectedId, token]);

  async function onUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    await uploadPdf(token, file, file.name);
    await refresh();
    e.target.value = "";
  }

  async function onDelete(id: string) {
    await deletePdf(token, id);
    if (selectedId === id) setSelectedId(null);
    await refresh();
  }

  return (
    <div className="w-full max-w-4xl flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <label className="text-indigo-600 underline cursor-pointer text-sm">
          Upload PDF
          <input type="file" accept="application/pdf" className="hidden" onChange={onUpload} />
        </label>
        {selectedId && <span className="text-sm text-slate-400">Select another file to view it.</span>}
      </div>

      <ul className="flex flex-col gap-2">
        {pdfs.map((p) => (
          <li key={p.id} className="flex items-center gap-3 border rounded p-3 bg-white">
            <button className="flex-1 text-left truncate" onClick={() => setSelectedId(p.id)}>
              <span className="font-medium">{p.title}</span>
              <span className="ml-2 text-xs text-slate-400">{p.pageCount} pages</span>
            </button>
            <button className="text-sm text-red-600" onClick={() => onDelete(p.id)}>✕</button>
          </li>
        ))}
      </ul>

      {blobUrl && (
        <div className="border rounded p-4 bg-white">
          <Document
            file={blobUrl}
            onLoadSuccess={({ numPages }) => setNumPages(numPages)}
          >
            {Array.from({ length: numPages }, (_, i) => i + 1).map((p) => (
              <Page key={p} pageNumber={p} className="mb-4 border" />
            ))}
          </Document>
        </div>
      )}
    </div>
  );
}
