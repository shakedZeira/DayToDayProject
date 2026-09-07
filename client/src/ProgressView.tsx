import { useEffect, useState } from "react";
import { getProgress } from "./progressApi";
import type { ProgressResponse } from "shared";

interface Props {
  token: string;
}

function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="bg-white rounded-xl shadow-sm p-4 flex flex-col gap-1">
      <span className="text-xs uppercase tracking-wide text-slate-400">{label}</span>
      <span className="text-2xl font-bold text-slate-800">{value}</span>
      {sub && <span className="text-xs text-slate-500">{sub}</span>}
    </div>
  );
}

const DAY_LABELS = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];

function last7Labels(now: Date): string[] {
  const out: string[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
    out.push(DAY_LABELS[d.getDay()]);
  }
  return out;
}

export function ActivityChart({ days, labels }: { days: number[]; labels: string[] }) {
  const max = Math.max(1, ...days);
  return (
    <div className="bg-white rounded-xl shadow-sm p-4">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400 mb-2">Tasks completed</h3>
      <svg viewBox="0 0 140 40" className="w-full" role="img" aria-label="Tasks completed in the last 7 days">
        {days.map((v, i) => {
          const h = (v / max) * 34;
          return <rect key={i} x={i * 20} y={40 - h} width={14} height={h} rx={2} fill="#4f46e5" />;
        })}
      </svg>
      <div className="flex justify-between text-[10px] text-slate-400">
        {labels.map((l, i) => (
          <span key={i} style={{ width: 14 }}>{l}</span>
        ))}
      </div>
    </div>
  );
}

export default function ProgressView({ token }: Props) {
  const [progress, setProgress] = useState<ProgressResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getProgress(token)
      .then(setProgress)
      .catch((e) => setError(e instanceof Error ? e.message : "failed"));
  }, [token]);

  if (error) return <p className="text-red-600">{error}</p>;
  if (!progress) return <p className="text-slate-400">Loading progress…</p>;

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-2xl font-bold text-slate-800">Progress</h2>
      <div className="grid grid-cols-2 gap-4">
        <Stat label="Task streak" value={`${progress.tasks.streak}d`} sub={`${progress.tasks.total} total tasks`} />
        <Stat label="Week completion" value={`${Math.round(progress.tasks.completionRateWeek * 100)}%`} />
        <Stat label="Workouts this week" value={`${progress.health.workoutsThisWeek}/${progress.health.workoutsTarget}`} />
        <Stat label="Italian streak" value={`${progress.italian.streak}d`} sub={`${progress.italian.totalLessons} lessons`} />
        <Stat label="Calories today" value={`${Math.round(progress.health.caloriesToday)}`} sub={`target ${progress.health.calorieTarget}`} />
        <Stat label="PDFs in progress" value={`${progress.study.pdfsInProgress}`} sub={`${progress.study.flashcards} flashcards`} />
      </div>
      <ActivityChart days={progress.tasks.completedByDay} labels={last7Labels(new Date())} />
    </div>
  );
}
