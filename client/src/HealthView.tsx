import { useCallback, useEffect, useState } from "react";
import Workouts from "./Workouts";
import Plans from "./Plans";
import Analytics from "./Analytics";
import Goals from "./Goals";
import Foods from "./Foods";
import type { MealLogRecord } from "./foodApi";
import NutritionSummary from "./NutritionSummary";
import PhotoCalories from "./PhotoCalories";
import { getSummary } from "./nutritionApi";
import type { NutritionSummary as NutritionSummaryData } from "shared";

interface Props {
  token: string;
}

type HealthTab = "nutrition" | "workouts" | "goals";

const TABS: { key: HealthTab; label: string }[] = [
  { key: "nutrition", label: "Nutrition" },
  { key: "workouts", label: "Workouts" },
  { key: "goals", label: "Goals" },
];

export default function HealthView({ token }: Props) {
  const [tab, setTab] = useState<HealthTab>("nutrition");
  const [summary, setSummary] = useState<NutritionSummaryData | null>(null);
  const [workoutSubTab, setWorkoutSubTab] = useState<"plans" | "history" | "progress">("plans");
  const [analyticsRefreshKey, setAnalyticsRefreshKey] = useState(0);

  const refreshNutrition = useCallback(async () => {
    try {
      setSummary(await getSummary(token));
    } catch {
      setSummary(null);
    }
  }, [token]);

  useEffect(() => { refreshNutrition(); }, [refreshNutrition]);

  const summaryMeals: MealLogRecord[] = summary
    ? summary.meals.map((m) => ({
        id: m.id,
        foodId: m.id,
        grams: m.grams,
        date: summary.date,
        name: m.foodName,
      }))
    : [];

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-2xl font-bold text-slate-800">Health</h2>
      <div className="flex gap-1 border-b border-slate-200 pb-px">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-3 py-2 text-sm font-medium rounded-t transition-colors ${
              tab === t.key
                ? "bg-indigo-600 text-white"
                : "text-slate-600 hover:bg-slate-200"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "nutrition" && (
        <div className="flex flex-col gap-4">
          <Foods token={token} meals={summaryMeals} onChanged={refreshNutrition} />
          {summary && <NutritionSummary summary={summary} />}
          <PhotoCalories token={token} />
        </div>
      )}

      {tab === "workouts" && (
        <div className="flex flex-col gap-3">
          <div className="flex gap-1 border-b border-slate-200 pb-px">
            {(["plans", "history", "progress"] as const).map((st) => (
              <button
                key={st}
                onClick={() => {
                  setWorkoutSubTab(st);
                  if (st === "progress") setAnalyticsRefreshKey((k) => k + 1);
                }}
                className={`px-3 py-1.5 text-xs font-medium rounded transition-colors ${
                  workoutSubTab === st
                    ? "bg-indigo-600 text-white"
                    : "text-slate-500 hover:bg-slate-200"
                }`}
              >
                {st === "plans" ? "Plans" : st === "history" ? "History" : "Progress"}
              </button>
            ))}
          </div>
          {workoutSubTab === "plans" && <Plans token={token} />}
          {workoutSubTab === "history" && <Workouts token={token} />}
          {workoutSubTab === "progress" && <Analytics token={token} refreshKey={analyticsRefreshKey} />}
        </div>
      )}

      {tab === "goals" && <Goals token={token} />}
    </div>
  );
}
