import { useEffect, useState } from "react";
import type { ItalianExercise, ItalianReviewItem } from "shared";
import { getReviewQueue, gradeReviewItem } from "./italianApi";
import { correctText } from "./ItalianLessonRunner";

interface Props {
  token: string;
  onClose: () => void;
}

export const GRADES = [
  { label: "Again", grade: 1 },
  { label: "Hard", grade: 3 },
  { label: "Good", grade: 4 },
  { label: "Easy", grade: 5 }
] as const;

export function promptText(ex: ItalianExercise): string {
  if (ex.kind === "match") return "Match: " + ex.pairs.map((p) => p.left).join(", ");
  return ex.direction === "listen" ? `Listen: ${ex.speak ?? ""}` : ex.prompt;
}

export default function ItalianReview({ token, onClose }: Props) {
  const [queue, setQueue] = useState<ItalianReviewItem[] | null>(null);
  const [total, setTotal] = useState(0);
  const [idx, setIdx] = useState(0);
  const [revealed, setRevealed] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getReviewQueue(token)
      .then((q) => {
        setQueue(q.due);
        setTotal(q.dueCount);
      })
      .catch((e: Error) => setError(e.message));
  }, [token]);

  async function grade(g: number) {
    const item = queue![idx];
    try {
      await gradeReviewItem(token, item.exerciseId, g);
    } catch (e) {
      setError((e as Error).message);
      return;
    }
    setRevealed(false);
    setIdx((i) => i + 1);
  }

  const item = queue?.[idx];
  return (
    <div className="border rounded-lg bg-white p-6 flex flex-col gap-4">
      <div className="flex justify-between items-center">
        <h2 className="text-lg font-medium">Review</h2>
        <button className="text-sm text-gray-500" onClick={onClose}>
          Close
        </button>
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}
      {!queue && !error && <p className="text-gray-500">Loading…</p>}
      {queue && !item && (
        <p className="text-gray-600">
          {queue.length === 0 && idx === 0
            ? "Nothing due. Complete lessons on the Path to build your review queue."
            : "All caught up for now."}
        </p>
      )}
      {item && (
        <>
          <p className="text-sm text-gray-500">
            {idx + 1} / {queue!.length}
            {total > queue!.length ? ` (${total} due)` : ""}
          </p>
          <p className="text-xl font-medium">{promptText(item.exercise)}</p>
          {revealed ? (
            <>
              <p className="rounded bg-[#58CC02]/10 px-4 py-3">{correctText(item.exercise)}</p>
              <div className="grid grid-cols-4 gap-2">
                {GRADES.map((g) => (
                  <button key={g.label} className="border rounded px-2 py-2 text-sm" onClick={() => void grade(g.grade)}>
                    {g.label}
                  </button>
                ))}
              </div>
            </>
          ) : (
            <button className="self-end bg-[#58CC02] text-white rounded px-6 py-2" onClick={() => setRevealed(true)}>
              Show answer
            </button>
          )}
        </>
      )}
    </div>
  );
}
