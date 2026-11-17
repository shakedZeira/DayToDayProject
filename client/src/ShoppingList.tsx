import { useMemo, useState } from "react";
import type { RecipeView } from "shared";

interface Props {
  token: string;
  recipes: RecipeView[];
  onClose: () => void;
}

type IngKey = string;

function keyOf(name: string, unit: string): IngKey {
  return `${name.trim().toLowerCase()}|${unit.trim()}`;
}

export default function ShoppingList({ token, recipes, onClose }: Props) {
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [purchased, setPurchased] = useState<Set<IngKey>>(new Set());

  const toggleRecipe = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    setSelected((prev) => {
      if (prev.size === recipes.length) return new Set();
      return new Set(recipes.map((r) => r.id));
    });
  };

  const togglePurchased = (key: IngKey) => {
    setPurchased((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const resetPurchased = () => setPurchased(new Set());

  const aggregated = useMemo(() => {
    const map = new Map<IngKey, { name: string; unit: string; amount: number; recipeIds: string[] }>();
    for (const id of selected) {
      const recipe = recipes.find((r) => r.id === id);
      if (!recipe) continue;
      for (const ing of recipe.ingredients) {
        const key = keyOf(ing.name, ing.unit);
        const existing = map.get(key);
        if (existing) {
          existing.amount += ing.amount;
          existing.recipeIds.push(id);
        } else {
          map.set(key, { name: ing.name, unit: ing.unit, amount: ing.amount, recipeIds: [id] });
        }
      }
    }
    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name));
  }, [recipes, selected]);

  const isSelected = (id: string) => selected.has(id);
  const isPurchased = (key: IngKey) => purchased.has(key);

  return (
    <div className="border border-slate-200 rounded-xl bg-white p-4 max-w-2xl" dir="rtl">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-bold text-slate-800">רשימת קניות</h2>
        <button onClick={onClose} className="text-slate-500 hover:text-slate-700 font-medium">
          סגירה
        </button>
      </div>

      {/* Recipe selection */}
      <section className="mb-4 border border-slate-200 rounded-lg p-3">
        <div className="flex items-center justify-between mb-2">
          <label className="text-xs font-semibold uppercase tracking-wide text-slate-400">בחירת מתכונים</label>
          <div className="flex gap-1">
            <button
              type="button"
              onClick={toggleSelectAll}
              className="text-xs text-slate-600 hover:text-slate-900 font-medium underline px-2"
            >
              {selected.size === recipes.length && recipes.length > 0 ? "ניקוי הכל" : "בחירת הכל"}
            </button>
            <button
              type="button"
              onClick={resetPurchased}
              className="text-xs text-slate-600 hover:text-slate-900 font-medium underline px-2"
            >
              איפוס רכישות
            </button>
          </div>
        </div>
        <div className="flex flex-col gap-1">
          {recipes.length === 0 ? (
            <span className="text-xs text-slate-400">אין מתכונים זמינים.</span>
          ) : (
            recipes.map((recipe) => (
              <label
                key={recipe.id}
                className="flex items-center gap-2 cursor-pointer select-none"
                onClick={(e) => {
                  if ((e.target as HTMLElement).closest("input")) return;
                  toggleRecipe(recipe.id);
                }}
              >
                <input
                  type="checkbox"
                  checked={isSelected(recipe.id)}
                  onChange={(e) => toggleRecipe(recipe.id)}
                  className="w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                />
                <span className="flex-1 min-w-0 text-sm text-slate-700 truncate">{recipe.title}</span>
                <span className="text-xs text-slate-400 capitalize">{recipe.meal}</span>
              </label>
            ))
          )}
        </div>
        <p className="mt-1 text-xs text-slate-500">
          נבחרו {selected.size} מתכון{selected.size !== 1 ? "ים" : ""}
        </p>
      </section>

      {/* Aggregated shopping list */}
      <section className="border border-slate-200 rounded-lg">
        <div className="flex items-center justify-between border-b border-slate-200 px-3 py-2">
          <h3 className="font-semibold text-slate-800 text-sm">
            רכיבים לקנייה ({aggregated.length})
          </h3>
          {aggregated.length > 0 && purchased.size > 0 && (
            <span className="text-xs text-green-600 font-medium">{purchased.size} נקנה</span>
          )}
        </div>
        <div className="divide-y divide-slate-100">
          {aggregated.length === 0 ? (
            <div className="px-3 py-6 text-center text-slate-400 text-sm" dir="rtl">
              בחר מתכונים למעלה כדי לבנות את רשימת הקניות שלך.
            </div>
          ) : (
            aggregated.map((item) => {
              const key = keyOf(item.name, item.unit);
              const done = isPurchased(key);
              return (
                <label
                  key={key}
                  className={`px-3 py-2 flex items-center gap-2 cursor-pointer select-none ${
                    done ? "bg-green-50" : ""
                  }`}
                  onClick={(e) => {
                    if ((e.target as HTMLElement).closest("input")) return;
                    togglePurchased(key);
                  }}
                >
                  <input
                    type="checkbox"
                    checked={done}
                    onChange={() => togglePurchased(key)}
                    className="w-4 h-4 rounded border-slate-300 text-green-600 focus:ring-green-500"
                  />
                  <div className="flex-1 min-w-0">
                    <span
                      className={`block text-sm ${done ? "line-through text-slate-400" : "text-slate-700"}`}
                    >
                      {item.name}
                    </span>
                    <span className="text-xs text-slate-500">
                      {item.amount} {item.unit}
                    </span>
                  </div>
                  <span className="text-xs text-slate-400 italic">
                    {item.recipeIds.length} מתכון{item.recipeIds.length !== 1 ? "ים" : ""}
                  </span>
                </label>
              );
            })
          )}
        </div>
      </section>
    </div>
  );
}
