import type { NutritionSummary } from "shared";

interface Props {
  summary: NutritionSummary;
}

export function MacroBar({ label, grams, target, color }: { label: string; grams: number; target: number; color: string }) {
  const pct = Math.min(100, Math.round((grams / Math.max(target, 1)) * 100));
  return (
    <div className="flex flex-col gap-1" data-testid={`macro-${label.toLowerCase()}`}>
      <div className="flex justify-between text-xs text-slate-600">
        <span>{label}</span>
        <span>{Math.round(grams)} / {target} g</span>
      </div>
      <div
        className="h-2 w-full rounded-full bg-slate-200 overflow-hidden"
        role="progressbar"
        aria-label={`${label} intake`}
        aria-valuemin={0}
        aria-valuemax={target}
        aria-valuenow={Math.round(grams)}
      >
        <div className={`h-full ${color}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

export default function NutritionSummary({ summary }: Props) {
  const pct = Math.min(100, Math.round((summary.consumed / Math.max(summary.target, 1)) * 100));
  const over = summary.remaining < 0;

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4 md-card p-4">
      <h2 className="md-title-medium text-md-onSurface">
        Calorie Summary
      </h2>

      <div className="flex gap-6">
        <div className="flex flex-col">
          <span className="text-xs text-slate-400">Target</span>
          <span className="text-lg font-semibold text-slate-800">{summary.target} kcal</span>
        </div>
        <div className="flex flex-col">
          <span className="text-xs text-slate-400">Consumed</span>
          <span className="text-lg font-semibold text-slate-800">{summary.consumed} kcal</span>
        </div>
        <div className="flex flex-col">
          <span className="text-xs text-slate-400">Remaining</span>
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

      {summary.macros && summary.macroTargets && (
        <div className="flex flex-col gap-2">
          <MacroBar label="Protein" grams={summary.macros.proteinG} target={summary.macroTargets.proteinG} color="bg-emerald-500" />
          <MacroBar label="Carbs" grams={summary.macros.carbsG} target={summary.macroTargets.carbsG} color="bg-amber-500" />
          <MacroBar label="Fat" grams={summary.macros.fatG} target={summary.macroTargets.fatG} color="bg-sky-500" />
        </div>
      )}

      <ul className="flex flex-col gap-2">
        {summary.meals.length === 0 ? (
          <li className="text-sm text-slate-400">No meals logged for this day.</li>
        ) : (
          summary.meals.map((meal) => (
            <li
              key={meal.id}
              className="flex items-center justify-between border border-md-outline rounded-md3xs px-3 py-2 bg-slate-50 text-sm"
            >
              <span className="text-slate-700">
                {meal.foodName} · {meal.servings !== undefined ? `${meal.servings} serving${meal.servings === 1 ? "" : "s"}` : `${meal.grams}g`}
              </span>
              <span className="text-slate-400 text-right">
                {meal.calories} kcal
                {meal.proteinG !== undefined && (
                  <span className="block text-xs">P {meal.proteinG} · C {meal.carbsG} · F {meal.fatG}</span>
                )}
              </span>
            </li>
          ))
        )}
      </ul>
    </div>
  );
}