# Cooking Tab — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Context note:** Implement this plan via dispatched subagents (one per task, fresh context) to avoid blowing up the orchestrating agent's context. The orchestrator should review between tasks, not implement inline.

**Goal:** Add a top-level **Cooking** tab so you can learn to cook basic, healthy, tasty food (high-protein focus, e.g. protein pancakes). The tab stores recipes, lets you cook them step-by-step with timers, log a cooked recipe as a meal in your existing nutrition tracker, favorite recipes, build a shopping list, and works offline (PWA).

**Architecture:** Client React PWA view (`CookingView` + sub-components) + `cookingApi.ts`, registered as a new bottom-nav item in `App.tsx`/`Nav.tsx`. Server-side Prisma `Recipe` model (+ seed data) behind an Express `recipesRouter` guarded by the JWT `requireAuth` middleware. Photos handled with `multer` (same pattern as the PDFs module). Recipes are a shared library (visible to all users, owned by a seed/system user), with per-user favorites. A cooked recipe can be logged as a `MealLog` row via the existing Foods flow.

**Tech Stack:** Express, Prisma, PostgreSQL, `multer` (multipart photo upload), React, TypeScript, Tailwind, plain inline SVG/timers for cooking mode, PWA (vite-plugin-pwa / workbox) for offline caching.

**Spec:** `docs/superpowers/specs/2026-08-30-personal-daily-companion-design.md`

## Global Constraints

- Everything under `D:\AI Projects\DayToDayProject` (no C: usage).
- TypeScript throughout; lint + typecheck must pass before any commit (`npm run typecheck`, `npm run lint`).
- Single-user app: all data rows carry an `ownerId` set to the authed user's id (recipes themselves are shared/system-owned; favorites are per-user).
- All authed routes require a valid Bearer token via `requireAuth` from `server/src/session.ts` (exports `requireAuth` and `AuthedRequest`).
- Photo upload is optional/local-first; default runs without paid API keys.
- Windows host (cmd.exe shell); paths use `\` in commands.
- Server tests use `vitest` + `supertest` + `prisma` from `server/src/db.ts`, with `npm test --workspace server`.

---

## Requirement Summary (from your answers)

| Area | Decision |
|------|----------|
| Tab placement | New top-level bottom-nav tab ("Cooking") |
| Recipe fields | Title, ingredients, instructions, prep & cook time, servings, calories, protein, carbs/fat, difficulty, tags, image/photo, source |
| Categorization | By meal (breakfast / lunch / dinner / snack) |
| Cooking mode | Yes — step-by-step instructions with per-step timers |
| Meal-logging link | Yes — cook recipe → log as meal in Foods (calories/protein carry over) |
| Health focus | High protein |
| Seed recipes | Yes — curated starter set including protein pancakes |
| Ownership | Shared library (visible to all) |
| Photos | Yes — upload/take photo per recipe |
| Favorites | Yes — per-user bookmarks |
| Shopping list | Yes — combine ingredients across recipes |
| Offline / PWA | Yes — recipes cached for offline use |
| Extra | None |

---

### Task 1: Recipe data model + Prisma seed

**Files:**
- Modify: `server/prisma/schema.prisma`
- Create: `server/src/recipes.seed.ts` (shared starter recipe data incl. protein pancakes)
- Modify: `server/src/index.ts` or a new seed entry point (wire seed into `prisma db seed`)

**Interfaces:**
- Consumes: `prisma` from `server/src/db.ts`.
- Produces: Prisma model `Recipe`; seed script `seedRecipes()` inserting ~10 starter high-protein recipes (protein pancakes, scrambled eggs, grilled chicken, etc.) each tagged by meal; exports shared types appended to `shared/src/index.ts`.

- [ ] **Step 1: Add the Recipe model to the Prisma schema**

  Append to `server/prisma/schema.prisma`:
  ```prisma
  model Recipe {
    id          String   @id @default(cuid())
    ownerId     String?    // null = shared/system recipe
    title       String
    description String?
    meal        String     // "breakfast" | "lunch" | "dinner" | "snack"
    difficulty  String     // "beginner" | "intermediate" | "advanced"
    prepTimeMin Int
    cookTimeMin Int
    servings    Int
    calories    Int        // per serving
    proteinG    Float      // per serving
    carbsG      Float      // per serving
    fatG        Float      // per serving
    ingredients String     // JSON string: [{name, amount, unit}]
    instructions String   // JSON string: [{step, text, timerSeconds}]
    imageUrl    String?
    source      String?
    tags        String?    // comma-separated, e.g. "high-protein,quick"
    createdAt   DateTime   @default(now())
    updatedAt   DateTime   @updatedAt
    favorites   RecipeFavorite[]

    @@index([meal])
    @@index([ownerId])
  }

  model RecipeFavorite {
    id        String   @id @default(cuid())
    ownerId   String
    recipeId  String
    createdAt DateTime @default(now())
    recipe    Recipe   @relation(fields: [recipeId], references: [id], onDelete: Cascade)

    @@unique([ownerId, recipeId])
    @@index([ownerId])
  }
  ```

- [ ] **Step 2: Regenerate Prisma client and push schema**

  ```bash
  npx prisma generate --schema server/prisma/schema.prisma
  npx prisma db push --schema server/prisma/schema.prisma
  ```
  Expected: client regenerated; DB updated; exit 0.

- [ ] **Step 3: Write the seed script**

  Create `server/src/recipes.seed.ts` with `seedRecipes()` inserting a curated set of starter recipes (include **Protein Pancakes**), each tagged by meal, high-protein focused, with ingredients/instructions JSON and optional imageUrl. Wire it into `prisma.db.seed` or a `npm run seed --workspace server` addition.

- [ ] **Step 4: Export shared recipe types**

  In `shared/src/index.ts` append:
  ```ts
  export interface RecipeIngredient { name: string; amount: number; unit: string; }
  export interface RecipeInstructionStep { step: number; text: string; timerSeconds?: number | null; }
  export interface Recipe { id: string; ownerId?: string | null; title: string; description?: string | null; meal: string; difficulty: string; prepTimeMin: number; cookTimeMin: number; servings: number; calories: number; proteinG: number; carbsG: number; fatG: number; ingredients: RecipeIngredient[]; instructions: RecipeInstructionStep[]; imageUrl?: string | null; source?: string | null; tags?: string | null; isFavorite: boolean; createdAt: string; }
  export interface RecipeCreateInput { title: string; description?: string | null; meal: string; difficulty: string; prepTimeMin: number; cookTimeMin: number; servings: number; calories: number; proteinG: number; carbsG: number; fatG: number; ingredients: RecipeIngredient[]; instructions: RecipeInstructionStep[]; imageUrl?: string | null; source?: string | null; tags?: string | null; }
  export interface RecipeView extends Recipe {}
  ```

---

### Task 2: Server recipes API

**Files:**
- Create: `server/src/recipes.ts` (router)
- Create: `server/src/recipes.test.ts`
- Modify: `server/src/app.ts` (mount `recipesRouter`)
- Modify: `server/src/multer.ts` or reuse existing photo-upload config

**Interfaces:**
- Consumes: `prisma` from `server/src/db.ts`; `requireAuth`, `AuthedRequest` from `server/src/session.ts`; `router` pattern from Phase 1 (`tasksRouter`/`foodsRouter`); photo-upload config (`multer`).
- Produces: REST routes guarded by `requireAuth`:
  - `GET /api/recipes` — list shared recipes (with optional `meal` filter, `?favorites=true` for user's favorites, `?mine=true` for user's own)
  - `GET /api/recipes/:id` — single recipe (with `isFavorite` flag for authed user)
  - `POST /api/recipes` — create a recipe (ownerId = authed user)
  - `PUT /api/recipes/:id` — update own recipe
  - `DELETE /api/recipes/:id` — delete own recipe
  - `POST /api/recipes/:id/favorite` — toggle favorite
  - `GET /api/recipes/favorites` — list user's favorite recipes
  - `POST /api/recipes/upload` — multipart photo upload → returns imageUrl (multer, stored under `server/uploads/recipes/`)
- Produces: shared types (already in Task 1); seed is separate.

- [ ] **Step 1: Write the recipes router** — full CRUD + favorite toggle + photo upload, all `requireAuth`-guarded, `ownerId` set on create/update, recipes visible to all (shared) but editable only by owner.
- [ ] **Step 2: Mount the router** in `server/src/app.ts` (e.g. `app.use("/api/recipes", recipesRouter)`).
- [ ] **Step 3: Write tests** — `recipes.test.ts` covering list/filter, create, favorite toggle, photo upload, 404, unauthorized.

---

### Task 3: Client cooking API + types

**Files:**
- Create: `client/src/cookingApi.ts`
- Modify: `client/src/App.tsx` (register `cooking` view)
- Modify: `client/src/Nav.tsx` (add "Cooking" bottom-nav item)

**Interfaces:**
- Consumes: `authedFetch` from `client/src/auth.ts`.
- Produces: `cookingApi.ts` with `fetchRecipes(params)`, `fetchRecipe(id)`, `createRecipe(input)`, `updateRecipe(id, input)`, `deleteRecipe(id)`, `toggleFavorite(recipeId)`, `fetchFavorites()`, `uploadRecipePhoto(file)`; exported types re-exported from `shared`.

- [ ] **Step 1: Write `cookingApi.ts`** mirroring the `foodApi.ts` pattern (authedFetch + JSON).
- [ ] **Step 2: Register the view** — add `{ view: "cooking", <CookingView /> }` in `App.tsx` and `{ key: "cooking", label: "Cooking" }` in `Nav.tsx`.

---

### Task 4: Client CookingView + sub-components

**Files:**
- Create: `client/src/CookingView.tsx` (shell with meal tabs)
- Create: `client/src/RecipeList.tsx` (list/grid of recipes, filter by meal, favorite star)
- Create: `client/src/RecipeDetail.tsx` (full recipe view: image, macros, ingredients, instructions, "Cook" button, "Log as meal" button)
- Create: `client/src/RecipeForm.tsx` (create/edit recipe, includes photo picker)
- Create: `client/src/CookingMode.tsx` (step-by-step view with per-step timers)
- Create: `client/src/ShoppingList.tsx` (combine ingredients across selected recipes)
- Create: `client/src/cookingApi.ts` (if not done in Task 3)

**Interfaces:**
- `CookingView`: tabs for Breakfast / Lunch / Dinner / Snack + All + Favorites; recipe cards; navigate to detail.
- `RecipeDetail`: show image, per-serving macros (calories/protein/carbs/fat), difficulty, prep/cook time, servings, ingredients checklist, instructions, "Start Cooking" → `CookingMode`, "Log as Meal" → calls `logMeal` from `foodApi.ts` with computed macros.
- `CookingMode`: step-by-step; each step shows text + optional countdown timer (`timerSeconds`); mark steps complete.
- `ShoppingList`: multi-select recipes → aggregate ingredient names/amounts (combine duplicates), checkable list.
- `RecipeForm`: all recipe fields incl. meal/difficulty/tags, ingredients + instructions as structured arrays, photo upload.

- [ ] **Step 1: CookingView shell + meal tabs + RecipeList**
- [ ] **Step 2: RecipeDetail + RecipeForm**
- [ ] **Step 3: CookingMode (step timers)**
- [ ] **Step 4: ShoppingList**
- [ ] **Step 5: Wire "Log as Meal" to existing `foodApi.logMeal`**

---

### Task 5: Photos (multer upload)

**Files:**
- Modify: `server/src/recipes.ts` (photo upload route)
- Create: `client/src/RecipeForm.tsx` photo picker (uses `<input type="file">` or camera capture)
- Modify: `server/src/app.ts` (serve static `/uploads/recipes/` or return URL)

**Interfaces:**
- Consumes: `multer`; `server/uploads/recipes/` dir.
- Produces: `POST /api/recipes/upload` returns `{ imageUrl }`; client displays image in `RecipeDetail`/`RecipeForm`.

- [ ] **Step 1: Server photo upload route + static serve**
- [ ] **Step 2: Client photo picker + preview in RecipeForm**

---

### Task 6: Favorites + shopping list wiring

**Files:**
- Already covered in Tasks 2–4 (favorite toggle route, `isFavorite` flag, ShoppingList component).

- [ ] **Step 1: Favorite star on recipe cards + detail**
- [ ] **Step 2: Favorites tab in CookingView**
- [ ] **Step 3: Shopping list aggregation + checkboxes**

---

### Task 7: Offline / PWA support

**Files:**
- Modify: `client/vite.config.ts` (workbox config) — precache cooking routes/assets
- Modify: `client/src/sw.ts` if custom caching needed for `/api/recipes`

**Interfaces:**
- Consumes: `vite-plugin-pwa` (already configured per README).
- Produces: recipe list/detail pages cached for offline; recipes fetched once available offline.

- [ ] **Step 1: Add cooking routes to PWA precache / runtime caching**
- [ ] **Step 2: Verify offline load of recipe list**

---

### Task 8: Tests + typecheck + lint

**Files:**
- `server/src/recipes.test.ts`
- `client/src/*.test.tsx` (optional, vitest)

- [ ] **Step 1: `npm test --workspace server`** — recipes tests pass.
- [ ] **Step 2: `npm run typecheck`** — client/server/shared pass.
- [ ] **Step 3: `npm run lint`** — client/server pass.
- [ ] **Step 4: `npx prisma generate` + `npx prisma db push`** — schema valid.

---

### Acceptance Criteria

- [ ] New "Cooking" bottom-nav tab opens a recipe list filtered by meal.
- [ ] Recipe detail shows title, ingredients, instructions, macros (calories/protein/carbs/fat), difficulty, time, servings, image.
- [ ] "Protein Pancakes" exists in the seed starter set.
- [ ] Cooking mode steps through instructions with per-step timers.
- [ ] "Log as Meal" on a recipe creates a `MealLog` via the existing Foods flow (carries calories/protein).
- [ ] Favorites toggle persists per-user; favorites tab shows them.
- [ ] Shopping list combines ingredients across selected recipes.
- [ ] Photo upload works (optional image per recipe).
- [ ] App works offline for cached recipes (PWA).
- [ ] `npm run typecheck`, `npm run lint`, `npm test --workspace server` all pass.

---

### Cross-Module Hooks

- **Nutrition (Foods):** `RecipeDetail` "Log as Meal" → `foodApi.logMeal(token, { foodId, grams })` — either create a synthetic `Food` row from the recipe macros or log the recipe's per-serving calories/protein against today's date so the daily calories/protein graph updates.
- **Progress dashboard:** optionally surface "recipes cooked this week" in `ProgressView` (extends `ProgressHealthSummary`).
