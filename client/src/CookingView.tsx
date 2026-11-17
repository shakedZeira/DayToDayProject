import { useCallback, useEffect, useState } from "react";
import type { RecipeMeal, RecipeView } from "shared";
import { fetchRecipes, fetchFavorites } from "./cookingApi";
import RecipeList from "./RecipeList";
import RecipeDetail from "./RecipeDetail";
import RecipeForm from "./RecipeForm";
import CookingMode from "./CookingMode";
import ShoppingList from "./ShoppingList";

type CookingTab = "all" | "breakfast" | "lunch" | "dinner" | "snack" | "favorites";

type View = "list" | "detail" | "form" | "shopping" | "cooking";

const TABS: { key: CookingTab; label: string }[] = [
  { key: "all", label: "הכל" },
  { key: "breakfast", label: "ארוחת בוקר" },
  { key: "lunch", label: "ארוחת צהריים" },
  { key: "dinner", label: "ארוחת ערב" },
  { key: "snack", label: "נשנוש" },
  { key: "favorites", label: "מועדפים" },
];

interface Props {
  token: string;
}

export default function CookingView({ token }: Props) {
  const [tab, setTab] = useState<CookingTab>("all");
  const [recipes, setRecipes] = useState<RecipeView[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [view, setView] = useState<View>("list");
  const [detailRecipe, setDetailRecipe] = useState<RecipeView | null>(null);
  const [formRecipe, setFormRecipe] = useState<RecipeView | null>(null);
  const [cookingRecipe, setCookingRecipe] = useState<RecipeView | null>(null);
  const [toast, setToast] = useState<{ message: string; type: "success" | "error" } | null>(null);

  const loadRecipes = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (tab === "favorites") {
        const favs = await fetchFavorites(token);
        setRecipes(favs);
      } else {
        const meal = tab === "all" ? undefined : (tab as RecipeMeal);
        const list = await fetchRecipes(token, meal ? { meal } : undefined);
        // fetchRecipes declares Recipe[]; RecipeView is the superset view used across the UI.
        setRecipes(list as unknown as RecipeView[]);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "נכשל בטעינת המתכונים");
      setRecipes([]);
    } finally {
      setLoading(false);
    }
  }, [token, tab]);

  useEffect(() => {
    loadRecipes();
  }, [loadRecipes]);

  const openDetail = (recipe: RecipeView) => {
    setDetailRecipe(recipe);
    setView("detail");
  };

  const openForm = (recipe?: RecipeView) => {
    setFormRecipe(recipe ?? null);
    setView("form");
  };

  const onSave = (saved: RecipeView) => {
    setRecipes((prev) => {
      const existing = prev.findIndex((r) => r.id === saved.id);
      if (existing >= 0) {
        const next = [...prev];
        next[existing] = saved;
        return next;
      }
      return [saved, ...prev];
    });
    if (detailRecipe?.id === saved.id) setDetailRecipe(saved);
    setView("list");
    setToast({ message: "המתכון נשמר", type: "success" });
  };

  const onDelete = (id: string) => {
    setRecipes((prev) => prev.filter((r) => r.id !== id));
    if (detailRecipe?.id === id) setView("list");
    setToast({ message: "המתכון נמחק", type: "success" });
  };

  const onToggleFavorite = (recipe: RecipeView, result: { isFavorited: boolean; favoriteCount: number }) => {
    setRecipes((prev) =>
      prev.map((r) =>
        r.id === recipe.id ? { ...r, isFavorited: result.isFavorited, favoriteCount: result.favoriteCount } : r
      )
    );
    if (detailRecipe?.id === recipe.id) {
      setDetailRecipe({ ...detailRecipe, isFavorited: result.isFavorited, favoriteCount: result.favoriteCount });
    }
  };

  const onLogged = () => {
    setToast({ message: "נרשמה מנה כארוחה", type: "success" });
    if (view === "detail") setView("list");
  };

  const dismissToast = () => setToast(null);

  if (view === "form") {
    return (
      <div className="flex flex-col h-full" dir="rtl">
        <div className="flex items-center justify-between bg-md-surface px-4 h-14">
          <button onClick={() => setView("list")} className="text-slate-600 font-medium hover:text-indigo-600">
            → חזרה
          </button>
          <h2 className="text-xl font-medium text-md-onSurface flex-1">
            {formRecipe ? "עריכת מתכון" : "מתכון חדש"}
          </h2>
        </div>
        <div className="flex-1 overflow-y-auto">
          <RecipeForm
            token={token}
            recipe={formRecipe ?? undefined}
            onSave={onSave}
            onCancel={() => setView("list")}
          />
        </div>
      </div>
    );
  }

  if (view === "detail" && detailRecipe) {
    return (
      <div className="flex flex-col h-full" dir="rtl">
        <div className="flex items-center justify-between bg-md-surface px-4 h-14">
          <button onClick={() => setView("list")} className="text-slate-600 font-medium hover:text-indigo-600">
            → חזרה למתכונים
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-4">
          <RecipeDetail
            token={token}
            recipe={detailRecipe}
            onBack={() => setView("list")}
            onEdit={(r) => {
              setFormRecipe(r);
              setView("form");
            }}
            onDelete={onDelete}
            onToggleFavorite={(r) => onToggleFavorite(r, { isFavorited: !r.isFavorited, favoriteCount: r.favoriteCount })}
            onLogged={() => {
              setView("list");
              setToast({ message: "נרשמה מנה כארוחה", type: "success" });
            }}
          />
        </div>
      </div>
    );
  }

  if (view === "shopping") {
    return (
      <div className="flex flex-col h-full" dir="rtl">
        <div className="flex items-center justify-between bg-md-surface px-4 h-14">
          <button onClick={() => setView("list")} className="text-slate-600 font-medium hover:text-indigo-600">
            → חזרה
          </button>
          <h2 className="text-xl font-medium text-md-onSurface flex-1">רשימת קניות</h2>
        </div>
        <div className="flex-1 overflow-y-auto p-4">
          <ShoppingList token={token} recipes={recipes} onClose={() => setView("list")} />
        </div>
      </div>
    );
  }

  if (view === "cooking" && cookingRecipe) {
    return (
      <div className="flex flex-col h-full" dir="rtl">
        <CookingMode
          token={token}
          recipe={cookingRecipe}
          onComplete={() => {
            setView("list");
            setToast({ message: "המתכון סומן כמוכן", type: "success" });
          }}
          onClose={() => setView("list")}
        />
      </div>
    );
  }

  // List view
  return (
    <div className="flex flex-col h-full" dir="rtl">
      <div className="flex items-center justify-between bg-md-surface px-4 h-14">
        <div className="text-right">
          <h2 className="text-xl font-medium text-md-onSurface">מתכונים</h2>
          <p className="text-xs text-slate-500">
            {recipes.length} מתכון{recipes.length !== 1 ? "ים" : ""}
          </p>
        </div>
        <div className="flex gap-1">
          <button
            onClick={() => setView("shopping")}
            className="text-sm font-medium text-md-primary border border-md-outline rounded-full px-3 py-1.5 hover:bg-md-primaryContainer/40"
          >
            רשימת קניות
          </button>
          <button
            onClick={() => openForm()}
            className="bg-indigo-600 text-white text-sm font-medium rounded-full px-3 py-1.5 hover:bg-indigo-700"
          >
            הוספת מתכון
          </button>
        </div>
      </div>

      {error && (
        <div className="mx-4 my-2 px-3 py-2 rounded bg-red-50 border border-red-100 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="flex gap-2 px-4 pt-4 overflow-x-auto">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-3 h-8 text-xs font-medium shrink-0 rounded-full transition-colors ${
              tab === t.key
                ? "bg-md-primary text-white"
                : "text-md-primary hover:bg-md-primaryContainer/50"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto p-4">
        {loading && recipes.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-md-onSurfaceVariant md-body-medium">
            טוען מתכונים…
          </div>
        ) : (
          <RecipeList
            token={token}
            recipes={recipes}
            onSelect={openDetail}
            onToggleFavorite={onToggleFavorite}
            onDelete={onDelete}
            onLogAsMeal={(recipe) => {
              // Handled inside RecipeDetail too; here from the card we open detail then log.
              openDetail(recipe);
            }}
          />
        )}
      </div>

      <div className="fixed bottom-24 md:bottom-6 left-6 z-10">
        <button
          onClick={() => openForm()}
          className="bg-indigo-600 text-white rounded-full p-3 shadow-lg hover:bg-indigo-700 focus:outline-none"
          aria-label="הוספת מתכון"
        >
          <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
        </button>
      </div>

      {toast && (
        <div
          className={`fixed bottom-24 md:bottom-6 right-6 z-10 flex items-center gap-2 px-3 py-2 rounded-md3m shadow-lg ${
            toast.type === "success" ? "bg-green-900 text-white" : "bg-red-900 text-white"
          }`}
        >
          <span className="text-sm">{toast.message}</span>
          <button onClick={dismissToast} className="ml-2 text-xs opacity-70 hover:opacity-100">✕</button>
        </div>
      )}
    </div>
  );
}
