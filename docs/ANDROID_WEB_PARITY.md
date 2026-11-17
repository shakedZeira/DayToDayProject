# Android / web parity

Web styling follows the Compose theme: `client/tailwind.config.js` (palette, `md-*` tokens, `md3*` radii) and
`client/src/index.css` (`md-card`, `md-btn-*`, `md-title-*`, `md-body-*`, `md-progress`, from Type.kt / Components.kt).

| Screen | Android layout | Web file | Status |
|---|---|---|---|
| Navigation | BottomNavHost NavigationBar | Nav.tsx | Matched (NBA omitted; Tasks/Nutrition/Progress under More) |
| Top bar | Centered 20sp title, surface | App.tsx header | Matched (title "DayToDay", account/logout on the right) |
| Home | Welcome back, 2-col stat cards, daily goal bar, progress link card, Quick Actions | DigestView.tsx | Matched in structure; stat tiles show digest data (workouts, tasks, Italian) instead of Health Connect steps/calories; NBA quick action replaced by Tasks |
| Workout (plans/history/progress) | ExerciseCard, SetRow, History cards, Progress stats | Plans, ActiveWorkout, Workouts, Analytics, HealthView | Partial: cards, fields, buttons, tabs, history empty state restyled; exercise animation thumbnails and per-set card layout not rebuilt |
| Cooking | Filter chips, recipe cards (160dp image, macro chips), empty state, detail sections | CookingView, RecipeList, RecipeDetail | Matched visually; Hebrew labels kept; icon chips approximated by text |
| Goals | Add goal, This Week cards, Italian section | Goals.tsx | Matched order and card style; Android steps/custom goal types are not in the web API |
| Settings | Sectioned list (Account, Notifications, Data & Privacy...) | SettingsView.tsx | Sections and styling matched; Android-only rows (Theme, Spotify, NBA alerts, Server, Share) skipped |
| Italian | Duo-style path, green/gold/orange accents | ItalianView, ItalianPath, ItalianLesson* | Colors, unit headers, node size matched; voice/chat screens Android-only |
| NBA, Steps, Spotify | Android-only | - | Skipped (no server endpoints) |

Not verified with screenshots (no mocked-browser pass yet); typecheck, build and tests pass.
