import { useCallback, useEffect, useMemo, useState } from "react";
import {
  getWorkouts,
  getWorkout,
  createWorkout,
  appendSets,
  getProgressive,
  type WorkoutSummary,
  type Workout,
} from "./workoutApi";
import type { ProgressiveSuggestion } from "shared";
import ProgressionChart, { type ProgressionChartPoint } from "./ProgressionChart";

interface Props {
  token: string;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export default function Workouts({ token }: Props) {
  const [workouts, setWorkouts] = useState<WorkoutSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string>("");
  const [selected, setSelected] = useState<Workout | null>(null);
  const [progressions, setProgressions] = useState<ProgressiveSuggestion[]>([]);
  const [title, setTitle] = useState("");
  const [exercise, setExercise] = useState("");
  const [weightKg, setWeightKg] = useState("");
  const [reps, setReps] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadDetail = useCallback(
    async (id: string) => {
      if (!id) return;
      try {
        const [workout, suggestions] = await Promise.all([
          getWorkout(token, id),
          getProgressive(token, id),
        ]);
        setSelected(workout);
        setProgressions(suggestions);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load workout");
      }
    },
    [token]
  );

  const refresh = useCallback(async () => {
    try {
      const data = await getWorkouts(token);
      setWorkouts(data);
      setError(null);
      if (data.length === 0) {
        setSelectedId("");
        setSelected(null);
        setProgressions([]);
        return;
      }
      const keep = data.some((w) => w.id === selectedId);
      const nextId = keep ? selectedId : data[data.length - 1].id;
      setSelectedId(nextId);
      await loadDetail(nextId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load workouts");
    } finally {
      setLoading(false);
    }
  }, [token, selectedId, loadDetail]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    loadDetail(selectedId);
  }, [loadDetail, selectedId]);

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    try {
      await createWorkout(token, { title: title.trim() });
      setTitle("");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create workout");
    }
  }

  async function onLogSet(e: React.FormEvent) {
    e.preventDefault();
    const kg = Number(weightKg);
    const r = Number(reps);
    if (!selected || !exercise.trim() || !kg || kg <= 0 || !r || r <= 0) return;
    try {
      await appendSets(token, selected.id, [
        { exercise: exercise.trim(), weightKg: kg, reps: Math.floor(r) },
      ]);
      setExercise("");
      setWeightKg("");
      setReps("");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to log set");
    }
  }

  const chartPoints = useMemo<ProgressionChartPoint[]>(() => {
    if (!selected) return [];
    const firstExercise = selected.sets[0]?.exercise;
    if (!firstExercise) return [];
    const points = workouts
      .filter((w) => w.sets.some((s) => s.exercise === firstExercise))
      .map((w) => {
        const weights = w.sets
          .filter((s) => s.exercise === firstExercise)
          .map((s) => s.weightKg);
        return { date: w.date, maxKg: Math.max(...weights) };
      });
    points.sort((a, b) => new Date(a.date).valueOf() - new Date(b.date).valueOf());
    return points;
  }, [selected, workouts]);

  if (loading) {
    return (
      <div className="w-full max-w-2xl text-center text-slate-400 py-8">Loading workouts…</div>
    );
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4 border rounded bg-white p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-indigo-600">
        Workouts
      </h2>

      <form onSubmit={onCreate} className="flex gap-2">
        <input
          className="border rounded px-3 py-2 flex-1"
          placeholder="Workout title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />
        <button className="bg-indigo-600 text-white rounded px-4 font-medium">
          Create
        </button>
      </form>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      {workouts.length === 0 ? (
        <div className="text-center text-slate-400 py-6">No workouts yet. Create one above!</div>
      ) : (
        <>
          <select
            className="border rounded px-3 py-2"
            value={selectedId}
            onChange={(e) => setSelectedId(e.target.value)}
          >
            {workouts.map((w) => (
              <option key={w.id} value={w.id}>
                {w.title} · {formatDate(w.date)}
              </option>
            ))}
          </select>

          {selected && (
            <>
              <form onSubmit={onLogSet} className="flex gap-2">
                <input
                  className="border rounded px-3 py-2 flex-1"
                  placeholder="Exercise"
                  value={exercise}
                  onChange={(e) => setExercise(e.target.value)}
                  required
                />
                <input
                  className="border rounded px-3 py-2 w-20"
                  type="number"
                  min={0}
                  step="any"
                  placeholder="kg"
                  value={weightKg}
                  onChange={(e) => setWeightKg(e.target.value)}
                  required
                />
                <input
                  className="border rounded px-3 py-2 w-20"
                  type="number"
                  min={1}
                  placeholder="reps"
                  value={reps}
                  onChange={(e) => setReps(e.target.value)}
                  required
                />
                <button className="bg-indigo-600 text-white rounded px-4 font-medium">
                  + Set
                </button>
              </form>

              {selected.sets.length === 0 ? (
                <div className="text-center text-slate-400 py-4">No sets logged yet.</div>
              ) : (
                <ul className="flex flex-col gap-1">
                  {selected.sets.map((s) => (
                    <li
                      key={s.id}
                      className="border rounded px-3 py-2 bg-slate-50 text-sm text-slate-700"
                    >
                      {s.exercise}: {s.weightKg}kg × {s.reps} reps
                    </li>
                  ))}
                </ul>
              )}

              {progressions.length > 0 && (
                <section className="flex flex-col gap-2">
                  <h3 className="text-xs font-semibold uppercase tracking-wide text-indigo-600">
                    Progression suggestions
                  </h3>
                  <ul className="flex flex-col gap-2">
                    {progressions.map((p) => {
                      const met = p.reason === "completed_target";
                      return (
                        <li
                          key={p.exercise}
                          className="border rounded px-3 py-2 bg-slate-50 text-sm text-slate-700"
                        >
                          <span className="font-medium text-slate-800">{p.exercise}</span>:{" "}
                          {p.currentWeightKg}kg → {p.suggestedNextKg}kg{" "}
                          {met
                            ? "(target met — bump weight)"
                            : "(keep until 3×8 met)"}
                        </li>
                      );
                    })}
                  </ul>
                </section>
              )}

              {chartPoints.length > 0 && (
                <section className="flex flex-col gap-2">
                  <h3 className="text-xs font-semibold uppercase tracking-wide text-indigo-600">
                    Progression
                  </h3>
                  <ProgressionChart points={chartPoints} />
                </section>
              )}
            </>
          )}
        </>
      )}
    </div>
  );
}
