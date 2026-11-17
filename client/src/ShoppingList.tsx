import { useCallback, useEffect, useState } from "react";
import type { RecipeView, ShoppingItemView } from "shared";
import {
  addRecipeToShoppingList,
  addShoppingItem,
  clearCheckedShoppingItems,
  deleteShoppingItem,
  fetchShoppingList,
  updateShoppingItem,
} from "./shoppingApi";

interface Props {
  token: string;
  recipes: RecipeView[];
  onClose: () => void;
}

export default function ShoppingList({ token, recipes, onClose }: Props) {
  const [items, setItems] = useState<ShoppingItemView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [recipeId, setRecipeId] = useState("");
  const [servings, setServings] = useState("");
  const [skipPantry, setSkipPantry] = useState(true);
  const [newName, setNewName] = useState("");

  const run = useCallback(async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof Error ? e.message : "request failed");
    }
  }, []);

  const reload = useCallback(
    () => run(async () => setItems(await fetchShoppingList(token))),
    [run, token]
  );

  useEffect(() => {
    reload().finally(() => setLoading(false));
  }, [reload]);

  const toggle = (item: ShoppingItemView) =>
    run(async () => {
      const updated = await updateShoppingItem(token, item.id, { checked: !item.checked });
      setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
    });

  const remove = (item: ShoppingItemView) =>
    run(async () => {
      await deleteShoppingItem(token, item.id);
      setItems((prev) => prev.filter((i) => i.id !== item.id));
    });

  const clearChecked = () =>
    run(async () => {
      await clearCheckedShoppingItems(token);
      setItems((prev) => prev.filter((i) => !i.checked));
    });

  const addRecipe = () =>
    run(async () => {
      if (!recipeId) return;
      const n = servings ? Number(servings) : undefined;
      setItems(await addRecipeToShoppingList(token, recipeId, { servings: n, skipPantry }));
      setRecipeId("");
      setServings("");
    });

  const addManual = () =>
    run(async () => {
      if (!newName.trim()) return;
      await addShoppingItem(token, { name: newName.trim() });
      setNewName("");
      await reload();
    });

  const checkedCount = items.filter((i) => i.checked).length;

  return (
    <div className="border border-slate-200 rounded-xl bg-white p-4 max-w-2xl" dir="rtl">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-bold text-slate-800">רשימת קניות</h2>
        <button onClick={onClose} className="text-slate-500 hover:text-slate-700 font-medium">
          סגירה
        </button>
      </div>

      {error && <p className="mb-2 text-sm text-red-600">{error}</p>}

      {/* Add a recipe's ingredients */}
      <section className="mb-4 border border-slate-200 rounded-lg p-3">
        <label className="text-xs font-semibold uppercase tracking-wide text-slate-400">הוספת מתכון לרשימה</label>
        <div className="flex gap-2 mt-2">
          <select
            value={recipeId}
            onChange={(e) => setRecipeId(e.target.value)}
            className="flex-1 min-w-0 border border-slate-300 rounded px-2 py-1 text-sm"
            aria-label="מתכון"
          >
            <option value="">בחירת מתכון</option>
            {recipes.map((r) => (
              <option key={r.id} value={r.id}>
                {r.title}
              </option>
            ))}
          </select>
          <input
            type="number"
            min={1}
            max={100}
            value={servings}
            onChange={(e) => setServings(e.target.value)}
            placeholder="מנות"
            aria-label="מספר מנות"
            className="w-20 border border-slate-300 rounded px-2 py-1 text-sm"
          />
          <button
            type="button"
            onClick={addRecipe}
            disabled={!recipeId}
            className="px-3 py-1 text-sm rounded bg-indigo-600 text-white disabled:opacity-40"
          >
            הוספה
          </button>
        </div>
        <label className="mt-2 flex items-center gap-2 text-xs text-slate-600">
          <input type="checkbox" checked={skipPantry} onChange={(e) => setSkipPantry(e.target.checked)} />
          דלג על מה שיש במזווה
        </label>
      </section>

      {/* Persistent list */}
      <section className="border border-slate-200 rounded-lg">
        <div className="flex items-center justify-between border-b border-slate-200 px-3 py-2">
          <h3 className="font-semibold text-slate-800 text-sm">פריטים ({items.length})</h3>
          {checkedCount > 0 && (
            <button type="button" onClick={clearChecked} className="text-xs text-slate-600 hover:text-slate-900 underline">
              ניקוי {checkedCount} שנקנו
            </button>
          )}
        </div>
        <div className="flex gap-2 px-3 py-2 border-b border-slate-100">
          <input
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && addManual()}
            placeholder="הוספת פריט"
            aria-label="פריט חדש"
            className="flex-1 min-w-0 border border-slate-300 rounded px-2 py-1 text-sm"
          />
          <button type="button" onClick={addManual} className="px-3 py-1 text-sm rounded border border-slate-300">
            +
          </button>
        </div>
        <div className="divide-y divide-slate-100">
          {loading ? (
            <div className="px-3 py-6 text-center text-slate-400 text-sm">טוען...</div>
          ) : items.length === 0 ? (
            <div className="px-3 py-6 text-center text-slate-400 text-sm">הרשימה ריקה. הוסף מתכון או פריט.</div>
          ) : (
            items.map((item) => (
              <div key={item.id} className={`px-3 py-2 flex items-center gap-2 ${item.checked ? "bg-green-50" : ""}`}>
                <input
                  type="checkbox"
                  checked={item.checked}
                  onChange={() => toggle(item)}
                  aria-label={item.name}
                  className="w-4 h-4 rounded border-slate-300 text-green-600 focus:ring-green-500"
                />
                <div className="flex-1 min-w-0">
                  <span className={`block text-sm ${item.checked ? "line-through text-slate-400" : "text-slate-700"}`}>
                    {item.name}
                  </span>
                  <span className="text-xs text-slate-500">
                    {item.amount} {item.unit}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => remove(item)}
                  aria-label="מחיקה"
                  className="text-slate-400 hover:text-red-600 text-sm px-1"
                >
                  ✕
                </button>
              </div>
            ))
          )}
        </div>
      </section>
    </div>
  );
}
