import { useCallback, useEffect, useRef, useState } from "react";
import {
  createWorkout,
  addWorkoutExercise,
  appendSetToExercise,
  getWorkouts,
  type Workout,
  type WorkoutSet,
} from "./workoutApi";
import { getExercises } from "./exerciseApi";
import type { ExerciseRecord } from "shared";
import ExerciseDemoModal, { type DemoExercise } from "./ExerciseDemoModal";

export interface ActiveInitialExercise {
  exerciseId?: string;
  exerciseName: string;
  targetSets?: number;
  targetReps?: number;
}

interface ActiveInitial {
  title: string;
  xes: ActiveInitialExercise[];
}

interface ActiveSet {
  weightKg: number;
  reps: number;
  setType: string;
  logged?: boolean;
  loggedSet?: WorkoutSet;
}

interface ActiveExercise {
  xeId: string;
  exerciseId?: string | null;
  exerciseName: string;
  muscleGroup?: string;
  targetSets?: number;
  targetReps?: number;
  prevWeight?: number;
  prevReps?: number;
  sets: ActiveSet[];
}

function defaultRestTimer(): number {
  try {
    const v = localStorage.getItem("dtd.restTimer");
    if (v) {
      const n = Number(v);
      if (n > 0) return n;
    }
  } catch { /* noop */ }
  return 90;
}

function beep() {
  try {
    const ctx = new AudioContext();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.value = 880;
    osc.type = "sine";
    gain.gain.value = 0.3;
    osc.start();
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
    osc.stop(ctx.currentTime + 0.35);
  } catch { /* noop */ }
}

function muscleGroupBadge(mg?: string) {
  if (!mg) return null;
  const colors: Record<string, string> = {
    Chest: "bg-red-100 text-red-700",
    Back: "bg-blue-100 text-blue-700",
    Shoulders: "bg-yellow-100 text-yellow-700",
    Arms: "bg-purple-100 text-purple-700",
    Legs: "bg-green-100 text-green-700",
    Core: "bg-orange-100 text-orange-700",
    Glutes: "bg-pink-100 text-pink-700",
  };
  const cls = colors[mg] ?? "bg-slate-100 text-slate-600";
  return (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${cls}`}>
      {mg}
    </span>
  );
}

interface Props {
  token: string;
  initial?: ActiveInitial;
  onFinish: (workoutId: string) => void;
}

export default function ActiveWorkout({ token, initial, onFinish }: Props) {
  const [workout, setWorkout] = useState<Workout | null>(null);
  const [exercises, setExercises] = useState<ActiveExercise[]>([]);
  const [addingEx, setAddingEx] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<ExerciseRecord[]>([]);
  const [freeTextName, setFreeTextName] = useState("");
  const [restDuration, setRestDuration] = useState(defaultRestTimer);
  const [restRemaining, setRestRemaining] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [finishing, setFinishing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [demoExercise, setDemoExercise] = useState<DemoExercise | null>(null);
  const restRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchPrevPerformance = useCallback(
    async (exerciseNames: string[]): Promise<Map<string, { weight: number; reps: number }>> => {
      const map = new Map<string, { weight: number; reps: number }>();
      try {
        const all = await getWorkouts(token);
        for (const name of exerciseNames) {
          if (map.has(name)) continue;
          for (let i = all.length - 1; i >= 0; i--) {
            const ws = all[i].sets.filter((s) => s.exercise === name);
            if (ws.length > 0) {
              const last = ws[ws.length - 1];
              map.set(name, { weight: last.weightKg, reps: last.reps });
              break;
            }
          }
        }
      } catch { /* noop */ }
      return map;
    },
    [token]
  );

  useEffect(() => {
    let cancelled = false;

    async function init() {
      try {
        const now = new Date();
        const dateStr = now.toLocaleDateString(undefined, { month: "short", day: "numeric" });
        const title = initial?.title ?? `Workout ${dateStr}`;
        const w = await createWorkout(token, { title });
        if (cancelled) return;

        const newExercises: ActiveExercise[] = [];

        if (initial && initial.xes.length > 0) {
          const names = initial.xes.map((x) => x.exerciseName);
          const prevMap = await fetchPrevPerformance(names);
          if (cancelled) return;

          for (const x of initial.xes) {
            const xe = await addWorkoutExercise(token, w.id, {
              exerciseId: x.exerciseId,
              exerciseName: x.exerciseName,
            });
            const prev = prevMap.get(x.exerciseName);
            const sets: ActiveSet[] = Array.from(
              { length: x.targetSets ?? 3 },
              () => ({
                weightKg: prev?.weight ?? 0,
                reps: prev?.reps ?? x.targetReps ?? 8,
                setType: "working",
              })
            );
            newExercises.push({
              xeId: xe.id,
              exerciseId: xe.exerciseId ?? x.exerciseId ?? undefined,
              exerciseName: xe.exerciseName,
              targetSets: x.targetSets,
              targetReps: x.targetReps,
              prevWeight: prev?.weight,
              prevReps: prev?.reps,
              sets,
            });
          }
        }

        if (!cancelled) {
          setWorkout(w);
          setExercises(newExercises);
          setLoading(false);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to start workout");
          setLoading(false);
        }
      }
    }
    init();
    return () => { cancelled = true; };
  }, [token, initial, fetchPrevPerformance]);

  useEffect(() => {
    if (restRemaining <= 0) {
      if (restRef.current) {
        clearInterval(restRef.current);
        restRef.current = null;
      }
      return;
    }
    restRef.current = setInterval(() => {
      setRestRemaining((prev) => {
        if (prev <= 1) {
          beep();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => {
      if (restRef.current) {
        clearInterval(restRef.current);
        restRef.current = null;
      }
    };
  }, [restRemaining > 0]);

  async function handleSearch(q: string) {
    setSearchQuery(q);
    if (q.length < 1) {
      setSearchResults([]);
      return;
    }
    const results = await getExercises(token, q);
    setSearchResults(results);
  }

  async function addExerciseFromLibrary(ex: ExerciseRecord) {
    if (!workout) return;
    try {
      const xe = await addWorkoutExercise(token, workout.id, {
        exerciseId: ex.id,
        exerciseName: ex.name,
      });
      setExercises((prev) => [
        ...prev,
        {
          xeId: xe.id,
          exerciseId: ex.id,
          exerciseName: ex.name,
          muscleGroup: ex.muscleGroup,
          sets: [{ weightKg: 0, reps: 8, setType: "working" }],
        },
      ]);
      setAddingEx(false);
      setSearchQuery("");
      setSearchResults([]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add exercise");
    }
  }

  async function addExerciseFreeText() {
    if (!workout || !freeTextName.trim()) return;
    try {
      const xe = await addWorkoutExercise(token, workout.id, {
        exerciseName: freeTextName.trim(),
      });
      setExercises((prev) => [
        ...prev,
        {
          xeId: xe.id,
          exerciseId: null,
          exerciseName: xe.exerciseName,
          sets: [{ weightKg: 0, reps: 8, setType: "working" }],
        },
      ]);
      setAddingEx(false);
      setFreeTextName("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add exercise");
    }
  }

  async function logSet(exIdx: number, setIdx: number) {
    if (!workout) return;
    const ex = exercises[exIdx];
    const s = ex.sets[setIdx];
    if (!s.weightKg && s.weightKg !== 0) return;
    try {
      const logged = await appendSetToExercise(token, workout.id, ex.xeId, {
        weightKg: s.weightKg,
        reps: s.reps,
        setType: s.setType,
      });
      setExercises((prev) => {
        const next = prev.map((e) => ({ ...e, sets: e.sets.map((st) => ({ ...st })) }));
      const target = next[exIdx].sets[setIdx];
      target.logged = true;
      target.loggedSet = logged;
        return next;
      });
      setRestRemaining(restDuration);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to log set");
    }
  }

  function addEmptySet(exIdx: number) {
    const ex = exercises[exIdx];
    const last = ex.sets[ex.sets.length - 1];
    setExercises((prev) => {
      const next = [...prev];
      const target = { ...next[exIdx] };
      target.sets = [
        ...target.sets,
        {
          weightKg: last?.weightKg ?? 0,
          reps: last?.reps ?? 8,
          setType: "working",
        },
      ];
      next[exIdx] = target;
      return next;
    });
  }

  function updateSetField(exIdx: number, setIdx: number, field: "weightKg" | "reps", value: string) {
    setExercises((prev) => {
      const next = prev.map((e) => ({ ...e, sets: e.sets.map((st) => ({ ...st })) }));
      const target = next[exIdx].sets[setIdx];
      if (field === "weightKg") {
        target.weightKg = value === "" ? 0 : Number(value);
      } else {
        target.reps = value === "" ? 0 : Math.floor(Number(value));
      }
      return next;
    });
  }

  function updateSetType(exIdx: number, setIdx: number, value: string) {
    setExercises((prev) => {
      const next = prev.map((e) => ({ ...e, sets: e.sets.map((st) => ({ ...st })) }));
      next[exIdx].sets[setIdx].setType = value;
      return next;
    });
  }

  function handleFinish() {
    if (!workout) return;
    setFinishing(true);
  }

  function confirmFinish() {
    if (!workout) return;
    onFinish(workout.id);
  }

  function updateRestDuration(val: number) {
    const v = Math.max(10, val);
    setRestDuration(v);
    try { localStorage.setItem("dtd.restTimer", String(v)); } catch { /* noop */ }
  }

  if (loading) {
    return (
      <div className="w-full max-w-2xl text-center text-slate-400 py-8">
        Starting workout…
      </div>
    );
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      {error && <p className="text-red-600 text-sm">{error}</p>}

      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-slate-800">
          {initial?.title ?? `Workout ${new Date().toLocaleDateString(undefined, { month: "short", day: "numeric" })}`}
        </h2>
        <button
          onClick={handleFinish}
          className="bg-indigo-600 text-white rounded px-4 py-2 text-sm font-medium hover:bg-indigo-700"
        >
          Finish Workout
        </button>
      </div>

      <div className="flex items-center gap-2 text-sm text-slate-600">
        <label>Rest timer:</label>
        <input
          type="number"
          min={10}
          step={15}
          value={restDuration}
          onChange={(e) => updateRestDuration(Number(e.target.value))}
          className="w-16 border rounded px-2 py-1 text-center"
        />
        <span>s</span>
      </div>

      {exercises.map((ex, exIdx) => (
        <div key={ex.xeId} className="border rounded bg-white p-3 flex flex-col gap-2">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-semibold text-slate-800">{ex.exerciseName}</span>
            {muscleGroupBadge(ex.muscleGroup)}
            <button
              onClick={() =>
                setDemoExercise({ name: ex.exerciseName, muscleGroup: ex.muscleGroup })
              }
              className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-indigo-600 hover:bg-indigo-100"
            >
              Demo
            </button>
            {ex.targetSets != null && ex.targetReps != null && (
              <span className="text-xs text-slate-400">
                Target: {ex.targetSets} × {ex.targetReps}
              </span>
            )}
            {ex.prevWeight != null && ex.prevReps != null && (
              <span className="text-xs text-slate-400">
                Previous: {ex.prevWeight}kg × {ex.prevReps}
              </span>
            )}
          </div>

          {ex.sets.map((s, sIdx) => (
            <div key={sIdx} className="flex items-center gap-1 flex-wrap">
              <span className="text-xs text-slate-400 w-6 text-right">
                {sIdx + 1}
              </span>
              {s.logged ? (
                <span className="text-sm text-green-600 font-medium">
                  ✓ {s.weightKg}kg × {s.reps}
                  {s.setType !== "working" ? ` (${s.setType})` : ""}
                </span>
              ) : (
                <>
                  <input
                    type="number"
                    min={0}
                    step="any"
                    placeholder={ex.prevWeight != null ? String(ex.prevWeight) : "kg"}
                    value={s.weightKg || ""}
                    onChange={(e) => updateSetField(exIdx, sIdx, "weightKg", e.target.value)}
                    className="w-20 border rounded px-2 py-1.5 text-sm"
                  />
                  <input
                    type="number"
                    min={1}
                    placeholder={ex.prevReps != null ? String(ex.prevReps) : "reps"}
                    value={s.reps || ""}
                    onChange={(e) => updateSetField(exIdx, sIdx, "reps", e.target.value)}
                    className="w-16 border rounded px-2 py-1.5 text-sm"
                  />
                  <select
                    value={s.setType}
                    onChange={(e) => updateSetType(exIdx, sIdx, e.target.value)}
                    className="border rounded px-1 py-1.5 text-xs"
                  >
                    <option value="working">Working</option>
                    <option value="warmup">Warm-up</option>
                  </select>
                  <button
                    onClick={() => logSet(exIdx, sIdx)}
                    className="bg-indigo-600 text-white rounded px-3 py-1.5 text-sm font-medium hover:bg-indigo-700"
                  >
                    ✓
                  </button>
                </>
              )}
            </div>
          ))}

          <button
            onClick={() => addEmptySet(exIdx)}
            className="text-xs text-indigo-600 hover:text-indigo-800 self-start"
          >
            + Add set
          </button>
        </div>
      ))}

      {restRemaining > 0 && (
        <div className="fixed bottom-0 inset-x-0 bg-slate-900 text-white p-4 flex items-center justify-between z-50">
          <span className="text-2xl font-mono font-bold">
            {Math.floor(restRemaining / 60)}:{String(restRemaining % 60).padStart(2, "0")}
          </span>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setRestRemaining(0)}
              className="text-sm text-slate-300 underline"
            >
              Skip
            </button>
          </div>
        </div>
      )}

      {addingEx ? (
        <div className="border rounded bg-white p-3 flex flex-col gap-2">
          <input
            type="text"
            placeholder="Search exercises…"
            value={searchQuery}
            onChange={(e) => handleSearch(e.target.value)}
            className="border rounded px-3 py-2 text-sm"
            autoFocus
          />
          {searchResults.length > 0 && (
            <div className="max-h-40 overflow-y-auto border rounded">
              {searchResults.map((ex) => (
                <button
                  key={ex.id}
                  onClick={() => addExerciseFromLibrary(ex)}
                  className="block w-full px-3 py-1.5 text-left text-sm hover:bg-indigo-50"
                >
                  {ex.name}{" "}
                  <span className="text-xs text-slate-400">{ex.muscleGroup}</span>
                </button>
              ))}
            </div>
          )}
          <div className="flex gap-2">
            <input
              type="text"
              placeholder="Or type exercise name…"
              value={freeTextName}
              onChange={(e) => setFreeTextName(e.target.value)}
              className="border rounded px-3 py-2 flex-1 text-sm"
            />
            <button
              onClick={addExerciseFreeText}
              disabled={!freeTextName.trim()}
              className="bg-indigo-600 text-white rounded px-3 py-2 text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
            >
              Add
            </button>
          </div>
          <button
            onClick={() => {
              setAddingEx(false);
              setSearchQuery("");
              setSearchResults([]);
              setFreeTextName("");
            }}
            className="text-xs text-slate-500 hover:text-slate-700"
          >
            Cancel
          </button>
        </div>
      ) : (
        <button
          onClick={() => setAddingEx(true)}
          className="border-2 border-dashed border-slate-300 rounded p-3 text-center text-sm text-slate-500 hover:border-indigo-400 hover:text-indigo-600"
        >
          + Add Exercise
        </button>
      )}

      {finishing && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 flex flex-col gap-4 max-w-sm w-full">
            <h3 className="text-lg font-bold text-slate-800">Finish workout?</h3>
            <p className="text-sm text-slate-600">
              {exercises.length} exercise{exercises.length !== 1 ? "s" : ""},{" "}
              {exercises.reduce((sum, ex) => sum + ex.sets.filter((s) => s.logged).length, 0)} sets logged.
            </p>
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => setFinishing(false)}
                className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800"
              >
                Cancel
              </button>
              <button
                onClick={confirmFinish}
                className="bg-indigo-600 text-white rounded px-4 py-2 text-sm font-medium hover:bg-indigo-700"
              >
                Finish
              </button>
            </div>
          </div>
        </div>
      )}

      <ExerciseDemoModal
        exercise={demoExercise}
        onClose={() => setDemoExercise(null)}
      />
    </div>
  );
}
