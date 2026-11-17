import { useCallback, useEffect, useState } from "react";
import { getTodayDigest } from "./digestApi";
import type { DigestResponse } from "shared";

interface Props {
  token: string;
  onNavigate?: (view: string) => void;
}

// Mirrors HomeScreen.kt: "Welcome back!" headline, subtitle, content cards (DayTodayCard, 12dp radius,
// 16dp padding, 16dp gaps), the "Workout Progress & History" link card and the "Quick Actions" button rows.
// Android's step/calorie tiles come from Health Connect and have no web equivalent, so the web shows the
// digest cards (focus task, workout, Italian) in their place.
function StatCard({ title, value, hint, color, onClick }: { title: string; value: string; hint?: string; color: string; onClick?: () => void }) {
  const body = (
    <div className="flex flex-col items-center p-4 text-center">
      <span className="md-headline-medium" style={{ color }}>{value}</span>
      <span className="md-body-small text-md-onSurfaceVariant">{title}</span>
      {hint && <span className="md-label-small text-md-primary mt-1">{hint}</span>}
    </div>
  );
  return onClick ? (
    <button className="md-card w-full" onClick={onClick}>{body}</button>
  ) : (
    <div className="md-card w-full">{body}</div>
  );
}

export default function DigestView({ token, onNavigate }: Props) {
  const [digest, setDigest] = useState<DigestResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setError(null);
    try {
      setDigest(await getTodayDigest(token));
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed");
    }
  }, [token]);

  useEffect(() => { refresh(); }, [refresh]);

  const go = (v: string) => onNavigate?.(v);

  if (error && !digest) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <p className="md-body-medium text-md-error">{error}</p>
        <button className="md-btn-filled" onClick={refresh}>Retry</button>
      </div>
    );
  }
  if (!digest) return <p className="md-body-medium text-md-onSurfaceVariant text-center py-16">Loading your day...</p>;

  const target = 2;
  return (
    <div className="flex flex-col gap-4">
      <h2 className="md-headline-medium text-md-onSurface">Welcome back!</h2>
      <p className="md-body-medium text-md-onSurfaceVariant -mt-3">Here's your daily overview</p>

      <div className="grid grid-cols-2 gap-3">
        <StatCard title="Workouts this week" value={`${digest.health.workoutsThisWeek}/${target}`} color="#3B82F6" onClick={() => go("workouts")} hint="Tap to open" />
        <StatCard title="Tasks due today" value={String(digest.tasksToday.length)} color="#10B981" onClick={() => go("tasks")} hint="Tap to open" />
        <StatCard title="Italian streak" value={`${digest.italian.streak}`} color="#F59E0B" onClick={() => go("italian")} />
        <StatCard title="Lessons done" value={String(digest.italian.totalLessons)} color="#EF4444" />
      </div>

      <div className="flex flex-col gap-1.5">
        <div className="flex justify-between">
          <span className="md-title-medium text-md-onSurface">Weekly Workout Goal</span>
          <span className="md-body-medium text-md-onSurfaceVariant">{digest.health.workoutsThisWeek} / {target}</span>
        </div>
        <div className="md-progress">
          <div style={{ width: `${Math.min(100, (digest.health.workoutsThisWeek / target) * 100)}%` }} />
        </div>
      </div>

      <section className="md-card p-4 flex flex-col gap-1">
        <h3 className="md-title-medium text-md-onSurface">Focus task</h3>
        {digest.topTask ? (
          <p className="md-body-medium text-md-onSurface">{digest.topTask.title}</p>
        ) : (
          <p className="md-body-medium text-md-onSurfaceVariant">Nothing pressing — enjoy the day.</p>
        )}
      </section>
      <section className="md-card p-4 flex flex-col gap-1">
        <h3 className="md-title-medium text-md-onSurface">Workout</h3>
        <p className={`md-body-medium ${digest.health.reminder ? "text-md-onSurface" : "text-md-onSurfaceVariant"}`}>
          {digest.health.reminder ?? "On track this week."}
        </p>
      </section>
      <section className="md-card p-4 flex flex-col gap-1">
        <h3 className="md-title-medium text-md-onSurface">Italian</h3>
        <p className={`md-body-medium ${digest.italian.lessonTitle ? "text-md-onSurface" : "text-md-onSurfaceVariant"}`}>
          {digest.italian.lessonTitle ?? "No lesson queued today."}
        </p>
      </section>

      <button className="md-card w-full p-4 flex items-center gap-3.5 text-left" onClick={() => go("progress")}>
        <svg viewBox="0 0 24 24" className="w-8 h-8 fill-md-primary" aria-hidden><path d="M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z" /></svg>
        <span className="flex-1">
          <span className="block md-title-medium font-bold text-md-onSurface">Workout Progress &amp; History</span>
          <span className="block md-body-small text-md-onSurfaceVariant">View volume stats, personal bests &amp; workout trends</span>
        </span>
        <svg viewBox="0 0 24 24" className="w-6 h-6 fill-md-onSurfaceVariant" aria-label="Open Workout Progress"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" /></svg>
      </button>

      <h3 className="md-title-large text-md-onSurface">Quick Actions</h3>
      <div className="flex gap-3">
        <button className="md-btn-filled flex-1" onClick={() => go("tasks")}>Tasks</button>
        <button className="md-btn-filled flex-1" onClick={() => go("workouts")}>Build Workout</button>
      </div>
      <div className="flex gap-3">
        <button className="md-btn-filled flex-1" onClick={() => go("progress")}>Workout Progress</button>
      </div>
    </div>
  );
}
