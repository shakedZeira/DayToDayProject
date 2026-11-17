import { useState } from "react";
import type { RecipeView } from "shared";
import { logRecipeMeal } from "./foodApi";
import CookingMode from "./CookingMode";

interface Props {
  token: string;
  recipe: RecipeView;
  onBack: () => void;
  onEdit: (recipe: RecipeView) => void;
  onDelete: (id: string) => void;
  onToggleFavorite: (recipe: RecipeView) => void;
  onLogged: () => void;
}

function macrosLabel(value: number, unit: string) {
  return `${Math.round(value * 10) / 10}${unit}`;
}

const MACRO_LABELS: Record<string, string> = {
  Calories: "קלוריות",
  Protein: "חלבון",
  Carbs: "פחמימות",
  Fat: "שומן",
};

export default function RecipeDetail({
  token,
  recipe,
  onBack,
  onEdit,
  onDelete,
  onToggleFavorite,
  onLogged,
}: Props) {
  const [logging, setLogging] = useState<string | null>(null);
  const [cooking, setCooking] = useState(false);

  async function handleLogAsMeal() {
    setLogging(recipe.id);
    try {
      await logRecipeMeal(token, recipe.id, 1);
      onLogged();
    } catch (err) {
      alert(err instanceof Error ? err.message : "נכשל ברישום הארוחה");
    } finally {
      setLogging(null);
    }
  }

  return (
    <div className="flex flex-col gap-4" dir="rtl">
      <div className="flex items-center justify-between gap-2">
        <button onClick={onBack} className="text-slate-600 font-medium hover:text-indigo-600">
          → חזרה למתכונים
        </button>
        <h1 className="text-2xl font-bold text-slate-800 flex-1">{recipe.title}</h1>
        <button
          onClick={() => onToggleFavorite(recipe)}
          className={`p-1.5 rounded-full transition-colors ${
            recipe.isFavorited ? "bg-yellow-100 text-yellow-600" : "text-slate-400 hover:bg-slate-100"
          }`}
          title={recipe.isFavorited ? "הסר ממועדפים" : "הוסף למועדפים"}
        >
          <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 5c0 1 1 2 2 2v3l3-3 3 3v-3c1 0 2-1 2-2 0-1-1-2-2-2s-2 1-2 2V7l-3-3-3 3V7c-1-1-2-1-2-2s-1-1-2-2" />
          </svg>
        </button>
      </div>

      <div className="aspect-video w-full rounded-lg overflow-hidden bg-slate-100 relative mb-2">
        {recipe.imageUrl ? (
          <img src={recipe.imageUrl} alt={recipe.title} className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-slate-400 text-xs">
            אין תמונה
          </div>
        )}
        <span className="absolute top-2 right-2 bg-indigo-600 text-white text-xs font-medium px-2 py-0.5 rounded capitalize">
          {recipe.meal}
        </span>
        <span className="absolute top-2 left-2 bg-white/90 text-slate-600 text-xs font-medium px-2 py-0.5 rounded backdrop-blur">
          {recipe.difficulty}
        </span>
      </div>

      {recipe.description && (
        <p className="text-slate-600 text-sm leading-relaxed">{recipe.description}</p>
      )}

      {/* Macros */}
      <div className="grid grid-cols-4 gap-2 border border-slate-200 rounded-lg p-2 mb-2">
        <div className="flex flex-col items-center">
          <span className="text-xs uppercase tracking-wide text-slate-400">קלוריות</span>
          <span className="font-semibold text-slate-800">{Math.round(recipe.calories)} קלוריות</span>
        </div>
        <div className="flex flex-col items-center">
          <span className="text-xs uppercase tracking-wide text-slate-400">חלבון</span>
          <span className="font-semibold text-slate-800">{macrosLabel(recipe.proteinG, "ג׳")}</span>
        </div>
        <div className="flex flex-col items-center">
          <span className="text-xs uppercase tracking-wide text-slate-400">פחמימות</span>
          <span className="font-semibold text-slate-800">{macrosLabel(recipe.carbsG, "ג׳")}</span>
        </div>
        <div className="flex flex-col items-center">
          <span className="text-xs uppercase tracking-wide text-slate-400">שומן</span>
          <span className="font-semibold text-slate-800">{macrosLabel(recipe.fatG, "ג׳")}</span>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-400">מנות</span>
          <span className="text-sm text-slate-600">{recipe.servings}</span>
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-400 ml-2">·</span>
          <span className="text-sm text-slate-600">
            הכנה {recipe.prepTimeMin} דק׳ · בישול {recipe.cookTimeMin} דק׳
          </span>
        </div>
      </div>

      {/* Ingredients checklist */}
      <section className="border border-slate-200 rounded-lg bg-white">
        <div className="flex items-center justify-between border-b border-slate-200 px-3 py-2">
          <h3 className="font-semibold text-slate-800 text-sm">רכיבים ({recipe.ingredients.length})</h3>
        </div>
        <ul className="divide-y divide-slate-100">
          {recipe.ingredients.length === 0 ? (
            <li className="px-3 py-3 text-center text-slate-400 text-sm">אין רכיבים רשומים.</li>
          ) : (
            recipe.ingredients.map((ing) => (
              <li key={`${ing.name}-${ing.unit}`} className="px-3 py-2 flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  className="w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                />
                <span className="flex-1 min-w-0 text-slate-700">{ing.name}</span>
                <span className="text-slate-500">{ing.amount} {ing.unit}</span>
              </li>
            ))
          )}
        </ul>
      </section>

      {/* Instructions */}
      <section className="border border-slate-200 rounded-lg bg-white">
        <div className="flex items-center justify-between border-b border-slate-200 px-3 py-2">
          <h3 className="font-semibold text-slate-800 text-sm">הוראות הכנה ({recipe.instructions.length})</h3>
          <button
            onClick={() => setCooking(true)}
            className="bg-indigo-600 text-white text-xs font-medium px-2.5 py-1 rounded hover:bg-indigo-700"
          >
            התחל לבשל
          </button>
        </div>
        <ul className="divide-y divide-slate-100">
          {recipe.instructions.length === 0 ? (
            <li className="px-3 py-3 text-center text-slate-400 text-sm">אין הוראות רשומות.</li>
          ) : (
            recipe.instructions
              .slice()
              .sort((a, b) => a.step - b.step)
              .map((step) => (
                <li key={step.step} className="px-3 py-2 flex items-start gap-2 text-sm">
                  <span className="mt-0.5 flex-shrink-0 text-xs font-bold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded">
                    שלב {step.step}
                  </span>
                  <p className="text-slate-700 leading-relaxed">{step.text}</p>
                  {step.timerSeconds && step.timerSeconds > 0 && (
                    <span className="ml-2 text-xs text-slate-500 self-start mt-0.5">
                      ⏱ {Math.floor(step.timerSeconds / 60)}:{String(step.timerSeconds % 60).padStart(2, "0")}
                    </span>
                  )}
                </li>
              ))
          )}
        </ul>
      </section>

      <div className="flex gap-2">
        <button
          onClick={handleLogAsMeal}
          disabled={logging === recipe.id}
          className="flex-1 bg-indigo-600 text-white font-medium py-2.5 rounded-lg hover:bg-indigo-700 disabled:opacity-60"
        >
          {logging === recipe.id ? "נרשם…" : "רישום כארוחה (מנה אחת)"}
        </button>
        <button
          onClick={() => onEdit(recipe)}
          className="bg-white border border-slate-200 text-slate-700 font-medium py-2.5 rounded-lg px-4 hover:bg-slate-50"
        >
          עריכה
        </button>
        <button
          onClick={() => onDelete(recipe.id)}
          className="text-red-600 font-medium py-2.5 rounded-lg px-4 hover:bg-red-50"
        >
          מחיקה
        </button>
      </div>

      {cooking && (
        <CookingMode
          token={token}
          recipe={recipe}
          onComplete={() => {
            setCooking(false);
            onLogged();
          }}
          onClose={() => setCooking(false)}
        />
      )}
    </div>
  );
}
