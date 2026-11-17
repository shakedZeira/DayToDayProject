// Weekly review view. NOT wired into Nav/App yet: render <WeeklyReview token={token} /> from a tab or Settings.
import { useEffect, useState } from "react";
import type { WeeklyReviewResponse } from "shared";
import { authedFetch } from "./auth";

async function getWeeklyReview(token: string): Promise<WeeklyReviewResponse> {
  const res = await authedFetch(token, "/api/review/weekly");
  if (!res.ok) throw new Error("failed to load weekly review");
  return res.json();
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="bg-white rounded-xl shadow-sm p-4 flex flex-col gap-2">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">{title}</h3>
      {children}
    </section>
  );
}

export default function WeeklyReview({ token }: { token: string }) {
  const [review, setReview] = useState<WeeklyReviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getWeeklyReview(token).then(setReview).catch((e) => setError(e instanceof Error ? e.message : "failed"));
  }, [token]);

  if (error) return <p className="text-red-600">{error}</p>;
  if (!review) return <p className="text-slate-400">Loading your week…</p>;

  const { tasks, workouts, nutrition, italian, personalRecords } = review;
  const max = Math.max(1, ...tasks.completedByDay);

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Weekly review</h2>
        <p className="text-sm text-slate-400">{review.from} – {review.to}</p>
      </div>

      <Card title="Tasks">
        <p className="text-slate-700">{tasks.completed} completed</p>
        <div className="flex items-end gap-1 h-12" aria-hidden="true">
          {tasks.completedByDay.map((n, i) => (
            <div key={i} className="flex-1 bg-indigo-200 rounded-sm" style={{ height: `${(n / max) * 100}%`, minHeight: 2 }} />
          ))}
        </div>
      </Card>

      <Card title="Workouts">
        <p className="text-slate-700">{workouts.count} of {workouts.target} target</p>
        {workouts.days.length > 0 && <p className="text-xs text-slate-400">{workouts.days.join(", ")}</p>}
      </Card>

      <Card title="Calories">
        {nutrition.daysLogged === 0 ? (
          <p className="text-slate-400">Nothing logged this week.</p>
        ) : (
          <p className="text-slate-700">
            avg {nutrition.avgCalories} / {nutrition.targetCalories} kcal ({nutrition.diffFromTarget > 0 ? "+" : ""}
            {nutrition.diffFromTarget}) over {nutrition.daysLogged} day{nutrition.daysLogged !== 1 ? "s" : ""}
          </p>
        )}
      </Card>

      <Card title="Italian">
        <p className="text-slate-700">
          {italian.lessonsCompleted} lessons, {italian.xpEarned} XP, {italian.streak}-day streak
        </p>
      </Card>

      <Card title="Personal records">
        {personalRecords.length === 0 ? (
          <p className="text-slate-400">No new PRs this week.</p>
        ) : (
          <ul className="text-slate-700 text-sm">
            {personalRecords.map((p) => (
              <li key={`${p.exerciseName}-${p.repRange}`}>
                {p.exerciseName}: {p.weight} kg x {p.repRange} (e1RM {Math.round(p.e1rm)})
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
