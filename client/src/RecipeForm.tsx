import { useCallback, useState } from "react";
import type { RecipeCreateInput, RecipeDifficulty, RecipeMeal, RecipeView } from "shared";
import { createRecipe, updateRecipe, uploadRecipePhoto } from "./cookingApi";

interface Props {
  token: string;
  recipe?: RecipeView;
  onSave: (recipe: RecipeView) => void;
  onCancel: () => void;
}

const EMPTY_INGREDIENT = { name: "", amount: 1, unit: "" };
const EMPTY_INSTRUCTION = { step: 1, text: "", timerSeconds: null as number | null };

function toTagsString(tags: string[] | null | undefined): string {
  return (tags ?? []).join(", ");
}

function NumField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (n: number) => void;
}) {
  return (
    <div dir="rtl" lang="he">
      <label className="block text-xs font-semibold text-slate-500 mb-1">
        {label}
      </label>
      <input aria-label={label}
        className="border border-md-outline rounded-md3xs px-3 py-2 w-full"
        type="number"
        min="0"
        step="any"
        value={value}
        onChange={(e) => {
          const n = Number(e.target.value);
          onChange(isFinite(n) ? n : 0);
        }}
      />
    </div>
  );
}

export default function RecipeForm({ token, recipe, onSave, onCancel }: Props) {
  const isEdit = Boolean(recipe);
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [form, setForm] = useState<{
    title: string;
    description: string;
    meal: RecipeMeal;
    difficulty: RecipeDifficulty;
    prepTimeMin: number;
    cookTimeMin: number;
    servings: number;
    calories: number;
    proteinG: number;
    carbsG: number;
    fatG: number;
    source: string;
    tags: string;
  }>({
    title: recipe?.title ?? "",
    description: recipe?.description ?? "",
    meal: recipe?.meal ?? "breakfast",
    difficulty: recipe?.difficulty ?? "beginner",
    prepTimeMin: recipe?.prepTimeMin ?? 0,
    cookTimeMin: recipe?.cookTimeMin ?? 0,
    servings: recipe?.servings ?? 1,
    calories: recipe?.calories ?? 0,
    proteinG: recipe?.proteinG ?? 0,
    carbsG: recipe?.carbsG ?? 0,
    fatG: recipe?.fatG ?? 0,
    source: recipe?.source ?? "",
    tags: toTagsString(recipe?.tags),
  });

  const [ingredients, setIngredients] = useState<{ name: string; amount: number; unit: string }[]>(
    recipe?.ingredients.length ? [...recipe.ingredients] : [EMPTY_INGREDIENT]
  );

  const [instructions, setInstructions] = useState<{ step: number; text: string; timerSeconds: number | null }[]>(
    recipe?.instructions.length
      ? [...recipe.instructions].map((i, idx) => ({ ...i, step: idx + 1, timerSeconds: i.timerSeconds ?? null }))
      : [EMPTY_INSTRUCTION]
  );

  const [imageUrl, setImageUrl] = useState<string | null>(recipe?.imageUrl ?? null);

  const setField = useCallback(
    <K extends keyof typeof form>(key: K, value: typeof form[K]) =>
      setForm((prev) => ({ ...prev, [key]: value })),
    []
  );

  const addIngredient = () =>
    setIngredients((prev) => [...prev, { ...EMPTY_INGREDIENT, name: "" }]);

  const removeIngredient = (index: number) =>
    setIngredients((prev) => prev.filter((_, i) => i !== index));

  const updateIngredient = (index: number, field: "name" | "amount" | "unit", value: string | number) =>
    setIngredients((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });

  const addInstruction = () =>
    setInstructions((prev) => [...prev, { step: prev.length + 1, text: "", timerSeconds: null }]);

  const removeInstruction = (index: number) =>
    setInstructions((prev) => prev.filter((_, i) => i !== index));

  const updateInstruction = (
    index: number,
    field: "text" | "timerSeconds",
    value: string | number | null
  ) =>
    setInstructions((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });

  const handleImageChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const result = await uploadRecipePhoto(token, file);
      setImageUrl(result.imageUrl);
      e.target.value = "";
    } catch (err) {
      setError(err instanceof Error ? err.message : "נכשל בהעלאת התמונה");
    } finally {
      setUploading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const input: RecipeCreateInput = {
        title: form.title.trim(),
        description: form.description.trim() || null,
        meal: form.meal,
        difficulty: form.difficulty,
        prepTimeMin: form.prepTimeMin,
        cookTimeMin: form.cookTimeMin,
        servings: form.servings,
        calories: form.calories,
        proteinG: form.proteinG,
        carbsG: form.carbsG,
        fatG: form.fatG,
        ingredients: ingredients.map((ing) => ({
          name: ing.name.trim(),
          amount: ing.amount,
          unit: ing.unit.trim(),
        })),
        instructions: instructions.map((inst, idx) => ({
          step: idx + 1,
          text: inst.text.trim(),
          timerSeconds: inst.timerSeconds ?? null,
        })),
        imageUrl: imageUrl ?? null,
        source: form.source.trim() || null,
        tags: form.tags.trim() || null,
      };

      const result = isEdit && recipe
        ? await updateRecipe(token, recipe.id, input)
        : await createRecipe(token, input);
      onSave(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "נכשל בשמירת המתכון");
    } finally {
      setSubmitting(false);
    }
  };

  if (submitting) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-slate-500 text-sm" dir="rtl" lang="he">
        שומר…
      </div>
    );
  }

  const MEAL_OPTIONS: { value: RecipeMeal; label: string }[] = [
    { value: "breakfast", label: "ארוחת בוקר" },
    { value: "lunch", label: "ארוחת צהריים" },
    { value: "dinner", label: "ארוחת ערב" },
    { value: "snack", label: "נשנוש" },
  ];

  const DIFFICULTY_OPTIONS: { value: RecipeDifficulty; label: string }[] = [
    { value: "beginner", label: "מתחיל" },
    { value: "intermediate", label: "בינוני" },
    { value: "advanced", label: "מתקדם" },
  ];

  return (
    <div className="border border-slate-200 md-card p-4 max-w-xl" dir="rtl" lang="he">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-bold text-slate-800">{isEdit ? "עריכת מתכון" : "מתכון חדש"}</h2>
        <button
          onClick={onCancel}
          className="text-slate-500 hover:text-slate-700 font-medium"
        >
          ביטול
        </button>
      </div>

      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">
            כותרת
          </label>
          <input aria-label="למשל: פנקייק חלבון"
            className="border border-md-outline rounded-md3xs px-3 py-2 w-full"
            value={form.title}
            onChange={(e) => setField("title", e.target.value)}
            placeholder="למשל: פנקייק חלבון"
            required
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold text-slate-500 mb-1">
              ארוחה
            </label>
            <select aria-label="ארוחה"
              className="border border-md-outline rounded-md3xs px-3 py-2 w-full"
              value={form.meal}
              onChange={(e) => setField("meal", e.target.value as RecipeMeal)}
            >
              {MEAL_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-500 mb-1">
              רמה
            </label>
            <select aria-label="רמת קושי"
              className="border border-md-outline rounded-md3xs px-3 py-2 w-full"
              value={form.difficulty}
              onChange={(e) => setField("difficulty", e.target.value as RecipeDifficulty)}
            >
              {DIFFICULTY_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-4 gap-3">
          <NumField label="הכנה (דק׳)" value={form.prepTimeMin} onChange={(v) => setField("prepTimeMin", v)} />
          <NumField label="בישול (דק׳)" value={form.cookTimeMin} onChange={(v) => setField("cookTimeMin", v)} />
          <NumField label="מנות" value={form.servings} onChange={(v) => setField("servings", v)} />
          <NumField label="קלוריות" value={form.calories} onChange={(v) => setField("calories", v)} />
        </div>

        <div className="grid grid-cols-4 gap-3">
          <NumField label="חלבון (ג׳)" value={form.proteinG} onChange={(v) => setField("proteinG", v)} />
          <NumField label="פחמימות (ג׳)" value={form.carbsG} onChange={(v) => setField("carbsG", v)} />
          <NumField label="שומן (ג׳)" value={form.fatG} onChange={(v) => setField("fatG", v)} />
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">
            תיאור
          </label>
          <textarea aria-label="תיאור קצר"
            className="border border-md-outline rounded-md3xs px-3 py-2 w-full"
            value={form.description}
            onChange={(e) => setField("description", e.target.value)}
            rows={2}
            placeholder="תיאור קצר…"
          />
        </div>

        {/* Ingredients */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="text-xs font-semibold text-slate-500">
              רכיבים
            </label>
            <button
              type="button"
              onClick={addIngredient}
              className="text-xs text-indigo-600 hover:text-indigo-800 font-medium underline"
            >
              הוסף רכיב
            </button>
          </div>
          {ingredients.map((ing, idx) => (
            <div key={idx} className="flex gap-2 items-end">
              <input aria-label="שם"
                className="border border-md-outline rounded-md3xs px-2 py-2 flex-1"
                value={ing.name}
                onChange={(e) => updateIngredient(idx, "name", e.target.value)}
                placeholder="שם"
              />
              <input aria-label="כמות"
                className="border border-md-outline rounded-md3xs px-2 py-2 w-16"
                type="number"
                min="0"
                step="any"
                value={ing.amount}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  updateIngredient(idx, "amount", isFinite(v) ? v : 0);
                }}
              />
              <input aria-label="יחידה"
                className="border border-md-outline rounded-md3xs px-2 py-2 w-20"
                value={ing.unit}
                onChange={(e) => updateIngredient(idx, "unit", e.target.value)}
                placeholder="יחידה"
              />
              <button
                type="button"
                aria-label={`הסר רכיב ${idx + 1}`}
                onClick={() => removeIngredient(idx)}
                className="text-slate-500 hover:text-red-600"
              >
                <svg aria-hidden="true" className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          ))}
        </div>

        {/* Instructions */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="text-xs font-semibold text-slate-500">
              הוראות הכנה
            </label>
            <button
              type="button"
              onClick={addInstruction}
              className="text-xs text-indigo-600 hover:text-indigo-800 font-medium underline"
            >
              הוסף שלב
            </button>
          </div>
          {instructions.map((inst, idx) => (
            <div key={idx} className="flex gap-2 items-start mb-1">
              <span className="mt-1.5 text-xs font-bold text-slate-500 flex-shrink-0 w-4 text-center">
                {inst.step}
              </span>
              <textarea aria-label="הוראות לשלב"
                className="border border-md-outline rounded-md3xs px-2 py-2 flex-1"
                value={inst.text}
                onChange={(e) => updateInstruction(idx, "text", e.target.value)}
                rows={2}
                placeholder="הוראות לשלב…"
              />
              <input aria-label="טיימר (שניות)"
                className="border border-md-outline rounded-md3xs px-2 py-2 w-20 ml-1"
                type="number"
                min="0"
                step="1"
                value={inst.timerSeconds ?? ""}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  updateInstruction(idx, "timerSeconds", e.target.value === "" ? null : (isFinite(v) ? v : null));
                }}
                placeholder="טיימר (שניות)"
              />
              <button
                type="button"
                aria-label={`הסר שלב ${idx + 1}`}
                onClick={() => removeInstruction(idx)}
                className="mt-0.5 text-slate-500 hover:text-red-600"
              >
                <svg aria-hidden="true" className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          ))}
        </div>

        {/* Photo */}
        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">
            תמונה
          </label>
          <div className="flex flex-col gap-2">
            <input aria-label="תמונת מתכון"
              type="file"
              accept="image/*"
              onChange={handleImageChange}
              disabled={uploading}
              className="block w-full text-sm text-slate-500 file:mr-3 file:py-1 file:px-3 file:rounded file:border-0 file:text-sm file:font-semibold file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100"
            />
            {uploading && <span className="text-xs text-slate-500">מעלה תמונה…</span>}
            {imageUrl && (
              <div className="flex items-center gap-2">
                <img src={imageUrl} alt="preview" className="w-16 h-16 rounded object-cover border border-slate-200" />
                <button
                  type="button"
                  onClick={() => setImageUrl(null)}
                  className="text-xs text-red-600 hover:text-red-800 underline"
                >
                  הסר תמונה
                </button>
              </div>
            )}
          </div>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">
            מקור (אופציונלי)
          </label>
          <input aria-label="כתובת אתר או ספר בישול"
            className="border border-md-outline rounded-md3xs px-3 py-2 w-full"
            value={form.source}
            onChange={(e) => setField("source", e.target.value)}
            placeholder="כתובת אתר או ספר בישול"
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">
            תגיות (מופרדות בפסיקים, אופציונלי)
          </label>
          <input aria-label="למשל: צמחוני, עתיר חלבון"
            className="border border-md-outline rounded-md3xs px-3 py-2 w-full"
            value={form.tags}
            onChange={(e) => setField("tags", e.target.value)}
            placeholder="למשל: צמחוני, עתיר חלבון"
          />
        </div>

        <div className="flex gap-2 pt-4">
          <button
            type="submit"
            className="flex-1 bg-indigo-600 text-white font-medium py-2.5 rounded-full hover:bg-indigo-700 disabled:opacity-60"
          >
            {isEdit ? "עדכון מתכון" : "יצירת מתכון"}
          </button>
        </div>
      </form>
    </div>
  );
}
