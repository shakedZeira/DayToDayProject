import { useEffect, useRef, useState } from "react";
import {
  searchFoods,
  logMeal,
  deleteMeal,
  createFood,
  lookupBarcode,
  type MealLogRecord,
} from "./foodApi";
import type { FoodRecord } from "shared";
import { barcodeScanSupported, scanBarcode } from "./barcodeScan";

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

  const [barcode, setBarcode] = useState("");
  const [scanning, setScanning] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const emptyForm = { name: "", calories: "", protein: "", carbs: "", fat: "", barcode: "" };
  const [form, setForm] = useState(emptyForm);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => () => abortRef.current?.abort(), []);

  function fillFormFrom(food: { name: string; caloriesPer100: number; proteinPer100?: number; carbsPer100?: number; fatPer100?: number; barcode?: string | null }) {
    setForm({
      name: food.name,
      calories: String(food.caloriesPer100),
      protein: String(food.proteinPer100 ?? 0),
      carbs: String(food.carbsPer100 ?? 0),
      fat: String(food.fatPer100 ?? 0),
      barcode: food.barcode ?? "",
    });
    setShowForm(true);
  }

  async function onBarcode(code: string) {
    const c = code.trim();
    if (!c) return;
    try {
      const r = await lookupBarcode(token, c);
      setError(null);
      if (r.source === "mine" && r.foodId) {
        setResults([{ id: r.foodId, servingUnit: "g", proteinPer100: 0, carbsPer100: 0, fatPer100: 0, ...r.food, caloriesPer100: r.food.caloriesPer100, custom: true }]);
      } else {
        fillFormFrom(r.food);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Barcode lookup failed");
    }
  }

  async function onScan() {
    if (scanning) {
      abortRef.current?.abort();
      return;
    }
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setScanning(true);
    try {
      const code = await scanBarcode(videoRef.current!, ctrl.signal);
      if (code) {
        setBarcode(code);
        await onBarcode(code);
      }
    } catch {
      setError("Camera unavailable - type the barcode number instead.");
    } finally {
      setScanning(false);
    }
  }

  async function onCreateFood(e: React.FormEvent) {
    e.preventDefault();
    const n = (v: string) => (v.trim() === "" ? 0 : Number(v));
    try {
      const food = await createFood(token, {
        name: form.name.trim(),
        caloriesPer100: n(form.calories),
        proteinPer100: n(form.protein),
        carbsPer100: n(form.carbs),
        fatPer100: n(form.fat),
        barcode: form.barcode.trim() || null,
      });
      setResults([food]);
      setForm(emptyForm);
      setShowForm(false);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create food");
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
    <div className="w-full max-w-2xl flex flex-col gap-4 md-card p-4">
      <h2 className="md-title-medium text-md-onSurface">
        Nutrition
      </h2>

      <form onSubmit={onSearch} className="flex gap-2">
        <input aria-label="Search foods — e.g. chicken / עוף"
          className="border border-md-outline rounded-md3xs px-3 py-2 flex-1"
          placeholder="Search foods — e.g. chicken / עוף"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="bg-indigo-600 text-white rounded-full px-4 font-medium">
          Search
        </button>
      </form>

      <div className="flex gap-2 items-center">
        <input
          className="border border-md-outline rounded-md3xs px-3 py-2 flex-1"
          inputMode="numeric"
          aria-label="Barcode"
          placeholder="Barcode number"
          value={barcode}
          onChange={(e) => setBarcode(e.target.value.replace(/\D/g, ""))}
          onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); void onBarcode(barcode); } }}
        />
        <button type="button" onClick={() => onBarcode(barcode)} className="bg-indigo-600 text-white rounded-full px-4 py-2 font-medium">
          Look up
        </button>
        {barcodeScanSupported() && (
          <button type="button" onClick={onScan} className="border border-md-outline rounded-full px-4 py-2 font-medium">
            {scanning ? "Stop" : "Scan"}
          </button>
        )}
      </div>
      <video ref={videoRef} playsInline muted className={scanning ? "w-full rounded-md3xs" : "hidden"} />

      <div className="flex gap-2 items-center">
        <label className="text-sm text-slate-600">Grams</label>
        <input aria-label="Grams"
          className="border border-md-outline rounded-md3xs px-3 py-2 w-24"
          type="number"
          min={1}
          step="any"
          value={grams}
          onChange={(e) => setGrams(e.target.value)}
        />
      </div>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      <section className="flex flex-col gap-2">
        <button type="button" onClick={() => setShowForm((v) => !v)} className="self-start text-sm text-indigo-600 font-medium" aria-expanded={showForm}>
          {showForm ? "Hide custom food" : "+ Custom food"}
        </button>
        {showForm && (
          <form onSubmit={onCreateFood} className="grid grid-cols-2 gap-2">
            <input className="col-span-2 border border-md-outline rounded-md3xs px-3 py-2" placeholder="Name" aria-label="Food name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            {([["calories", "kcal / 100g"], ["protein", "Protein g / 100g"], ["carbs", "Carbs g / 100g"], ["fat", "Fat g / 100g"]] as const).map(([k, label]) => (
              <input key={k} className="border border-md-outline rounded-md3xs px-3 py-2" type="number" min={0} step="any" placeholder={label} aria-label={label} required={k === "calories"} value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })} />
            ))}
            <button className="col-span-2 bg-indigo-600 text-white rounded-full px-4 py-2 font-medium">Save food</button>
          </form>
        )}
      </section>

      {results.length > 0 && (
        <ul className="flex flex-col gap-2">
          {results.map((food) => (
            <li
              key={food.id}
              className="border border-md-outline rounded-md3xs px-3 py-2 bg-slate-50 flex items-center gap-2"
            >
              <span className="flex-1 min-w-0">
                <span className="block text-slate-800 font-medium truncate">
                  {food.name}
                  {food.nameHe ? <span className="font-normal text-slate-500"> · {food.nameHe}</span> : null}
                </span>
                <span className="block text-xs text-slate-500">
                  {food.caloriesPer100} kcal/100{food.servingUnit}
                  {` · P ${food.proteinPer100 ?? 0} C ${food.carbsPer100 ?? 0} F ${food.fatPer100 ?? 0}`}
                </span>
              </span>
              <button
                onClick={() => onLog(food)}
                className="bg-indigo-600 text-white rounded-full px-3 py-1 text-sm font-medium flex-shrink-0"
              >
                Log {grams}g
              </button>
            </li>
          ))}
        </ul>
      )}

      <section className="flex flex-col gap-2">
        <h3 className="md-title-medium text-md-onSurface">
          Today's meals
        </h3>
        {meals.length === 0 ? (
          <div className="text-center text-slate-500 py-4 text-sm">No meals logged yet.</div>
        ) : (
          <ul className="flex flex-col gap-2">
            {meals.map((meal) => (
              <li
                key={meal.id}
                className="border border-md-outline rounded-md3xs px-3 py-2 bg-slate-50 flex items-center gap-2"
              >
                <span className="flex-1 min-w-0 truncate text-sm text-slate-700">
                  {meal.name ?? meal.foodId}: {meal.grams}g
                </span>
                <button
                  aria-label={`Delete ${meal.name ?? meal.foodId}`}
                  onClick={() => onDelete(meal.id)}
                  className="text-slate-500 hover:text-red-600 flex-shrink-0"
                  title="Delete"
                >
                  <svg aria-hidden="true" className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
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