import { useCallback, useEffect, useState } from "react";
import type { RoutineView, RoutineCreateInput, ExerciseRecord } from "shared";
import { getRoutines, createRoutine, updateRoutine, deleteRoutine } from "./routineApi";
import { getExercises } from "./exerciseApi";
import ActiveWorkout from "./ActiveWorkout";
import { toggleLinkWithPrevious, supersetLabel } from "./supersets";
import ExerciseDemoModal, { type DemoExercise } from "./ExerciseDemoModal";

interface ExerciseItem {
  exerciseId: string;
  exerciseName: string;
  targetSets: number;
  targetReps: number;
  supersetGroup?: number | null;
}

interface RoutineForm {
  name: string;
  description: string;
  exercises: ExerciseItem[];
}

const emptyForm: RoutineForm = {
  name: "",
  description: "",
  exercises: [],
};

type Mode = "list" | "new" | "edit" | "active";

interface ActiveInitial {
  title: string;
  xes: { exerciseId?: string; exerciseName: string; targetSets?: number; targetReps?: number; supersetGroup?: number | null }[];
}

export default function Plans({ token }: { token: string }) {
  const [routines, setRoutines] = useState<RoutineView[]>([]);
  const [mode, setMode] = useState<Mode>("list");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [activeInitial, setActiveInitial] = useState<ActiveInitial | null>(null);
  const [form, setForm] = useState<RoutineForm>(emptyForm);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<ExerciseRecord[]>([]);
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

  useEffect(() => { load(); }, [load]);

  const startNew = () => {
    setMode("new");
    setEditingId(null);
    setForm(emptyForm);
    setError(null);
  };

  const startEdit = (routine: RoutineView) => {
    setMode("edit");
    setEditingId(routine.id);
    const day = routine.days[0];
    setForm({
      name: routine.name,
      description: routine.description ?? "",
      exercises: day
        ? day.exercises.map((re) => ({
            exerciseId: re.exerciseId,
            exerciseName: re.exercise.name,
            targetSets: re.targetSets,
            targetReps: re.targetReps,
            supersetGroup: re.supersetGroup ?? null,
          }))
        : [],
    });
    setError(null);
  };

  const handleSearch = async (q: string) => {
    setSearchQuery(q);
    if (q.length < 1) { setSearchResults([]); return; }
    const results = await getExercises(token, q);
    setSearchResults(results);
  };

  const addExercise = (ex: ExerciseRecord) => {
    setForm((prev) => ({
      ...prev,
      exercises: [
        ...prev.exercises,
        { exerciseId: ex.id, exerciseName: ex.name, targetSets: 3, targetReps: 8 },
      ],
    }));
    setSearchQuery("");
    setSearchResults([]);
    setSearchOpen(false);
  };

  const removeExercise = (idx: number) => {
    setForm((prev) => ({
      ...prev,
      exercises: prev.exercises.filter((_, i) => i !== idx),
    }));
  };

  const updateExerciseField = (idx: number, field: string, value: any) => {
    setForm((prev) => {
      const exercises = [...prev.exercises];
      exercises[idx] = { ...exercises[idx], [field]: value };
      return { ...prev, exercises };
    });
  };

  const toggleSuperset = (idx: number) => {
    setForm((prev) => {
      const groups = toggleLinkWithPrevious(prev.exercises.map((e) => e.supersetGroup), idx);
      return { ...prev, exercises: prev.exercises.map((e, i) => ({ ...e, supersetGroup: groups[i] })) };
    });
  };

  const moveExercise = (idx: number, dir: -1 | 1) => {
    setForm((prev) => {
      const exercises = [...prev.exercises];
      const target = idx + dir;
      if (target < 0 || target >= exercises.length) return prev;
      [exercises[idx], exercises[target]] = [exercises[target], exercises[idx]];
      return { ...prev, exercises };
    });
  };

  const handleSave = async () => {
    if (!form.name.trim()) { setError("Name is required"); return; }
    if (form.exercises.length === 0) { setError("Add at least one exercise"); return; }
    setLoading(true);
    setError(null);
    try {
      const input: RoutineCreateInput = {
        name: form.name.trim(),
        description: form.description.trim() || null,
        days: [{
          name: "Workout",
          order: 0,
          exercises: form.exercises.map((re, i) => ({
            order: i,
            targetSets: re.targetSets,
            targetReps: re.targetReps,
            targetWeight: null,
            supersetGroup: re.supersetGroup ?? null,
            exerciseId: re.exerciseId,
          })),
        }],
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
    if (!confirm("Delete this workout?")) return;
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

  const startPlan = (routine: RoutineView) => {
    const day = routine.days[0];
    if (!day || day.exercises.length === 0) return;
    const now = new Date();
    const dateStr = now.toLocaleDateString(undefined, { month: "short", day: "numeric" });
    setActiveInitial({
      title: `${routine.name} — ${dateStr}`,
      xes: day.exercises.map((re) => ({
        exerciseId: re.exerciseId,
        exerciseName: re.exercise.name,
        targetSets: re.targetSets,
        targetReps: re.targetReps,
        supersetGroup: re.supersetGroup ?? null,
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
        <h2 className="text-xl font-bold text-slate-800">My Workouts</h2>
        {!inEditor && (
          <button
            onClick={startNew}
            className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700"
          >
            + New Workout
          </button>
        )}
      </div>

      {error && <div className="rounded bg-red-50 p-2 text-sm text-red-700">{error}</div>}

      {inEditor ? (
        <div className="space-y-3 rounded-md3m border border-slate-200 bg-white p-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-700">
              {isEditing ? "Edit workout" : "New workout"}
            </h3>
            <button
              onClick={() => { setMode("list"); setEditingId(null); setForm(emptyForm); setError(null); }}
              className="text-sm text-slate-500 hover:text-slate-700"
            >
              Cancel
            </button>
          </div>
          <input aria-label="Workout name"
            type="text"
            placeholder="Workout name"
            value={form.name}
            onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            className="w-full rounded-md3xs border border-md-outline border-md-outline px-3 py-2 text-sm"
          />
          <input aria-label="Description (optional)"
            type="text"
            placeholder="Description (optional)"
            value={form.description}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
            className="w-full rounded-md3xs border border-md-outline border-md-outline px-3 py-2 text-sm"
          />

          {form.exercises.length > 0 && (
            <div className="space-y-1.5">
              {form.exercises.map((re, idx) => (
                <div key={idx} className="flex items-center gap-2 text-sm">
                  <div className="flex flex-col gap-0.5 mr-1">
                    <button aria-label={`Move ${re.exerciseName} up`} onClick={() => moveExercise(idx, -1)} className="text-slate-500 hover:text-slate-600 leading-none" title="Move up">&#9650;</button>
                    <button aria-label={`Move ${re.exerciseName} down`} onClick={() => moveExercise(idx, 1)} className="text-slate-500 hover:text-slate-600 leading-none" title="Move down">&#9660;</button>
                  </div>
                  <span className="w-36 truncate text-slate-600 font-medium">{re.exerciseName}</span>
                  <input aria-label="Target sets"
                    type="number"
                    value={re.targetSets}
                    onChange={(e) => updateExerciseField(idx, "targetSets", parseInt(e.target.value) || 1)}
                    className="w-12 rounded-md3xs border border-md-outline border-md-outline px-1 py-0.5 text-center text-xs"
                    min={1}
                    title="Sets"
                  />
                  <span className="text-xs text-slate-500">×</span>
                  <input aria-label="Target reps"
                    type="number"
                    value={re.targetReps}
                    onChange={(e) => updateExerciseField(idx, "targetReps", parseInt(e.target.value) || 1)}
                    className="w-12 rounded-md3xs border border-md-outline border-md-outline px-1 py-0.5 text-center text-xs"
                    min={1}
                    title="Reps"
                  />
                  {idx > 0 && (
                    <button
                      onClick={() => toggleSuperset(idx)}
                      title="Superset with the previous exercise"
                      className={`text-xs font-medium ${re.supersetGroup != null && re.supersetGroup === form.exercises[idx - 1].supersetGroup ? "text-violet-700" : "text-slate-400"} hover:text-violet-700`}
                    >
                      SS
                    </button>
                  )}
                  {supersetLabel(re.supersetGroup) && (
                    <span className="text-xs text-violet-600">{supersetLabel(re.supersetGroup)}</span>
                  )}
                  <button
                    onClick={() => setDemoExercise({ name: re.exerciseName })}
                    className="text-xs font-medium text-indigo-500 hover:text-indigo-700"
                  >
                    Demo
                  </button>
                  <button
                    aria-label={`Remove ${re.exerciseName}`}
                    onClick={() => removeExercise(idx)}
                    className="ml-auto text-xs text-red-600 hover:text-red-600"
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          )}

          {searchOpen ? (
            <div className="space-y-2 rounded-md3xs border border-md-outline border-slate-200 bg-slate-50 p-3">
              <input aria-label="Search exercises"
                type="text"
                placeholder="Search exercises..."
                value={searchQuery}
                onChange={(e) => handleSearch(e.target.value)}
                className="w-full rounded-md3xs border border-md-outline border-md-outline px-2 py-1.5 text-sm"
                autoFocus
              />
              {searchResults.length > 0 && (
                <div className="max-h-48 overflow-y-auto rounded-md3xs border border-md-outline border-slate-200 bg-white">
                  {searchResults.map((ex) => (
                    <button
                      key={ex.id}
                      onClick={() => addExercise(ex)}
                      className="flex w-full items-center justify-between px-3 py-1.5 text-left text-sm hover:bg-indigo-50"
                    >
                      <span>{ex.name}</span>
                      <span className="text-xs text-slate-500">{ex.muscleGroup}</span>
                    </button>
                  ))}
                </div>
              )}
              <button
                onClick={() => { setSearchOpen(false); setSearchQuery(""); setSearchResults([]); }}
                className="text-xs text-slate-500 hover:text-slate-700"
              >
                Cancel
              </button>
            </div>
          ) : (
            <button
              onClick={() => setSearchOpen(true)}
              className="w-full rounded border-2 border-dashed border-md-outline py-2 text-sm text-slate-500 hover:border-indigo-400 hover:text-indigo-600"
            >
              + Add Exercise
            </button>
          )}

          <button
            onClick={handleSave}
            disabled={loading}
            className="rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {loading ? "Saving..." : "Save workout"}
          </button>
        </div>
      ) : routines.length === 0 ? (
        <div className="rounded-md3m border border-dashed border-md-outline bg-white p-8 text-center">
          <p className="text-sm text-slate-500">No workouts yet — create your first workout plan</p>
          <button
            onClick={startNew}
            className="mt-4 rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            + New Workout
          </button>
        </div>
      ) : (
        <div className="space-y-2">
          {routines.map((r) => {
            const day = r.days[0];
            const exCount = day ? day.exercises.length : 0;
            return (
              <div
                key={r.id}
                className="rounded-md3m border border-slate-200 bg-white p-4 flex items-center justify-between gap-3"
              >
                <div className="min-w-0">
                  <div className="font-semibold text-slate-800">{r.name}</div>
                  {r.description && <div className="text-xs text-slate-500">{r.description}</div>}
                  <div className="mt-0.5 text-xs text-slate-500">
                    {exCount} exercise{exCount !== 1 ? "s" : ""}
                    {day && (
                      <span className="ml-2">
                        {day.exercises.map((re) => re.exercise.name).join(", ")}
                      </span>
                    )}
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
                  <button
                    onClick={() => startPlan(r)}
                    className="rounded bg-indigo-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-indigo-700"
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
