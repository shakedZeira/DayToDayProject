import type { NutritionSummary } from "shared";

interface Props {
  summary: NutritionSummary;
}

export default function NutritionSummary({ summary }: Props) {
  const pct = Math.min(100, Math.round((summary.consumed / Math.max(summary.target, 1)) * 100));
  const over = summary.remaining < 0;

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4 border rounded bg-white p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-indigo-600">
        Calorie Summary
      </h2>

      <div className="flex gap-6">
        <div className="flex flex-col">
          <span className="text-xs uppercase tracking-wide text-slate-400">Target</span>
          <span className="text-lg font-semibold text-slate-800">{summary.target} kcal</span>
        </div>
        <div className="flex flex-col">
          <span className="text-xs uppercase tracking-wide text-slate-400">Consumed</span>
          <span className="text-lg font-semibold text-slate-800">{summary.consumed} kcal</span>
        </div>
        <div className="flex flex-col">
          <span className="text-xs uppercase tracking-wide text-slate-400">Remaining</span>
          <span className={`text-lg font-semibold ${over ? "text-red-600" : "text-slate-800"}`}>
            {summary.remaining} kcal
          </span>
        </div>
      </div>

      <div className="flex flex-col gap-1">
        <div className="h-2 w-full rounded-full bg-slate-200 overflow-hidden">
          <div
            className={`h-full ${over ? "bg-red-600" : "bg-indigo-600"}`}
            style={{ width: `${pct}%` }}
          />
        </div>
        <p className="text-xs text-slate-400">
          {pct}% of daily target
        </p>
      </div>

      <ul className="flex flex-col gap-2">
        {summary.meals.length === 0 ? (
          <li className="text-sm text-slate-400">No meals logged for this day.</li>
        ) : (
          summary.meals.map((meal) => (
            <li
              key={meal.id}
              className="flex items-center justify-between border rounded px-3 py-2 bg-slate-50 text-sm"
            >
              <span className="text-slate-700">
                {meal.foodName} · {meal.grams}g
              </span>
              <span className="text-slate-400">
                {meal.calories} kcal
              </span>
            </li>
          ))
        )}
      </ul>
    </div>
  );
}