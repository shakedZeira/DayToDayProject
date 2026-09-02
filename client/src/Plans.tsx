import { useCallback, useEffect, useState } from "react";
import type { RoutineView, RoutineCreateInput, ExerciseRecord } from "shared";
import { getRoutines, createRoutine, updateRoutine, deleteRoutine } from "./routineApi";
import { getExercises } from "./exerciseApi";
import ActiveWorkout from "./ActiveWorkout";
import ExerciseDemoModal, { type DemoExercise } from "./ExerciseDemoModal";

interface ExerciseDay {
  name: string;
  exercises: {
    exerciseId: string;
    exerciseName: string;
    order: number;
    targetSets: number;
    targetReps: number;
    targetWeight: number | null;
  }[];
}

interface RoutineForm {
  name: string;
  description: string;
  days: ExerciseDay[];
}

const emptyForm: RoutineForm = {
  name: "",
  description: "",
  days: [{ name: "Day 1", exercises: [] }],
};

type Mode = "list" | "new" | "edit" | "active";

interface ActiveInitial {
  title: string;
  xes: { exerciseId?: string; exerciseName: string; targetSets?: number; targetReps?: number }[];
}

export default function Plans({ token }: { token: string }) {
  const [routines, setRoutines] = useState<RoutineView[]>([]);
  const [mode, setMode] = useState<Mode>("list");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [activeInitial, setActiveInitial] = useState<ActiveInitial | null>(null);
  const [form, setForm] = useState<RoutineForm>(emptyForm);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchDayIdx, setSearchDayIdx] = useState<number | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<ExerciseRecord[]>([]);
  const [pickDays, setPickDays] = useState<Record<string, number>>({});
  const [demoExercise, setDemoExercise] = useState<DemoExercise | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await getRoutines(token);
      setRoutines(data);
      setError(null);
    } catch (e: any) {
      setError(e.message);
    }
  }, [token]);

  useEffect(() => {
    load();
  }, [load]);

  const startNew = () => {
    setMode("new");
    setEditingId(null);
    setForm(emptyForm);
    setError(null);
  };

  const startEdit = (routine: RoutineView) => {
    setMode("edit");
    setEditingId(routine.id);
    setForm({
      name: routine.name,
      description: routine.description ?? "",
      days: routine.days.map((d) => ({
        name: d.name,
        exercises: d.exercises.map((re) => ({
          exerciseId: re.exerciseId,
          exerciseName: re.exercise.name,
          order: re.order,
          targetSets: re.targetSets,
          targetReps: re.targetReps,
          targetWeight: re.targetWeight ?? null,
        })),
      })),
    });
    setError(null);
  };

  const handleSearch = async (q: string, dayIdx: number) => {
    setSearchQuery(q);
    setSearchDayIdx(dayIdx);
    if (q.length < 1) {
      setSearchResults([]);
      return;
    }
    const results = await getExercises(token, q);
    setSearchResults(results);
  };

  const addExercise = (dayIdx: number, ex: ExerciseRecord) => {
    setForm((prev) => {
      const days = [...prev.days];
      const day = { ...days[dayIdx] };
      day.exercises = [
        ...day.exercises,
        {
          exerciseId: ex.id,
          exerciseName: ex.name,
          order: day.exercises.length,
          targetSets: 3,
          targetReps: 8,
          targetWeight: null,
        },
      ];
      days[dayIdx] = day;
      return { ...prev, days };
    });
    setSearchQuery("");
    setSearchResults([]);
    setSearchDayIdx(null);
  };

  const removeExercise = (dayIdx: number, exIdx: number) => {
    setForm((prev) => {
      const days = [...prev.days];
      const day = { ...days[dayIdx] };
      day.exercises = day.exercises.filter((_, i) => i !== exIdx);
      day.exercises = day.exercises.map((e, i) => ({ ...e, order: i }));
      days[dayIdx] = day;
      return { ...prev, days };
    });
  };

  const updateExercise = (dayIdx: number, exIdx: number, field: string, value: any) => {
    setForm((prev) => {
      const days = [...prev.days];
      const day = { ...days[dayIdx] };
      const ex = { ...day.exercises[exIdx], [field]: value };
      day.exercises = [...day.exercises];
      day.exercises[exIdx] = ex;
      days[dayIdx] = day;
      return { ...prev, days };
    });
  };

  const addDay = () => {
    setForm((prev) => ({
      ...prev,
      days: [...prev.days, { name: `Day ${prev.days.length + 1}`, exercises: [] }],
    }));
  };

  const removeDay = (idx: number) => {
    setForm((prev) => ({
      ...prev,
      days: prev.days.filter((_, i) => i !== idx),
    }));
  };

  const handleSave = async () => {
    if (!form.name.trim()) {
      setError("Name is required");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const input: RoutineCreateInput = {
        name: form.name.trim(),
        description: form.description.trim() || null,
        days: form.days.map((d, i) => ({
          name: d.name.trim(),
          order: i,
          exercises: d.exercises.map((re) => ({
            order: re.order,
            targetSets: re.targetSets,
            targetReps: re.targetReps,
            targetWeight: re.targetWeight,
            exerciseId: re.exerciseId,
          })),
        })),
      };
      if (editingId) {
        await updateRoutine(token, editingId, input);
      } else {
        await createRoutine(token, input);
      }
      setEditingId(null);
      setForm(emptyForm);
      setMode("list");
      await load();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Delete this plan?")) return;
    setLoading(true);
    try {
      await deleteRoutine(token, id);
      await load();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const startPlan = (routine: RoutineView, dayIdx: number) => {
    const day = routine.days[dayIdx];
    if (!day) return;
    const now = new Date();
    const dateStr = now.toLocaleDateString(undefined, { month: "short", day: "numeric" });
    const title = `${routine.name} — ${day.name} ${dateStr}`;
    setActiveInitial({
      title,
      xes: day.exercises.map((re) => ({
        exerciseId: re.exerciseId,
        exerciseName: re.exercise.name,
        targetSets: re.targetSets,
        targetReps: re.targetReps,
      })),
    });
    setMode("active");
  };

  const onActiveFinish = async () => {
    setActiveInitial(null);
    setMode("list");
    await load();
  };

  if (mode === "active") {
    return (
      <ActiveWorkout
        token={token}
        initial={activeInitial ?? undefined}
        onFinish={onActiveFinish}
      />
    );
  }

  const isEditing = mode === "edit";
  const inEditor = mode === "new" || isEditing;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-slate-800">My Plans</h2>
        {!inEditor && (
          <button
            onClick={startNew}
            className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700"
          >
            + New Plan
          </button>
        )}
      </div>

      {error && <div className="rounded bg-red-50 p-2 text-sm text-red-700">{error}</div>}

      {inEditor ? (
        <div className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-700">
              {isEditing ? "Edit plan" : "New plan"}
            </h3>
            <button
              onClick={() => {
                setMode("list");
                setEditingId(null);
                setForm(emptyForm);
                setError(null);
              }}
              className="text-sm text-slate-500 hover:text-slate-700"
            >
              Cancel
            </button>
          </div>
          <input
            type="text"
            placeholder="Plan name"
            value={form.name}
            onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
          />
          <input
            type="text"
            placeholder="Description (optional)"
            value={form.description}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
            className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
          />

          {form.days.map((day, dayIdx) => (
            <div key={dayIdx} className="space-y-2 rounded border border-slate-100 bg-slate-50 p-3">
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  value={day.name}
                  onChange={(e) => {
                    setForm((prev) => {
                      const days = [...prev.days];
                      days[dayIdx] = { ...days[dayIdx], name: e.target.value };
                      return { ...prev, days };
                    });
                  }}
                  className="flex-1 rounded border border-slate-300 px-2 py-1 text-sm font-medium"
                />
                {form.days.length > 1 && (
                  <button
                    onClick={() => removeDay(dayIdx)}
                    className="text-xs text-red-500 hover:text-red-700"
                  >
                    Remove
                  </button>
                )}
              </div>

              {day.exercises.map((re, exIdx) => (
                <div key={exIdx} className="flex items-center gap-2 text-sm">
                  <span className="w-32 truncate text-slate-600">{re.exerciseName}</span>
                  <input
                    type="number"
                    value={re.targetSets}
                    onChange={(e) =>
                      updateExercise(dayIdx, exIdx, "targetSets", parseInt(e.target.value) || 1)
                    }
                    className="w-14 rounded border border-slate-300 px-1 py-0.5 text-center"
                    min={1}
                    title="Sets"
                  />
                  <span className="text-slate-400">x</span>
                  <input
                    type="number"
                    value={re.targetReps}
                    onChange={(e) =>
                      updateExercise(dayIdx, exIdx, "targetReps", parseInt(e.target.value) || 1)
                    }
                    className="w-14 rounded border border-slate-300 px-1 py-0.5 text-center"
                    min={1}
                    title="Reps"
                  />
                  <input
                    type="number"
                    value={re.targetWeight ?? ""}
                    onChange={(e) =>
                      updateExercise(
                        dayIdx,
                        exIdx,
                        "targetWeight",
                        e.target.value ? parseFloat(e.target.value) : null
                      )
                    }
                    className="w-16 rounded border border-slate-300 px-1 py-0.5 text-center"
                    placeholder="kg"
                    min={0}
                    title="Weight (kg)"
                  />
                  <button
                    onClick={() => setDemoExercise({ name: re.exerciseName })}
                    className="text-xs font-medium text-indigo-500 hover:text-indigo-700"
                  >
                    Demo
                  </button>
                  <button
                    onClick={() => removeExercise(dayIdx, exIdx)}
                    className="text-red-400 hover:text-red-600"
                  >
                    x
                  </button>
                </div>
              ))}

              {searchDayIdx === dayIdx && searchResults.length > 0 && (
                <div className="max-h-40 overflow-y-auto rounded border border-slate-200 bg-white">
                  {searchResults.map((ex) => (
                    <button
                      key={ex.id}
                      onClick={() => addExercise(dayIdx, ex)}
                      className="block w-full px-3 py-1.5 text-left text-sm hover:bg-indigo-50"
                    >
                      {ex.name} <span className="text-xs text-slate-400">{ex.muscleGroup}</span>
                    </button>
                  ))}
                </div>
              )}

              <input
                type="text"
                placeholder="Search exercises..."
                value={searchDayIdx === dayIdx ? searchQuery : ""}
                onChange={(e) => handleSearch(e.target.value, dayIdx)}
                onFocus={() => setSearchDayIdx(dayIdx)}
                className="w-full rounded border border-slate-300 px-2 py-1 text-sm"
              />
            </div>
          ))}

          <button onClick={addDay} className="text-sm text-indigo-600 hover:text-indigo-800">
            + Add Day
          </button>

          <button
            onClick={handleSave}
            disabled={loading}
            className="rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {loading ? "Saving..." : "Save plan"}
          </button>
        </div>
      ) : routines.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-300 bg-white p-8 text-center">
          <p className="text-sm text-slate-500">No plans yet — build your first plan</p>
          <button
            onClick={startNew}
            className="mt-4 rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            + New Plan
          </button>
        </div>
      ) : (
        <div className="space-y-2">
          {routines.map((r) => {
            const totalExercises = r.days.reduce((sum, d) => sum + d.exercises.length, 0);
            const multipleDays = r.days.length > 1;
            const dayIdx = pickDays[r.id] ?? 0;
            const day = r.days[dayIdx];
            return (
              <div
                key={r.id}
                className="rounded-lg border border-slate-200 bg-white p-4 flex flex-col gap-3"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <div className="font-semibold text-slate-800">{r.name}</div>
                    {r.description && (
                      <div className="text-xs text-slate-500">{r.description}</div>
                    )}
                    <div className="mt-1 text-xs text-slate-400">
                      {r.days.length} day{r.days.length !== 1 ? "s" : ""} · {totalExercises}{" "}
                      exercise{totalExercises !== 1 ? "s" : ""}
                    </div>
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <button
                      onClick={() => startEdit(r)}
                      className="rounded bg-slate-100 px-2 py-1 text-xs text-slate-600 hover:bg-slate-200"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => handleDelete(r.id)}
                      className="rounded bg-red-50 px-2 py-1 text-xs text-red-600 hover:bg-red-100"
                    >
                      Delete
                    </button>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  {multipleDays && (
                    <>
                      <label className="text-xs text-slate-500">Start day:</label>
                      <select
                        className="rounded border border-slate-300 px-2 py-1 text-sm"
                        value={dayIdx}
                        onChange={(e) =>
                          setPickDays((prev) => ({ ...prev, [r.id]: Number(e.target.value) }))
                        }
                      >
                        {r.days.map((d, i) => (
                          <option key={d.id} value={i}>
                            {d.name} ({d.exercises.length} exercise{d.exercises.length !== 1 ? "s" : ""})
                          </option>
                        ))}
                      </select>
                      {day && (
                        <span className="text-xs text-slate-400">
                          {day.exercises.length} exercise{day.exercises.length !== 1 ? "s" : ""}
                        </span>
                      )}
                    </>
                  )}
                  <button
                    onClick={() => startPlan(r, dayIdx)}
                    className="bg-indigo-600 text-white rounded px-5 py-2 text-sm font-semibold hover:bg-indigo-700"
                  >
                    Start
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      <ExerciseDemoModal
        exercise={demoExercise}
        onClose={() => setDemoExercise(null)}
      />
    </div>
  );
}
