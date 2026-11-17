// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from "vitest";
import { render, screen, cleanup, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Recipe, RecipeView } from "shared";
import CookingView from "./CookingView";
import RecipeDetail from "./RecipeDetail";
import { mockApi } from "./testFetch";

const recipe: Recipe = {
  id: "r1", ownerId: null, title: "Shakshuka", description: "Eggs in tomato sauce", meal: "breakfast",
  difficulty: "beginner", prepTimeMin: 10, cookTimeMin: 20, servings: 2, calories: 320, proteinG: 18,
  carbsG: 20, fatG: 16,
  ingredients: [{ name: "eggs", amount: 4, unit: "pcs" }],
  instructions: [{ step: 1, text: "Simmer the sauce", timerSeconds: 600 }],
  imageUrl: null, source: null, tags: null,
  createdAt: "2026-11-01T08:00:00.000Z", updatedAt: "2026-11-01T08:00:00.000Z",
  favoriteCount: 0, isFavorited: false,
};
const view: RecipeView = { ...recipe, tags: null, favoriteCount: 0, isFavorited: false };

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("CookingView", () => {
  it("lists recipes with calories", async () => {
    mockApi({ "GET /api/recipes": [recipe] });
    render(<CookingView token="tok" />);
    expect(await screen.findByText("Shakshuka")).toBeTruthy();
    expect(screen.getByText(/320/)).toBeTruthy();
  });

  it("shows an empty message when there are no recipes", async () => {
    mockApi({ "GET /api/recipes": [] });
    render(<CookingView token="tok" />);
    expect(await screen.findByText("לא נמצאו מתכונים.")).toBeTruthy();
  });

  it("shows the API error when the list fails", async () => {
    mockApi({ "GET /api/recipes": new Response(JSON.stringify({ error: "nope" }), { status: 500 }) });
    render(<CookingView token="tok" />);
    expect(await screen.findByText("nope")).toBeTruthy();
  });

  it("opens the detail view when a recipe is clicked", async () => {
    mockApi({ "GET /api/recipes": [recipe] });
    render(<CookingView token="tok" />);
    await userEvent.click(await screen.findByText("Shakshuka"));
    expect(await screen.findByRole("heading", { level: 1, name: "Shakshuka" })).toBeTruthy();
    expect(screen.getByText("Simmer the sauce")).toBeTruthy();
  });

  it("filters by meal using the tab query", async () => {
    const { fn } = mockApi({ "GET /api/recipes": [] });
    render(<CookingView token="tok" />);
    await screen.findByText("לא נמצאו מתכונים.");
    await userEvent.click(screen.getByRole("button", { name: "ארוחת ערב" }));
    await waitFor(() => expect(fn.mock.calls.some((c) => String(c[0]).includes("meal=dinner"))).toBe(true));
  });
});

describe("RecipeDetail", () => {
  it("logs one serving as a meal", async () => {
    const onLogged = vi.fn();
    const { calls } = mockApi({ "POST /api/recipes/r1/log": { ok: true } });
    render(
      <RecipeDetail token="tok" recipe={view} onBack={() => {}} onEdit={() => {}} onDelete={() => {}}
        onToggleFavorite={() => {}} onLogged={onLogged} />
    );
    await userEvent.click(screen.getByRole("button", { name: /רישום כארוחה/ }));
    await waitFor(() => expect(onLogged).toHaveBeenCalled());
    expect(calls[0]).toMatchObject({ method: "POST", path: "/api/recipes/r1/log", body: { servings: 1 } });
  });
});
