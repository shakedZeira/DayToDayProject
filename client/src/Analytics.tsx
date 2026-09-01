import { useCallback, useEffect, useState } from "react";
import type {
  AnalyticsSummary,
  ProgressiveOverloadView,
  ProgressionUpdateInput,
} from "shared";
import { getAnalytics, getProgressions, setProgression } from "./analyticsApi";

interface Props {
  token: string;
  refreshKey?: number;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function isRecent(iso?: string | null): boolean {
  if (!iso) return false;
  const then = new Date(iso).valueOf();
  return Date.now() - then < 24 * 60 * 60 * 1000;
}

function BarRow({ label, value, max }: { label: string; value: number; max: number }) {
  const pct = max > 0 ? Math.max(4, Math.round((value / max) * 100)) : 0;
  return (
    <li className="flex items-center gap-2 text-sm">
      <span className="w-24 shrink-0 truncate text-slate-600">{label}</span>
      <div className="flex-1 h-4 bg-slate-100 rounded overflow-hidden">
        <div
          className="h-full bg-indigo-500 rounded"
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="w-20 shrink-0 text-right text-slate-500 tabular-nums">
        {Math.round(value)} kg
      </span>
    </li>
  );
}

function VolumeBars({
  title,
  data,
}: {
  title: string;
  data: { date?: string; muscle?: string; kg: number }[];
}) {
  if (data.length === 0) {
    return (
      <p className="text-sm text-slate-400">No working volume logged yet.</p>
    );
  }
  const label = (d: (typeof data)[number]) =>
    d.date ? formatDate(d.date) : (d.muscle ?? "Other");
  const max = Math.max(...data.map((d) => d.kg));
  return (
    <section className="flex flex-col gap-2">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-indigo-600">{title}</h3>
      <ul className="flex flex-col gap-1.5">
        {data.map((d) => (
          <BarRow key={d.date ?? d.muscle} label={label(d)} value={d.kg} max={max} />
        ))}
      </ul>
    </section>
  );
}

interface EditDraft {
  targetSets: string;
  targetReps: string;
  targetWeight: string;
}

export default function Analytics({ token, refreshKey }: Props) {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [progressions, setProgressions] = useState<ProgressiveOverloadView[]>([]);
  const [editing, setEditing] = useState<string | null>(null);
  const [draft, setDraft] = useState<EditDraft>({ targetSets: "", targetReps: "", targetWeight: "" });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [analytics, prog] = await Promise.all([getAnalytics(token), getProgressions(token)]);
      setSummary(analytics);
      setProgressions(prog);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load analytics");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  function startEdit(p: ProgressiveOverloadView) {
    setEditing(p.exerciseName);
    setDraft({
      targetSets: String(p.targetSets),
      targetReps: String(p.targetReps),
      targetWeight: p.targetWeight != null ? String(p.targetWeight) : "",
    });
  }

  async function saveEdit(name: string) {
    const input: ProgressionUpdateInput = {
      targetSets: Number(draft.targetSets),
      targetReps: Number(draft.targetReps),
    };
    if (draft.targetWeight.trim() !== "") {
      input.targetWeight = Number(draft.targetWeight);
    }
    try {
      await setProgression(token, name, input);
      setEditing(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update targets");
    }
  }

  if (loading) {
    return (
      <div className="w-full max-w-2xl text-center text-slate-400 py-8">Loading analytics…</div>
    );
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4 border rounded bg-white p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-indigo-600">
        Progress &amp; Analytics
      </h2>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      {summary && (
        <div className="flex gap-4 text-sm text-slate-600">
          <span>
            <strong className="text-slate-800">{summary.totalWorkouts}</strong> workouts total
          </span>
          <span>
            <strong className="text-slate-800">{summary.thisWeekWorkouts}</strong> this week
          </span>
        </div>
      )}

      <section className="flex flex-col gap-2">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-indigo-600">
          Progress targets
        </h3>
        {progressions.length === 0 ? (
          <p className="text-sm text-slate-400">
            Log working sets to start tracking targets here.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {progressions.map((p) => (
              <li
                key={p.exerciseName}
                className="border rounded px-3 py-2 bg-slate-50 text-sm text-slate-700 flex flex-col gap-2"
              >
                {editing !== p.exerciseName ? (
                  <div className="flex items-center justify-between gap-2">
                    <div>
                      <span className="font-medium text-slate-800">{p.exerciseName}</span>: Target{" "}
                      {p.targetSets} × {p.targetReps}
                      {p.targetWeight != null ? ` at ${p.targetWeight}kg` : ""}
                    </div>
                    <div className="flex items-center gap-2">
                      {isRecent(p.lastProgressed) && p.incrementKg > 0 && (
                        <span className="text-xs bg-green-100 text-green-700 rounded px-2 py-0.5 font-medium">
                          +{p.incrementKg}kg bump
                        </span>
                      )}
                      <button
                        onClick={() => startEdit(p)}
                        className="text-xs text-indigo-600 hover:text-indigo-800"
                      >
                        Edit
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex flex-col gap-2">
                    <div className="flex gap-2 items-center">
                      <span className="text-slate-600">{p.exerciseName}</span>
                      <input
                        className="border rounded px-2 py-1 w-14"
                        type="number"
                        min={1}
                        value={draft.targetSets}
                        onChange={(e) => setDraft({ ...draft, targetSets: e.target.value })}
                      />
                      <span className="text-slate-400">×</span>
                      <input
                        className="border rounded px-2 py-1 w-14"
                        type="number"
                        min={1}
                        value={draft.targetReps}
                        onChange={(e) => setDraft({ ...draft, targetReps: e.target.value })}
                      />
                      <span className="text-slate-400">kg</span>
                      <input
                        className="border rounded px-2 py-1 w-16"
                        type="number"
                        min={0}
                        step="any"
                        placeholder="weight"
                        value={draft.targetWeight}
                        onChange={(e) => setDraft({ ...draft, targetWeight: e.target.value })}
                      />
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => saveEdit(p.exerciseName)}
                        className="bg-indigo-600 text-white rounded px-3 py-1 text-xs font-medium hover:bg-indigo-700"
                      >
                        Save
                      </button>
                      <button
                        onClick={() => setEditing(null)}
                        className="px-3 py-1 text-xs text-slate-600 hover:text-slate-800"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {summary && summary.prs.length > 0 && (
        <section className="flex flex-col gap-2">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-indigo-600">
            Recent PRs
          </h3>
          <ul className="flex flex-col gap-1">
            {summary.prs.map((pr) => (
              <li
                key={`${pr.exerciseName}-${pr.repRange}`}
                className="border rounded px-3 py-2 bg-slate-50 text-sm text-slate-700"
              >
                {pr.exerciseName} — {pr.repRange}×{pr.weight}kg{" "}
                <span className="text-slate-400">(e1rm ~{Math.round(pr.e1rm)}kg)</span>{" "}
                {formatDate(pr.date)}
              </li>
            ))}
          </ul>
        </section>
      )}

      {summary && <VolumeBars title="Volume per day" data={summary.volumeTrend} />}
      {summary && <VolumeBars title="Volume by muscle group" data={summary.muscleVolume} />}
    </div>
  );
}