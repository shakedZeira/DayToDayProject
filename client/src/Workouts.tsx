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
import { getRoutines } from "./routineApi";
import type { ProgressiveSuggestion, RoutineView } from "shared";
import ProgressionChart, { type ProgressionChartPoint } from "./ProgressionChart";
import ActiveWorkout from "./ActiveWorkout";

interface Props {
  token: string;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

type View = "start" | "routine-pick" | "active";

export default function Workouts({ token }: Props) {
  const [view, setView] = useState<View>("start");
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

  const [routines, setRoutines] = useState<RoutineView[]>([]);
  const [pickedRoutine, setPickedRoutine] = useState<RoutineView | null>(null);
  const [pickedDayIdx, setPickedDayIdx] = useState(0);
  const [loadingRoutines, setLoadingRoutines] = useState(false);

  const [activeInitial, setActiveInitial] = useState<
    | { title: string; xes: { exerciseId?: string; exerciseName: string; targetSets?: number; targetReps?: number }[] }
    | undefined
  >(undefined);

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
      setView("active");
      setActiveInitial({ title: title.trim(), xes: [] });
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

  function startEmptyWorkout() {
    setActiveInitial(undefined);
    setView("active");
  }

  async function openRoutinePicker() {
    setLoadingRoutines(true);
    try {
      const data = await getRoutines(token);
      setRoutines(data);
      setView("routine-pick");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load routines");
    } finally {
      setLoadingRoutines(false);
    }
  }

  function confirmRoutineStart() {
    if (!pickedRoutine) return;
    const day = pickedRoutine.days[pickedDayIdx];
    if (!day) return;
    const now = new Date();
    const dateStr = now.toLocaleDateString(undefined, { month: "short", day: "numeric" });
    const title = `${pickedRoutine.name} — ${day.name} ${dateStr}`;
    const xes = day.exercises.map((re) => ({
      exerciseId: re.exerciseId,
      exerciseName: re.exercise.name,
      targetSets: re.targetSets,
      targetReps: re.targetReps,
    }));
    setActiveInitial({ title, xes });
    setView("active");
  }

  function onActiveFinish() {
    setView("start");
    setActiveInitial(undefined);
    refresh();
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

  if (view === "active") {
    return <ActiveWorkout token={token} initial={activeInitial} onFinish={onActiveFinish} />;
  }

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

      {view === "start" && (
        <>
          <div className="flex gap-2">
            <button
              onClick={startEmptyWorkout}
              className="flex-1 bg-indigo-600 text-white rounded px-4 py-3 font-medium text-sm hover:bg-indigo-700"
            >
              Start Empty Workout
            </button>
            <button
              onClick={openRoutinePicker}
              disabled={loadingRoutines}
              className="flex-1 border border-indigo-600 text-indigo-600 rounded px-4 py-3 font-medium text-sm hover:bg-indigo-50 disabled:opacity-50"
            >
              {loadingRoutines ? "Loading…" : "Start from Routine"}
            </button>
          </div>

          {error && <p className="text-red-600 text-sm">{error}</p>}

          <form onSubmit={onCreate} className="flex gap-2">
            <input
              className="border rounded px-3 py-2 flex-1"
              placeholder="Or create a titled workout…"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />
            <button className="bg-slate-600 text-white rounded px-4 font-medium text-sm">
              Create
            </button>
          </form>

          {workouts.length === 0 ? (
            <div className="text-center text-slate-400 py-6">
              No workouts yet. Hit <span className="font-medium text-slate-600">Start Empty Workout</span> or
              start from a routine to log your first session.
            </div>
          ) : (
            <>
              <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Recent Workouts
              </h3>
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
                    <button className="bg-indigo-600 text-white rounded px-4 font-medium text-sm">
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
        </>
      )}

      {view === "routine-pick" && (
        <>
          <h3 className="text-sm font-semibold text-slate-700">Start from Routine</h3>
          {routines.length === 0 ? (
            <p className="text-sm text-slate-400">No routines yet. Create one in the Routines sub-tab, or start empty.</p>
          ) : (
            <>
              <select
                className="border rounded px-3 py-2"
                value={pickedRoutine?.id ?? ""}
                onChange={(e) => {
                  const r = routines.find((r) => r.id === e.target.value) ?? null;
                  setPickedRoutine(r);
                  setPickedDayIdx(0);
                }}
              >
                <option value="">Select a routine…</option>
                {routines.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.name} ({r.days.length} day{r.days.length !== 1 ? "s" : ""})
                  </option>
                ))}
              </select>

              {pickedRoutine && (
                <>
                  <select
                    className="border rounded px-3 py-2"
                    value={pickedDayIdx}
                    onChange={(e) => setPickedDayIdx(Number(e.target.value))}
                  >
                    {pickedRoutine.days.map((d, i) => (
                      <option key={d.id} value={i}>
                        {d.name} ({d.exercises.length} exercise{d.exercises.length !== 1 ? "s" : ""})
                      </option>
                    ))}
                  </select>

                  {pickedRoutine.days[pickedDayIdx] && (
                    <ul className="flex flex-col gap-1">
                      {pickedRoutine.days[pickedDayIdx].exercises.map((re) => (
                        <li
                          key={re.id}
                          className="border rounded px-3 py-2 bg-slate-50 text-sm text-slate-700"
                        >
                          {re.exercise.name}: {re.targetSets} × {re.targetReps}
                        </li>
                      ))}
                    </ul>
                  )}
                </>
              )}

              <div className="flex gap-2">
                <button
                  onClick={confirmRoutineStart}
                  disabled={!pickedRoutine}
                  className="bg-indigo-600 text-white rounded px-4 py-2 text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
                >
                  Start
                </button>
                <button
                  onClick={() => {
                    setView("start");
                    setPickedRoutine(null);
                  }}
                  className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
                >
                  Cancel
                </button>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}
