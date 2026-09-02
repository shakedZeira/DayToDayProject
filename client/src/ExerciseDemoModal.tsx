import { useEffect, useRef, useState } from "react";
import { getExerciseArchetype } from "./exerciseDemos";
import ExerciseSVG from "./ExerciseSVG";

export interface DemoExercise {
  name: string;
  muscleGroup?: string | null;
  equipment?: string | null;
  isCompound?: boolean;
}

interface Props {
  exercise: DemoExercise | null;
  onClose: () => void;
}

export default function ExerciseDemoModal({ exercise, onClose }: Props) {
  const panelRef = useRef<HTMLDivElement | null>(null);
  const [watch, setWatch] = useState(false);

  useEffect(() => {
    if (!exercise) return;
    const prev = document.activeElement as HTMLElement | null;
    setWatch(false);
    const raf = requestAnimationFrame(() => panelRef.current?.focus());
    return () => {
      cancelAnimationFrame(raf);
      prev?.focus?.();
    };
  }, [exercise]);

  useEffect(() => {
    if (!exercise) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [exercise, onClose]);

  if (!exercise) return null;

  const archetype = getExerciseArchetype(exercise.name);
  const ytSrc =
    "https://www.youtube.com/embed?listType=search&list=" +
    encodeURIComponent(`${exercise.name} exercise demo`);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={`How to do ${exercise.name}`}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="flex max-h-[90vh] w-full max-w-lg flex-col gap-3 overflow-y-auto rounded-lg bg-white p-5 outline-none"
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="text-lg font-bold text-slate-800">{exercise.name}</h3>
            <p className="mt-0.5 text-xs leading-relaxed text-slate-500">{archetype.label}</p>
            {(exercise.muscleGroup ?? exercise.equipment) && (
              <div className="mt-1.5 flex flex-wrap gap-1">
                {exercise.muscleGroup && (
                  <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] font-medium text-indigo-600">
                    {exercise.muscleGroup}
                  </span>
                )}
                {exercise.equipment && (
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600">
                    {exercise.equipment}
                  </span>
                )}
              </div>
            )}
          </div>
          <button
            onClick={onClose}
            aria-label="Close demo"
            className="shrink-0 rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            <svg viewBox="0 0 20 20" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M5 5l10 10M15 5L5 15" strokeLinecap="round" />
            </svg>
          </button>
        </div>

        <div className="flex flex-col items-center gap-1 rounded border border-slate-100 bg-slate-50/70 py-2">
          <ExerciseSVG archetype={archetype} />
          <span className="text-[11px] font-medium uppercase tracking-wide text-slate-400">
            Animated demonstration
          </span>
        </div>

        <div className="flex flex-col gap-2">
          <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
            Watch demo
          </h4>
          {watch ? (
            <div className="aspect-video w-full overflow-hidden rounded bg-slate-900">
              <iframe
                className="h-full w-full"
                src={ytSrc}
                title={`${exercise.name} exercise demo video`}
                allow="autoplay; encrypted-media; picture-in-picture"
                allowFullScreen
                referrerPolicy="no-referrer-when-downgrade"
              />
            </div>
          ) : (
            <button
              onClick={() => setWatch(true)}
              className="flex items-center justify-center gap-2 rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
            >
              <svg viewBox="0 0 20 20" className="h-4 w-4" fill="currentColor">
                <path d="M6 4l10 6-10 6V4z" />
              </svg>
              Watch demo video
            </button>
          )}
        </div>
      </div>
    </div>
  );
}