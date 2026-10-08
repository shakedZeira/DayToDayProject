import { useCallback, useEffect, useState } from "react";
import type { ItalianCourse, ItalianPathProgress, ItalianSubmitResult } from "shared";
import { getCourse, getPathProgress } from "./italianApi";
import { lessonStatuses, nextLessonId } from "./italianPathLogic";
import ItalianLessonRunner from "./ItalianLessonRunner";

interface Props {
  token: string;
}

export default function ItalianPath({ token }: Props) {
  const [course, setCourse] = useState<ItalianCourse | null>(null);
  const [progress, setProgress] = useState<ItalianPathProgress | null>(null);
  const [activeLessonId, setActiveLessonId] = useState<string | null>(null);
  const [result, setResult] = useState<ItalianSubmitResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [c, p] = await Promise.all([getCourse(token), getPathProgress(token)]);
      setCourse(c);
      setProgress(p);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed to load");
    }
  }, [token]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (error) {
    return (
      <div className="border rounded-lg p-6 bg-red-50 text-red-700 flex justify-between items-center">
        <span>{error}</span>
        <button className="border rounded px-3 py-1" onClick={() => void refresh()}>
          Retry
        </button>
      </div>
    );
  }
  if (!course || !progress) return <div className="p-6 text-gray-500">Loading…</div>;

  const completed = new Set(progress.completedLessonIds);
  const statuses = lessonStatuses(course, completed);
  const activeLesson = course.units
    .flatMap((u) => u.lessons)
    .find((l) => l.id === activeLessonId);

  if (activeLesson) {
    return (
      <ItalianLessonRunner
        token={token}
        lesson={activeLesson}
        onDone={(r) => {
          setActiveLessonId(null);
          setResult(r);
          void refresh();
        }}
      />
    );
  }

  const nextId = nextLessonId(course, completed);

  return (
    <div className="flex flex-col gap-5">
      <div className="border rounded-lg bg-white p-4 flex flex-wrap gap-6 text-sm">
        <span>
          <b>{progress.xp}</b> XP
        </span>
        <span>
          <b>{progress.streak}</b> day streak
        </span>
        <span>
          <b>{completed.size}</b>/{progress.totalLessons} lessons
        </span>
        {progress.reviewDue.length > 0 && (
          <span className="text-amber-600">
            {progress.reviewDue.length} mistake{progress.reviewDue.length > 1 ? "s" : ""} to review
          </span>
        )}
      </div>

      {result && (
        <div className="rounded-lg bg-emerald-50 border border-emerald-200 px-4 py-3 text-sm text-emerald-800">
          {result.lessonCompleted ? "Lesson complete! " : ""}
          +{result.xpGained} XP{result.lessonCompleted ? " 🎉" : ""}
        </div>
      )}

      {nextId && (
        <button
          className="bg-emerald-600 text-white rounded-lg px-6 py-3 font-medium self-start"
          onClick={() => {
            setResult(null);
            setActiveLessonId(nextId);
          }}
        >
          Continue: Next lesson
        </button>
      )}

      {course.units.map((unit) => {
        const unitDone = unit.lessons.filter((l) => completed.has(l.id)).length;
        return (
          <div key={unit.id} className="flex flex-col gap-3">
            <div className="flex justify-between items-baseline border-b pb-1">
              <h3 className="font-semibold">{unit.title}</h3>
              <span className="text-xs text-gray-500">
                {unitDone}/{unit.lessons.length}
              </span>
            </div>
            <div className="flex flex-wrap gap-3">
              {unit.lessons.map((l) => {
                const status = statuses[l.id];
                return (
                  <button
                    key={l.id}
                    disabled={status === "locked"}
                    onClick={() => {
                      setResult(null);
                      setActiveLessonId(l.id);
                    }}
                    className={`rounded-full w-36 h-36 flex flex-col items-center justify-center gap-1 border-4 text-sm ${
                      status === "completed"
                        ? "border-yellow-400 bg-yellow-50"
                        : status === "unlocked"
                          ? "border-emerald-500 bg-emerald-50"
                          : "border-gray-300 bg-gray-50 text-gray-400"
                    }`}
                  >
                    <span className="text-2xl">
                      {status === "completed" ? "⭐" : status === "unlocked" ? "▶" : "🔒"}
                    </span>
                    <span className="px-2 text-center font-medium">{l.title}</span>
                  </button>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
