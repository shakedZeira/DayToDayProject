import { useCallback, useEffect, useState } from "react";
import { listGoals, createGoal, deleteGoal, checkoffGoal, undoGoal, type WeeklyGoalInput } from "./goalsApi";
import type { WeeklyGoalView } from "shared";

interface Props {
  token: string;
}

const CATEGORY_OPTIONS = ["health", "italian", "study", "other", "general"] as const;
const AUTOSOURCE_OPTIONS = ["none", "workout", "italian_words", "italian_lesson", "study"] as const;

function sameLocalDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

function isTodayLocal(iso: string | null | undefined): boolean {
  if (!iso) return false;
  return sameLocalDay(new Date(iso), new Date());
}

function capLabel(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

export default function Goals({ token }: Props) {
  const [goals, setGoals] = useState<WeeklyGoalView[]>([]);
  const [title, setTitle] = useState("");
  const [targetCount, setTargetCount] = useState("3");
  const [unit, setUnit] = useState("");
  const [category, setCategory] = useState<string>("general");
  const [autoSource, setAutoSource] = useState<string>("none");
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const data = await listGoals(token);
      setGoals(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load goals");
    }
  }, [token]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function onAdd(e: React.FormEvent) {
    e.preventDefault();
    const count = Number(targetCount);
    if (!title.trim() || !count || count < 1) return;
    const input: WeeklyGoalInput = {
      title: title.trim(),
      targetCount: count,
      unit: unit.trim() || null,
      category: category === "general" ? null : category,
      autoSource: autoSource === "none" ? null : autoSource,
    };
    try {
      await createGoal(token, input);
      setTitle("");
      setUnit("");
      setCategory("general");
      setAutoSource("none");
      setTargetCount("3");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create goal");
    }
  }

  async function onCheckoff(id: string) {
    try {
      await checkoffGoal(token, id);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to check off goal");
    }
  }

  async function onUndo(id: string) {
    try {
      await undoGoal(token, id);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to undo goal");
    }
  }

  async function onDelete(id: string) {
    try {
      await deleteGoal(token, id);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete goal");
    }
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4 border rounded bg-white p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-indigo-600">
        Weekly Goals
      </h2>

      <form onSubmit={onAdd} className="flex flex-col gap-2">
        <div className="flex gap-2">
          <input
            className="border rounded px-3 py-2 flex-1"
            placeholder="Goal title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
          />
          <input
            className="border rounded px-3 py-2 w-20"
            type="number"
            min={1}
            placeholder="Target"
            value={targetCount}
            onChange={(e) => setTargetCount(e.target.value)}
            required
          />
          <input
            className="border rounded px-3 py-2"
            placeholder="Unit (optional)"
            value={unit}
            onChange={(e) => setUnit(e.target.value)}
          />
        </div>
        <div className="flex gap-2">
          <select
            className="border rounded px-3 py-2"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            {CATEGORY_OPTIONS.map((c) => (
              <option key={c} value={c}>
                {c === "general" ? "General" : capLabel(c)}
              </option>
            ))}
          </select>
          <select
            className="border rounded px-3 py-2"
            value={autoSource}
            onChange={(e) => setAutoSource(e.target.value)}
          >
            {AUTOSOURCE_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s === "none" ? "No auto source" : capLabel(s.replace(/_/g, " "))}
              </option>
            ))}
          </select>
        </div>
        <button className="bg-indigo-600 text-white rounded py-2 font-medium">
          Add Goal
        </button>
      </form>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      {goals.length === 0 ? (
        <div className="text-center text-slate-400 py-6">No goals yet. Add one above!</div>
      ) : (
        <ul className="flex flex-col gap-2">
          {goals.map((g) => {
            const pct = g.targetCount > 0 ? Math.min(100, (g.thisWeekCount / g.targetCount) * 100) : 0;
            const done = g.thisWeekCount >= g.targetCount;
            return (
              <li key={g.id} className="border rounded p-3 bg-slate-50 flex flex-col gap-2">
                <div className="flex items-center gap-3">
                  <div className="flex-1 min-w-0">
                    <p className="text-slate-800 font-medium truncate">{g.title}</p>
                    <div className="flex gap-2 text-xs text-slate-400">
                      {(g.category || g.autoSource) && (
                        <span className="bg-slate-100 rounded px-2 py-0.5">
                          {g.category ?? capLabel((g.autoSource ?? "").replace(/_/g, " "))}
                        </span>
                      )}
                      <span>
                        {g.thisWeekCount} / {g.targetCount}
                        {g.unit ? ` ${g.unit}` : ""}
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => onCheckoff(g.id)}
                    disabled={done}
                    className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                      done
                        ? "bg-emerald-600 text-white"
                        : "bg-indigo-600 text-white hover:bg-indigo-700"
                    } disabled:opacity-70`}
                    title={done ? "Goal reached this week" : "Check off"}
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                    </svg>
                  </button>
                  <button
                    onClick={() => onDelete(g.id)}
                    className="text-slate-300 hover:text-red-500 flex-shrink-0"
                    title="Delete"
                  >
                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
                <div className="h-2 w-full bg-slate-200 rounded overflow-hidden">
                  <div
                    className={`h-full ${done ? "bg-emerald-500" : "bg-indigo-500"}`}
                    style={{ width: `${pct}%` }}
                  />
                </div>
                {isTodayLocal(g.lastEventAt) && (
                  <button
                    onClick={() => onUndo(g.id)}
                    className="text-xs text-slate-500 underline text-left self-start"
                  >
                    undo
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
