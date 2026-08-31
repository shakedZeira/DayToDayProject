import { useState } from "react";
import { analyzePhoto } from "./photoApi";
import type { VisionEstimate } from "shared";

interface Props {
  token: string;
}

export default function PhotoCalories({ token }: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [estimate, setEstimate] = useState<VisionEstimate | null>(null);

  async function onAnalyze() {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      setEstimate(await analyzePhoto(token, file));
    } catch (err) {
      setEstimate(null);
      setError(err instanceof Error ? err.message : "Failed to analyze photo");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4 border rounded bg-white p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-indigo-600">
        Photo Calories
      </h2>
      <div className="flex gap-2 items-center">
        <input
          type="file"
          accept="image/*"
          className="flex-1 text-sm text-slate-600"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        <button
          onClick={onAnalyze}
          disabled={busy || !file}
          className="bg-indigo-600 text-white rounded px-4 py-2 text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {busy ? "Analyzing…" : "Analyze"}
        </button>
      </div>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      {estimate && (
        <div className="flex flex-col gap-2">
          <p className="text-slate-800 font-semibold">
            {estimate.totalCalories} kcal{" "}
            <span className="text-sm font-normal text-slate-400">(rough)</span>
          </p>
          <ul className="flex flex-col gap-1">
            {estimate.items.map((item, i) => (
              <li key={i} className="text-sm text-slate-700">
                {item.foodName}: ~{item.estimatedGrams}g ≈ {item.estimatedCalories} kcal
              </li>
            ))}
          </ul>
          <p className="text-xs text-slate-400 italic">{estimate.disclaimer}</p>
        </div>
      )}
    </div>
  );
}
