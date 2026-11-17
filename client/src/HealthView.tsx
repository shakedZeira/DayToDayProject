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
  /** Which section the bottom navigation selected. */
  tab?: HealthTab;
}

export type HealthTab = "nutrition" | "workouts" | "goals";

export default function HealthView({ token, tab = "nutrition" }: Props) {
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
      {tab === "nutrition" && (
        <div className="flex flex-col gap-4">
          <Foods token={token} meals={summaryMeals} onChanged={refreshNutrition} />
          {summary && <NutritionSummary summary={summary} />}
          <PhotoCalories token={token} />
        </div>
      )}

      {tab === "workouts" && (
        <div className="flex flex-col gap-3">
          <div className="flex border-b border-md-outlineVariant">
            {(["plans", "history", "progress"] as const).map((st) => (
              <button
                key={st}
                onClick={() => {
                  setWorkoutSubTab(st);
                  if (st === "progress") setAnalyticsRefreshKey((k) => k + 1);
                }}
                className={`flex-1 px-3 h-12 text-sm font-medium border-b-2 transition-colors ${
                  workoutSubTab === st
                    ? "border-md-primary text-indigo-700"
                    : "border-transparent text-md-onSurfaceVariant hover:bg-md-surfaceVariant"
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
