import { useState } from "react";
import {
  searchFoods,
  logMeal,
  deleteMeal,
  type MealLogRecord,
} from "./foodApi";
import type { FoodRecord } from "shared";

interface Props {
  token: string;
  meals: MealLogRecord[];
  onChanged: () => void;
}

export default function Foods({ token, meals, onChanged }: Props) {
  const [query, setQuery] = useState("");
  const [grams, setGrams] = useState("100");
  const [results, setResults] = useState<FoodRecord[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function onSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!query.trim()) return;
    try {
      setResults(await searchFoods(token, query.trim()));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to search foods");
    }
  }

  async function onLog(food: FoodRecord) {
    const g = Number(grams);
    if (!g || g <= 0) return;
    try {
      await logMeal(token, { foodId: food.id, grams: g });
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to log meal");
    }
  }

  async function onDelete(id: string) {
    try {
      await deleteMeal(token, id);
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete meal");
    }
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4 border rounded bg-white p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-indigo-600">
        Nutrition
      </h2>

      <form onSubmit={onSearch} className="flex gap-2">
        <input
          className="border rounded px-3 py-2 flex-1"
          placeholder="Search foods (e.g. chicken)"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="bg-indigo-600 text-white rounded px-4 font-medium">
          Search
        </button>
      </form>

      <div className="flex gap-2 items-center">
        <label className="text-sm text-slate-600">Grams</label>
        <input
          className="border rounded px-3 py-2 w-24"
          type="number"
          min={1}
          step="any"
          value={grams}
          onChange={(e) => setGrams(e.target.value)}
        />
      </div>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      {results.length > 0 && (
        <ul className="flex flex-col gap-2">
          {results.map((food) => (
            <li
              key={food.id}
              className="border rounded px-3 py-2 bg-slate-50 flex items-center gap-2"
            >
              <span className="flex-1 min-w-0">
                <span className="block text-slate-800 font-medium truncate">{food.name}</span>
                <span className="block text-xs text-slate-400">
                  {food.caloriesPer100} kcal/100{food.servingUnit}
                </span>
              </span>
              <button
                onClick={() => onLog(food)}
                className="bg-indigo-600 text-white rounded px-3 py-1 text-sm font-medium flex-shrink-0"
              >
                Log {grams}g
              </button>
            </li>
          ))}
        </ul>
      )}

      <section className="flex flex-col gap-2">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-indigo-600">
          Today's meals
        </h3>
        {meals.length === 0 ? (
          <div className="text-center text-slate-400 py-4 text-sm">No meals logged yet.</div>
        ) : (
          <ul className="flex flex-col gap-2">
            {meals.map((meal) => (
              <li
                key={meal.id}
                className="border rounded px-3 py-2 bg-slate-50 flex items-center gap-2"
              >
                <span className="flex-1 min-w-0 truncate text-sm text-slate-700">
                  {meal.name ?? meal.foodId}: {meal.grams}g
                </span>
                <button
                  onClick={() => onDelete(meal.id)}
                  className="text-slate-300 hover:text-red-500 flex-shrink-0"
                  title="Delete"
                >
                  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}