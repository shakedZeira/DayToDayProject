import { useCallback, useEffect, useState } from "react";
import { Document, Page } from "react-pdf";
import { pdfjs } from "react-pdf";
import {
  getPdfs, uploadPdf, deletePdf, getPdfFileUrl,
  getHighlights, createHighlight, deleteHighlight, updatePdfProgress,
  type Pdf, type Highlight
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
  const [currentPage, setCurrentPage] = useState(1);
  const [highlights, setHighlights] = useState<Highlight[]>([]);
  const [noteDraft, setNoteDraft] = useState<Record<string, string>>({});

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

  useEffect(() => {
    if (!selectedId) { setHighlights([]); setCurrentPage(1); return; }
    getHighlights(token, selectedId).then(setHighlights).catch(() => {});
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

  async function saveHighlight() {
    if (!selectedId) return;
    const text = window.getSelection()?.toString().trim();
    if (!text) return;
    try {
      const added = await createHighlight(token, selectedId, {
        pageNumber: currentPage, text, color: "yellow", positions: "[]"
      });
      setHighlights((h) => [...h, added]);
      window.getSelection()?.removeAllRanges();
    } catch {
      // selection or request failed; nothing to do
    }
  }

  async function saveNote(id: string) {
    const note = noteDraft[id]?.trim();
    if (note == null || !selectedId) return;
    const hl = highlights.find((h) => h.id === id);
    if (!hl) return;
    await createHighlight(token, selectedId, {
      pageNumber: hl.pageNumber, text: hl.text, note, color: hl.color, positions: hl.positions
    }).then(() => deleteHighlight(token, id));
    const updated = await getHighlights(token, selectedId);
    setHighlights(updated);
  }

  async function removeHighlight(id: string) {
    await deleteHighlight(token, id);
    setHighlights((h) => h.filter((x) => x.id !== id));
  }

  function goToPage(pageNumber: number) {
    setCurrentPage(pageNumber);
    if (!selectedId) return;
    const pdf = pdfs.find((p) => p.id === selectedId);
    const readProgress = pdf?.pageCount ? Math.min(1, pageNumber / pdf.pageCount) : 0;
    updatePdfProgress(token, selectedId, { lastPage: pageNumber, readProgress })
      .then((updated) => {
        setPdfs((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
      })
      .catch(() => {});
  }

  const selectedPdf = selectedId ? pdfs.find((p) => p.id === selectedId) : null;
  const progressPct = selectedPdf
    ? Math.round(Math.min(100, (selectedPdf.readProgress || 0) * 100))
    : 0;

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
            <button className="flex-1 text-left truncate" onClick={() => { setSelectedId(p.id); setCurrentPage(1); }}>
              <span className="font-medium">{p.title}</span>
              <span className="ml-2 text-xs text-slate-400">{p.pageCount} pages</span>
            </button>
            <button className="text-sm text-red-600" onClick={() => onDelete(p.id)}>✕</button>
          </li>
        ))}
      </ul>

      {selectedPdf && (
        <div className="border rounded p-4 bg-white">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <button
                onClick={saveHighlight}
                className="bg-indigo-600 text-white rounded px-3 py-1 text-sm"
              >
                Save selected text as highlight
              </button>
              <span className="text-xs text-slate-400">Current page: {currentPage}</span>
            </div>
            <span className="text-xs text-slate-500">{progressPct}% read</span>
          </div>
          <div className="mt-2 h-2 w-full rounded bg-slate-200 overflow-hidden">
            <div className="h-full bg-indigo-500 rounded" style={{ width: `${progressPct}%` }} />
          </div>
        </div>
      )}

      {selectedPdf && (
        <div className="border rounded p-4 bg-white">
          <h3 className="font-semibold text-slate-700 mb-2">Highlights</h3>
          {highlights.length === 0 && <p className="text-sm text-slate-400">No highlights yet. Select text and click “Save selected text as highlight”.</p>}
          <ul className="flex flex-col gap-3">
            {highlights.map((h) => (
              <li key={h.id} className="flex flex-col gap-2 border-l-4 border-yellow-400 pl-3">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm text-slate-700">
                    <span className="text-xs text-slate-400 mr-1">p{h.pageNumber}</span>
                    {h.text}
                  </p>
                  <div className="flex items-center gap-2 shrink-0">
                    <button onClick={() => goToPage(h.pageNumber)} className="text-xs text-indigo-600 underline">
                      Go to page
                    </button>
                    <button onClick={() => removeHighlight(h.id)} className="text-sm text-red-600">✕</button>
                  </div>
                </div>
                {h.note && <p className="text-xs text-slate-500 italic">{h.note}</p>}
                <div className="flex items-center gap-2">
                  <input
                    value={noteDraft[h.id] ?? ""}
                    onChange={(e) => setNoteDraft((d) => ({ ...d, [h.id]: e.target.value }))}
                    placeholder="Add a note…"
                    className="flex-1 border rounded px-2 py-1 text-sm"
                  />
                  <button onClick={() => saveNote(h.id)} className="text-xs text-indigo-600 underline">
                    Save note
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {blobUrl && (
        <div className="border rounded p-4 bg-white">
          <Document
            file={blobUrl}
            onLoadSuccess={({ numPages }) => setNumPages(numPages)}
          >
            {Array.from({ length: numPages }, (_, i) => i + 1).map((p) => (
              <Page
                key={p}
                pageNumber={p}
                className="mb-4 border"
                onClick={() => goToPage(p)}
              />
            ))}
          </Document>
        </div>
      )}
    </div>
  );
}
