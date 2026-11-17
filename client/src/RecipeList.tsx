import { useState } from "react";
import type { RecipeView } from "shared";
import { deleteRecipe, toggleFavorite } from "./cookingApi";

interface Props {
  token: string;
  recipes: RecipeView[];
  onSelect: (recipe: RecipeView) => void;
  onToggleFavorite: (recipe: RecipeView, result: { isFavorited: boolean; favoriteCount: number }) => void;
  onDelete: (id: string) => void;
  onLogAsMeal: (recipe: RecipeView) => void;
}

export default function RecipeList({
  token,
  recipes,
  onSelect,
  onToggleFavorite,
  onDelete,
  onLogAsMeal,
}: Props) {
  const [deleting, setDeleting] = useState<string | null>(null);

  async function handleDelete(id: string, e: React.MouseEvent) {
    e.stopPropagation();
    if (!window.confirm("למחוק את המתכון הזה?")) return;
    setDeleting(id);
    try {
      await deleteRecipe(token, id);
      onDelete(id);
    } catch (err) {
      setDeleting(null);
      alert(err instanceof Error ? err.message : "נכשל במחיקת המתכון");
    } finally {
      if (deleting === id) setDeleting(null);
    }
  }

  async function handleToggle(recipe: RecipeView, e: React.MouseEvent) {
    e.stopPropagation();
    try {
      const result = await toggleFavorite(token, recipe.id);
      onToggleFavorite(recipe, result);
    } catch (err) {
      alert(err instanceof Error ? err.message : "נכשל בעדכון המועדף");
    }
  }

  if (recipes.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-slate-400 text-sm" dir="rtl">
        לא נמצאו מתכונים.
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3" dir="rtl">
      {recipes.map((recipe) => (
        <div
          key={recipe.id}
          onClick={() => onSelect(recipe)}
          className="border border-slate-200 rounded-md3m bg-white p-3 hover:shadow-md cursor-pointer transition-all"
        >
          <div className="flex flex-col gap-2">
            <div className="aspect-video w-full rounded overflow-hidden bg-slate-100 relative">
              {recipe.imageUrl ? (
                <img src={recipe.imageUrl} alt={recipe.title} className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-slate-400 text-xs">
                  אין תמונה
                </div>
              )}
              <span
                className="absolute top-1 right-1 bg-indigo-600 text-white text-[10px] font-medium px-2 py-0.5 rounded-full capitalize"
              >
                {recipe.meal}
              </span>
            </div>

            <div className="flex items-baseline gap-2">
              <h3 className="font-semibold text-slate-800 text-sm leading-tight truncate" title={recipe.title}>
                {recipe.title}
              </h3>
              <button
                onClick={(e) => handleToggle(recipe, e)}
                className={`p-1 rounded-full transition-colors ${
                  recipe.isFavorited ? "bg-yellow-100 text-yellow-600" : "text-slate-400 hover:bg-slate-100"
                }`}
                title={recipe.isFavorited ? "הסר ממועדפים" : "הוסף למועדפים"}
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 5c0 1 1 2 2 2v3l3-3 3 3v-3c1 0 2-1 2-2 0-1-1-2-2-2s-2 1-2 2V7l-3-3-3 3V7c-1-1-2-1-2-2s-1-1-2-2" />
                </svg>
              </button>
            </div>

            {recipe.description && (
              <p className="text-xs text-slate-500 line-clamp-1">{recipe.description}</p>
            )}

            <div className="mt-2 flex items-center justify-between gap-2">
              <div className="flex gap-1.5 text-xs text-slate-500">
                <span className="flex items-center gap-0.5">
                  <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  {Math.round(recipe.calories)} קלוריות
                </span>
                <span className="mr-1">{recipe.proteinG}ג חלבון</span>
                <span className="mr-1">{recipe.carbsG}ג פחמימות</span>
                <span>{recipe.fatG}ג שומן</span>
              </div>
              <div className="flex gap-1">
                <button
                  onClick={(e) => onLogAsMeal(recipe)}
                  className="bg-indigo-600 text-white text-xs rounded-full px-2.5 py-1 font-medium hover:bg-indigo-700 disabled:opacity-50"
                  disabled={deleting === recipe.id}
                >
                  רישום כארוחה
                </button>
                <button
                  onClick={(e) => handleDelete(recipe.id, e)}
                  className="text-xs text-slate-500 hover:text-red-600 font-medium"
                  disabled={deleting === recipe.id}
                >
                  מחיקה
                </button>
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
