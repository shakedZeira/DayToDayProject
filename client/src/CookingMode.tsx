import { useCallback, useEffect, useRef, useState } from "react";
import type { RecipeInstructionStep, RecipeView } from "shared";

interface Props {
  token: string;
  recipe: RecipeView;
  onComplete: () => void;
  onClose: () => void;
}

function formatTime(seconds: number) {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

export default function CookingMode({ token, recipe, onComplete, onClose }: Props) {
  const instructions = [...(recipe.instructions ?? [])].sort((a, b) => a.step - b.step);
  const [doneSteps, setDoneSteps] = useState<Set<number>>(new Set());
  const [timerState, setTimerState] = useState<Map<number, { remaining: number; running: boolean }>>(new Map());
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const anyRunning = useCallback(() => {
    for (const t of timerState.values()) if (t.running) return true;
    return false;
  }, [timerState]);

  // Global tick for all running countdown timers.
  useEffect(() => {
    if (!anyRunning()) {
      if (intervalRef.current) clearInterval(intervalRef.current);
      intervalRef.current = null;
      return;
    }
    intervalRef.current = setInterval(() => {
      setTimerState((map) => {
        let changed = false;
        const next = new Map(map);
        for (const [stepStr, t] of next.entries()) {
          if (t.running && t.remaining > 0) {
            next.set(Number(stepStr), { remaining: t.remaining - 1, running: true });
            changed = true;
          }
        }
        return changed ? next : map;
      });
    }, 1000);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
      intervalRef.current = null;
    };
  }, [anyRunning]);

  const isDone = (step: number) => doneSteps.has(step);
  const timerFor = (step: number) => timerState.get(step);

  const toggleDone = (step: number) => {
    setDoneSteps((prev) => {
      const next = new Set(prev);
      if (next.has(step)) next.delete(step);
      else next.add(step);
      return next;
    });
  };

  const startTimer = (step: number) => {
    const stepInfo = instructions.find((i) => i.step === step);
    const seconds = stepInfo?.timerSeconds ?? 0;
    if (!seconds || seconds <= 0) return;
    setTimerState((map) => {
      const next = new Map(map);
      next.set(step, { remaining: seconds, running: true });
      return next;
    });
  };

  const pauseTimer = (step: number) => {
    setTimerState((map) => {
      const next = new Map(map);
      const t = next.get(step);
      if (t) next.set(step, { ...t, running: false });
      return next;
    });
  };

  const resetTimer = (step: number) => {
    const stepInfo = instructions.find((i) => i.step === step);
    const seconds = stepInfo?.timerSeconds ?? 0;
    setTimerState((map) => {
      const next = new Map(map);
      next.set(step, { remaining: seconds, running: false });
      return next;
    });
  };

  const allDone = instructions.length > 0 && instructions.every((i) => doneSteps.has(i.step));

  return (
    <div className="flex flex-col h-full" dir="rtl" lang="he">
      <div className="flex items-center justify-between gap-2 px-4 py-3 border-b border-slate-200 bg-white">
        <button
          onClick={onClose}
          className="text-slate-500 hover:text-slate-700 font-medium"
        >
          → חזרה למתכון
        </button>
        <h2 className="text-lg font-bold text-slate-800 flex-1">{recipe.title}</h2>
        <span className={`text-xs font-medium rounded-full px-2 py-1 ${
          allDone ? "bg-green-100 text-green-700" : "bg-slate-100 text-slate-600"
        }`}>
          {doneSteps.size}/{instructions.length} בוצע
        </span>
      </div>

      <div className="flex-1 overflow-y-auto p-4">
        <p className="text-sm text-slate-500 mb-4">
          {recipe.description ?? "אין תיאור זמין."}
        </p>

        {instructions.length === 0 ? (
          <div className="text-center text-slate-500 text-sm py-8" dir="rtl" lang="he">אין הוראות למתכון הזה.</div>
        ) : (
          <ul className="flex flex-col gap-3">
            {instructions.map((step) => {
              const done = isDone(step.step);
              const timer = timerFor(step.step);
              const hasTimer = Boolean(step.timerSeconds && step.timerSeconds > 0);
              const remaining = timer?.remaining ?? step.timerSeconds ?? 0;
              const running = Boolean(timer?.running);
              const finished = hasTimer && remaining === 0 && !running;

              return (
                <li
                  key={step.step}
                  className={`border rounded-xl p-3 flex flex-col gap-2 ${
                    done ? "bg-green-50 border-green-200" : "bg-white border-slate-200"
                  }`}
                >
                  <div className="flex items-start gap-3">
                    <input aria-label={`Step ${step.step} done`}
                      type="checkbox"
                      checked={done}
                      onChange={() => toggleDone(step.step)}
                      className="mt-1 w-5 h-5 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-xs font-bold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded">
                          שלב {step.step}
                        </span>
                        {done && (
                          <span className="text-xs font-medium text-green-700 bg-green-50 px-2 py-0.5 rounded">
                            בוצע
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-slate-700 leading-relaxed">{step.text}</p>

                      {hasTimer && (
                        <div className="mt-2 flex items-center gap-2">
                          <span className={`text-sm font-mono font-bold ${
                            running ? "text-indigo-600" : finished ? "text-green-700" : "text-slate-500"
                          }`}>
                            {formatTime(remaining)}
                          </span>
                          {!running && !finished && remaining > 0 && (
                            <button
                              onClick={() => startTimer(step.step)}
                              className="text-xs text-indigo-600 hover:text-indigo-800 font-medium underline"
                            >
                              הפעל טיימר
                            </button>
                          )}
                          {running && (
                            <button
                              onClick={() => pauseTimer(step.step)}
                              className="text-xs text-slate-600 hover:text-slate-900 font-medium underline"
                            >
                              השהה
                            </button>
                          )}
                          {(running || finished) && (
                            <button
                              onClick={() => resetTimer(step.step)}
                              className="text-xs text-slate-500 hover:text-slate-600 font-medium underline"
                            >
                              איפוס
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        )}

        {allDone && (
          <div className="mt-6 p-4 rounded-xl bg-green-50 border border-green-200">
            <h3 className="font-bold text-green-800">כל השלבים הושלמו! 🎉</h3>
            <p className="text-sm text-green-700 mt-1">סיימת לבשל את {recipe.title}.</p>
          </div>
        )}
      </div>

      <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 flex justify-end gap-2">
        <button onClick={onClose} className="text-slate-600 font-medium px-3 py-1.5 rounded hover:bg-slate-200">
          סגירה
        </button>
        {allDone && (
          <button onClick={onComplete} className="bg-indigo-600 text-white font-medium px-3 py-1.5 rounded hover:bg-indigo-700">
            סמן מתכון כמוכן
          </button>
        )}
      </div>
    </div>
  );
}
