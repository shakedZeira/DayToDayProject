import { useCallback, useEffect, useState } from "react";
import { getTodayDigest } from "./digestApi";
import type { DigestResponse } from "shared";

interface Props {
  token: string;
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="bg-white rounded-xl shadow-sm p-4 flex flex-col gap-2">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">{title}</h3>
      {children}
    </section>
  );
}

export default function DigestView({ token }: Props) {
  const [digest, setDigest] = useState<DigestResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setDigest(await getTodayDigest(token));
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed");
    }
  }, [token]);

  useEffect(() => { refresh(); }, [refresh]);

  if (error) return <p className="text-red-600">{error}</p>;
  if (!digest) return <p className="text-slate-400">Loading your day…</p>;

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Today</h2>
        <p className="text-sm text-slate-400">{digest.date}</p>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Card title="Focus task">
          {digest.topTask ? (
            <p className="text-lg text-slate-800">{digest.topTask.title}</p>
          ) : (
            <p className="text-slate-400">Nothing pressing — enjoy the day.</p>
          )}
          {digest.tasksToday.length > 1 && (
            <p className="text-xs text-slate-400">{digest.tasksToday.length} tasks due today</p>
          )}
        </Card>
        <Card title="Workout">
          {digest.health.reminder ? (
            <p className="text-slate-800">{digest.health.reminder}</p>
          ) : (
            <p className="text-slate-400">{digest.health.workoutsThisWeek}/2 workouts this week — on track.</p>
          )}
        </Card>
        <Card title="Italian">
          {digest.italian.lessonTitle ? (
            <p className="text-slate-800">{digest.italian.lessonTitle}</p>
          ) : (
            <p className="text-slate-400">No lesson queued today.</p>
          )}
          <p className="text-xs text-slate-400">Streak {digest.italian.streak} days · {digest.italian.totalLessons} lessons</p>
        </Card>
        <Card title="Study">
          {digest.study.type !== "none" ? (
            <>
              <p className="text-slate-800">{digest.study.detail}</p>
              {digest.study.title && <p className="text-xs text-slate-400">{digest.study.title}</p>}
            </>
          ) : (
            <p className="text-slate-400">No study material queued for today.</p>
          )}
        </Card>
      </div>
    </div>
  );
}
