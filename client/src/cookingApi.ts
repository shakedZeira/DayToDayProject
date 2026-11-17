import { authedFetch } from "./auth";
import type { Recipe, RecipeCreateInput, RecipeView } from "shared";

export interface RecipeListParams {
  meal?: string;
  favorites?: boolean;
  mine?: boolean;
}

interface FavoriteToggleResult {
  isFavorited: boolean;
  favoriteCount: number;
}

interface UploadRecipePhotoResult {
  imageUrl: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function fetchRecipes(
  token: string,
  params?: RecipeListParams
): Promise<Recipe[]> {
  const qs = new URLSearchParams();
  if (params?.meal) qs.set("meal", params.meal);
  if (params?.favorites) qs.set("favorites", "true");
  if (params?.mine) qs.set("mine", "true");
  const url = `/api/recipes${qs.toString() ? `?${qs.toString()}` : ""}`;
  const res = await authedFetch(token, url);
  return json<Recipe[]>(res);
}

export async function fetchRecipe(token: string, id: string): Promise<RecipeView> {
  const res = await authedFetch(token, `/api/recipes/${id}`);
  return json<RecipeView>(res);
}

export async function createRecipe(
  token: string,
  input: RecipeCreateInput
): Promise<RecipeView> {
  const res = await authedFetch(token, "/api/recipes", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<RecipeView>(res);
}

export async function updateRecipe(
  token: string,
  id: string,
  input: RecipeCreateInput
): Promise<RecipeView> {
  const res = await authedFetch(token, `/api/recipes/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<RecipeView>(res);
}

export async function deleteRecipe(token: string, id: string): Promise<void> {
  const res = await authedFetch(token, `/api/recipes/${id}`, {
    method: "DELETE",
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
}

export async function toggleFavorite(
  token: string,
  recipeId: string
): Promise<FavoriteToggleResult> {
  const res = await authedFetch(token, `/api/recipes/${recipeId}/favorite`, {
    method: "POST",
  });
  return json<FavoriteToggleResult>(res);
}

export async function fetchFavorites(token: string): Promise<RecipeView[]> {
  const res = await authedFetch(token, "/api/recipes/favorites");
  return json<RecipeView[]>(res);
}

export async function uploadRecipePhoto(
  token: string,
  file: File
): Promise<UploadRecipePhotoResult> {
  const form = new FormData();
  form.append("file", file);
  // Do not set Content-Type: the browser must set the multipart boundary.
  const res = await authedFetch(token, "/api/recipes/upload", {
    method: "POST",
    body: form,
  });
  return json<UploadRecipePhotoResult>(res);
}
