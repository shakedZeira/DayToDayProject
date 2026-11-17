# DayToDay Roadmap (2026-11-17)

Legend: effort S (<1 day) / M (1-3 days) / L (week+). **Free** = fits free tiers (Neon, Render, Cloudflare Pages/R2, Groq, GitHub Actions).

## Now (ship + stabilise)

| # | Item | Why | Effort | Free | Files |
|---|------|-----|--------|------|-------|
| 1 | Deploy: Neon + Render + Cloudflare Pages (Render/Railway/Vercel configs removed) | Phone use needs an always-reachable server | M | Yes | render.yaml, server/Dockerfile |
| 2 | Keep-alive ping + scheduler wiring | Free Render sleeps (~15 min idle); node-cron (every minute) will not fire while asleep, so reminders/push are missed | S | Yes (cron-job.org) | server/src/scheduler.ts, /api/health |
| 3 | Recipe photos off local disk (shrink + store in Postgres, or R2) | Free hosts have ephemeral disks, photos vanish on redeploy | M | Yes | server/src/recipes.ts, prisma/schema.prisma |
| 4 | Data export + backup (JSON dump of all user tables, download button, optional scheduled GitHub Action) | Free DB tiers have limited retention; this is your only safety net | M | Yes | new server/src/export.ts, client/src/SettingsView.tsx |
| 5 | GitHub Actions CI (typecheck, lint, vitest with Postgres service) | No .github folder; cheap regression guard | S | Yes | .github/workflows/ci.yml |
| 7 | Tech debt: delete flashcard/quiz generators and FlashcardOut/QuizOut; single source for shared types (shared.d.ts has drifted from shared/src/index.ts) | Dead code, type drift | S | Yes | server/src/providers/llm.ts(+test), shared/src/index.ts, server/src/shared.d.ts |
| 8 | Repo cleanup: logs, m[1]), window_dump*.xml, APKs, untrack spotify.properties, one .env.example | Noise, accidental commits | S | Yes | repo root, .gitignore |
| 9 | Document ALLOW_REGISTRATION / JWT_SECRET in .env.example; add JWT refresh or longer session | 7-day token forces re-login on phone | S | Yes | server/src/auth.ts, .env.example |

## Next (daily-use value)

| # | Item | Why | Effort | Free | Files |
|---|------|-----|--------|------|-------|
| 10 | Log a recipe as a meal (per-serving calories from ingredients to MealLog) | Cooking and Nutrition tabs are separate; recipes never feed calorie totals | M | Yes | server/src/recipes.ts, nutrition.ts, client/src/RecipeDetail.tsx, NutritionSummary.tsx |
| 11 | Persistent shopping list (server-side, check-off, pantry) + weekly meal plan | ShoppingList is computed client-side from selected recipes and is lost on reload | M | Yes | client/src/ShoppingList.tsx, new prisma model + route |
| 12 | Offline write queue in PWA (tasks, meals, workout sets) | Service worker only caches API GETs (NetworkFirst); writes fail offline, which hurts in the gym | L | Yes | client/src/sw.ts, api.ts, *Api.ts |
| 13 | Android offline-first sync (Room already holds workouts/NBA; add outbox for tasks, goals, meals) | App works on mobile data gaps and sleeping free server | L | Yes | composeApp/.../data/, DayTodayApiImpl.kt |
| 14 | Weekly review (Sunday digest: tasks done, workouts, calories vs target, Italian XP, PRs) + push | Digest is daily-only | M | Yes | server/src/digest.ts, progress.ts, pushSender.ts, client/src/DigestView.tsx |
| 15 | Smarter notifications: per-task reminders, quiet hours, snooze, workout-day nudge from routine | Scheduler is a one-minute cron with fixed rules | M | Yes | server/src/scheduler.ts, push.ts, client/src/Notifications.tsx |
| 16 | Parity audit web vs Android (cooking, Italian path, goals, progress) | Android has NBA/Spotify/steps; web has cooking, Italian path; Android cooking tab is new | M | Yes | composeApp/.../screen/, client/src/ |
| 17 | Android tests (ViewModels, repositories) | No androidTest dir; androidUnitTest is nearly empty | M | Yes | composeApp/src/androidUnitTest |
| 18 | Client tests for core flows (Tasks, Workouts, Recipes) | Only 4 client test files vs 40+ screens | M | Yes | client/src/*.test.tsx |
| 19 | Accessibility pass: labels, focus states, contrast, reduced motion | aria-/alt usage in only ~7 client files | S-M | Yes | client/src/*.tsx, index.css |
| 20 | Move expensive aggregations out of per-request code (progress, digest load all tasks/lessons then filter in JS) | Fine now, slow on a free-tier DB later | S | Yes | server/src/digest.ts, progress.ts |

## Later (nice to have)

| # | Item | Why | Effort | Free | Files |
|---|------|-----|--------|------|-------|
| 21 | Home-screen widgets (today tasks, steps, next workout) | Daily phone use | M | Yes | composeApp (Glance) |
| 22 | Health Connect: weight, sleep, heart rate into Progress | Steps only today | M | Yes | HealthRepositoryImpl.kt |
| 23 | Nutrition: barcode scan, macros (protein/carbs/fat), custom foods | Calories only today | M-L | Yes (Open Food Facts) | foods.ts, nutrition.ts, nutritionPhoto.ts |
| 24 | Real vision provider for photo calories (currently stub) | VISION_PROVIDER=stub | M | Partly (Gemini free tier) | server/src/providers |
| 25 | Italian: spaced-repetition review queue, speaking scoring, offline lesson packs | Builds on path/XP system | L | Yes | italianPath*.ts, client/src/Italian* |
| 26 | Workout: plate calculator, supersets, deload suggestions, volume charts per muscle | Progression engine exists | M | Yes | progression.ts, client/src/Analytics.tsx |
| 27 | Observability: structured logs, error reporting (Sentry free) | Debugging a hosted app | S | Yes | server/src/index.ts |
| 28 | Prisma 5 to 6/7 and Node/Vite upgrade routine (Renovate) | Keep deps fresh | S | Yes | package.json |
