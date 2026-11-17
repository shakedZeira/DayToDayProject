import { authedFetch } from "./auth";
import type { MealPlanEntryView, PantryItemView, RecipeMeal, ShoppingItemView } from "shared";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

function send(token: string, path: string, method: string, body?: unknown) {
  return authedFetch(token, path, {
    method,
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

// Shopping list
export const fetchShoppingList = (token: string) =>
  authedFetch(token, "/api/shopping").then((r) => json<ShoppingItemView[]>(r));

export const addShoppingItem = (token: string, input: { name: string; amount?: number; unit?: string }) =>
  send(token, "/api/shopping", "POST", input).then((r) => json<ShoppingItemView>(r));

export const addRecipeToShoppingList = (
  token: string,
  recipeId: string,
  opts: { servings?: number; skipPantry?: boolean } = {}
) => send(token, `/api/shopping/recipe/${recipeId}`, "POST", opts).then((r) => json<ShoppingItemView[]>(r));

export const updateShoppingItem = (
  token: string,
  id: string,
  patch: Partial<{ name: string; amount: number; unit: string; checked: boolean }>
) => send(token, `/api/shopping/${id}`, "PATCH", patch).then((r) => json<ShoppingItemView>(r));

export const deleteShoppingItem = (token: string, id: string) =>
  send(token, `/api/shopping/${id}`, "DELETE").then((r) => json<void>(r));

export const clearCheckedShoppingItems = (token: string) =>
  send(token, "/api/shopping/checked", "DELETE").then((r) => json<{ deleted: number }>(r));

// Pantry
export const fetchPantry = (token: string) =>
  authedFetch(token, "/api/shopping/pantry").then((r) => json<PantryItemView[]>(r));

export const addPantryItem = (token: string, input: { name: string; amount?: number | null; unit?: string }) =>
  send(token, "/api/shopping/pantry", "POST", input).then((r) => json<PantryItemView>(r));

export const updatePantryItem = (
  token: string,
  id: string,
  patch: Partial<{ name: string; amount: number | null; unit: string }>
) => send(token, `/api/shopping/pantry/${id}`, "PATCH", patch).then((r) => json<PantryItemView>(r));

export const deletePantryItem = (token: string, id: string) =>
  send(token, `/api/shopping/pantry/${id}`, "DELETE").then((r) => json<void>(r));

// Meal plan
export const fetchMealPlan = (token: string, from: string, to: string) =>
  authedFetch(token, `/api/mealplan?from=${from}&to=${to}`).then((r) => json<MealPlanEntryView[]>(r));

export const setMealPlanEntry = (
  token: string,
  date: string,
  slot: RecipeMeal,
  input: { recipeId: string; servings?: number }
) => send(token, `/api/mealplan/${date}/${slot}`, "PUT", input).then((r) => json<MealPlanEntryView>(r));

export const deleteMealPlanEntry = (token: string, date: string, slot: RecipeMeal) =>
  send(token, `/api/mealplan/${date}/${slot}`, "DELETE").then((r) => json<void>(r));

export const addMealPlanToShoppingList = (
  token: string,
  from: string,
  to: string,
  opts: { skipPantry?: boolean } = {}
) => send(token, "/api/mealplan/shopping", "POST", { from, to, ...opts }).then((r) => json<ShoppingItemView[]>(r));
