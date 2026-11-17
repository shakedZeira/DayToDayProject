import { useCallback, useEffect, useState } from "react";
import { listGoals, createGoal, deleteGoal, checkoffGoal, undoGoal, type WeeklyGoalInput } from "./goalsApi";
import type { WeeklyGoalView } from "shared";
import ItalianGoalCard from "./ItalianGoalCard";

interface Props {
  token: string;
}

const CATEGORY_OPTIONS = ["health", "italian", "study", "other", "general"] as const;
const AUTOSOURCE_OPTIONS = ["none", "workout", "italian_words", "italian_lesson", "italian_xp", "study"] as const;

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
    <div className="w-full flex flex-col gap-4">
      <form onSubmit={onAdd} className="md-card p-4 flex flex-col gap-2">
        <h2 className="md-title-medium text-md-onSurface">Add goal</h2>
        <div className="flex gap-2">
          <input aria-label="Goal title"
            className="border border-md-outline rounded-md3xs px-3 py-2 flex-1"
            placeholder="Goal title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
          />
          <input aria-label="Target"
            className="border border-md-outline rounded-md3xs px-3 py-2 w-20"
            type="number"
            min={1}
            placeholder="Target"
            value={targetCount}
            onChange={(e) => setTargetCount(e.target.value)}
            required
          />
          <input aria-label="Unit (optional)"
            className="border border-md-outline rounded-md3xs px-3 py-2"
            placeholder="Unit (optional)"
            value={unit}
            onChange={(e) => setUnit(e.target.value)}
          />
        </div>
        <div className="flex gap-2">
          <select aria-label="Category"
            className="border border-md-outline rounded-md3xs px-3 py-2"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            {CATEGORY_OPTIONS.map((c) => (
              <option key={c} value={c}>
                {c === "general" ? "General" : capLabel(c)}
              </option>
            ))}
          </select>
          <select aria-label="Auto-track source"
            className="border border-md-outline rounded-md3xs px-3 py-2"
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
        <button className="bg-indigo-600 text-white rounded-full py-2 font-medium">
          Add goal
        </button>
      </form>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      {goals.length === 0 ? (
        <p className="md-body-medium text-md-onSurfaceVariant text-center py-6">No goals yet. Use "Add goal" above to create one.</p>
      ) : (
        <>
        <h2 className="md-title-large text-md-onSurface">This Week</h2>
        <ul className="flex flex-col gap-3">
          {goals.map((g) => {
            const pct = g.targetCount > 0 ? Math.min(100, (g.thisWeekCount / g.targetCount) * 100) : 0;
            const done = g.thisWeekCount >= g.targetCount;
            return (
              <li key={g.id} className="md-card p-4 flex flex-col gap-2">
                <div className="flex items-center gap-3">
                  <div className="flex-1 min-w-0">
                    <p className="md-title-medium text-md-onSurface truncate">{g.title}</p>
                    <div className="flex gap-2 text-xs text-slate-500">
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
                    aria-label={`Check off ${g.title}`}
                    onClick={() => onCheckoff(g.id)}
                    disabled={done}
                    className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                      done
                        ? "bg-emerald-600 text-white"
                        : "bg-indigo-600 text-white hover:bg-indigo-700"
                    } disabled:opacity-70`}
                    title={done ? "Goal reached this week" : "Check off"}
                  >
                    <svg aria-hidden="true" className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                    </svg>
                  </button>
                  <button
                    aria-label={`Delete ${g.title}`}
                    onClick={() => onDelete(g.id)}
                    className="text-slate-500 hover:text-red-600 flex-shrink-0"
                    title="Delete"
                  >
                    <svg aria-hidden="true" className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
                <div className="h-2 w-full bg-md-primaryContainer rounded-full overflow-hidden">
                  <div
                    className={`h-full ${done ? "bg-md-secondary" : "bg-indigo-700"}`}
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
        </>
      )}

      <h2 className="md-title-large text-md-onSurface">Italian</h2>
      <ItalianGoalCard token={token} />
    </div>
  );
}
