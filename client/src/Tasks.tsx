import { useCallback, useEffect, useState } from "react";
import { listTasks, createTask, updateTask, deleteTask, type Task } from "./tasksApi";

interface Props {
  token: string;
}

const RECURRENCE_OPTIONS = ["none", "daily", "weekly"] as const;

function recurrenceLabel(r: string): string {
  if (r === "daily") return "Daily";
  if (r === "weekly") return "Weekly";
  return "";
}

export default function Tasks({ token }: Props) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [title, setTitle] = useState("");
  const [notes, setNotes] = useState("");
  const [category, setCategory] = useState("");
  const [dueAt, setDueAt] = useState("");
  const [recurrence, setRecurrence] = useState<string>("none");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const data = await listTasks(token);
      setTasks(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load tasks");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function onAdd(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    try {
      await createTask(token, {
        title: title.trim(),
        notes: notes.trim() || null,
        category: category.trim() || null,
        dueAt: dueAt || null,
        recurrence: recurrence === "none" ? undefined : recurrence,
      });
      setTitle("");
      setNotes("");
      setCategory("");
      setDueAt("");
      setRecurrence("none");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create task");
    }
  }

  async function onToggle(t: Task) {
    try {
      await updateTask(token, t.id, {
        status: t.status === "DONE" ? "PENDING" : "DONE",
      });
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update task");
    }
  }

  async function onDelete(id: string) {
    try {
      await deleteTask(token, id);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete task");
    }
  }

  function formatDate(iso: string | null): string {
    if (!iso) return "";
    const d = new Date(iso);
    return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  }

  if (loading) {
    return (
      <div className="w-full max-w-2xl text-center text-slate-400 py-8">Loading tasks…</div>
    );
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      <form onSubmit={onAdd} className="flex flex-col gap-2 border rounded bg-white p-4">
        <input
          className="border rounded px-3 py-2"
          placeholder="Task title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />
        <input
          className="border rounded px-3 py-2"
          placeholder="Notes (optional)"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
        />
        <div className="flex gap-2">
          <input
            className="border rounded px-3 py-2 flex-1"
            placeholder="Category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          />
          <input
            className="border rounded px-3 py-2 flex-1"
            type="datetime-local"
            value={dueAt}
            onChange={(e) => setDueAt(e.target.value)}
          />
          <select
            className="border rounded px-3 py-2"
            value={recurrence}
            onChange={(e) => setRecurrence(e.target.value)}
          >
            {RECURRENCE_OPTIONS.map((r) => (
              <option key={r} value={r}>
                {r === "none" ? "No repeat" : r.charAt(0).toUpperCase() + r.slice(1)}
              </option>
            ))}
          </select>
        </div>
        <button className="bg-indigo-600 text-white rounded py-2 font-medium">
          Add Task
        </button>
      </form>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      {tasks.length === 0 ? (
        <div className="text-center text-slate-400 py-8">No tasks yet. Add one above!</div>
      ) : (
        <ul className="flex flex-col gap-2">
          {tasks.map((t) => (
            <li
              key={t.id}
              className={`border rounded p-3 bg-white flex items-center gap-3 ${
                t.status === "DONE" ? "opacity-50" : ""
              }`}
            >
              <button
                onClick={() => onToggle(t)}
                className={`w-6 h-6 rounded-full border-2 flex-shrink-0 flex items-center justify-center ${
                  t.status === "DONE"
                    ? "bg-indigo-600 border-indigo-600 text-white"
                    : "border-slate-300"
                }`}
                title={t.status === "DONE" ? "Mark pending" : "Mark done"}
              >
                {t.status === "DONE" && (
                  <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                )}
              </button>
              <div className={`flex-1 min-w-0 ${t.status === "DONE" ? "line-through" : ""}`}>
                <p className="text-slate-800 font-medium truncate">{t.title}</p>
                <div className="flex gap-2 text-xs text-slate-400">
                  {t.category && (
                    <span className="bg-slate-100 rounded px-2 py-0.5">{t.category}</span>
                  )}
                  {t.dueAt && <span>{formatDate(t.dueAt)}</span>}
                  {t.recurrence !== "none" && <span>{recurrenceLabel(t.recurrence)}</span>}
                </div>
              </div>
              <button
                onClick={() => onDelete(t.id)}
                className="text-slate-300 hover:text-red-500 flex-shrink-0"
                title="Delete"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
