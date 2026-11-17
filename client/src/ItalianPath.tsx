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
      <div className="border rounded-md3m p-6 bg-red-50 text-red-700 flex justify-between items-center">
        <span>{error}</span>
        <button className="border border-md-outline rounded-md3xs px-3 py-1" onClick={() => void refresh()}>
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
      <div className="md-card p-4 flex flex-wrap gap-6 text-sm">
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
          <span className="text-amber-700">
            {progress.reviewDue.length} mistake{progress.reviewDue.length > 1 ? "s" : ""} to review
          </span>
        )}
      </div>

      {result && (
        <div className="rounded-md3m bg-[#58CC02]/10 border border-[#58CC02]/30 px-4 py-3 text-sm text-[#58A700]">
          {result.lessonCompleted ? "Lesson complete! " : ""}
          +{result.xpGained} XP{result.lessonCompleted ? " 🎉" : ""}
        </div>
      )}

      {nextId && (
        <button
          className="bg-[#58CC02] text-white rounded-md3m px-6 py-3 font-medium self-start"
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
              <h3 className="font-bold text-[#58A700]"><span className="block text-xs">UNIT {course.units.indexOf(unit) + 1}</span>{unit.title}</h3>
              <span className="text-xs text-gray-500">
                {unitDone}/{unit.lessons.length}
              </span>
            </div>
            <div className="flex flex-wrap gap-3">
              {unit.lessons.map((l) => {
                const status = statuses[l.id];
                const isTest = l.kind === "test";
                return (
                  <button
                    key={l.id}
                    disabled={status === "locked"}
                    onClick={() => {
                      setResult(null);
                      setActiveLessonId(l.id);
                    }}
                    className={`rounded-full w-28 h-28 flex flex-col items-center justify-center gap-1 border-4 text-sm ${
                      status === "completed"
                        ? "border-[#FFC800] bg-[#FFC800]/10"
                        : status === "unlocked" && isTest
                          ? "border-[#FF9600] bg-[#FF9600]/10"
                          : status === "unlocked"
                            ? "border-[#58CC02] bg-[#58CC02]/10"
                            : "border-[#4B4B55]/40 bg-gray-100 text-slate-500"
                    }`}
                  >
                    <span className="text-2xl">
                      {status === "completed" ? "⭐" : isTest ? "📝" : status === "unlocked" ? "▶" : "🔒"}
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
