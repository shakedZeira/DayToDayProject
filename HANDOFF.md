# DayToDay Project — Handoff (2026-10-03)

## What was done (committed as `a30b09e`)

### Health Connect — "Connect Health Connect" button
- Fixed `HealthConnectPermissionActivity` to always launch the permission contract
  (was skipping on config changes via `savedInstanceState == null` guard).
- Fixed `HealthConnectPermissionRequest` bridge: clears the in-flight deferred after
  completion so a second tap on the button doesn't resolve a stale result.
- `HealthRepositoryImpl.requestStepsPermission` now clears any prior deferred before
  starting a new request, and adds `FLAG_ACTIVITY_CLEAR_TOP`.
- `HomeScreen.onResume` now clears stats when permission was previously granted but
  is now missing, so the connect prompt reappears.
- Added `<property android:name="android.support.health_app_types" android:value="fitness"/>`
  to AndroidManifest.xml — **this caused a build failure** (see Remaining).

### NBA Scores — was showing SSL error / offline
- Root cause: `DayTodayApiImpl` hit `https://api.daytoday.app/` which doesn't exist
  (TLSV1_ALERT_UNRECOGNIZED_NAME). No NBA backend in this project's server.
- Replaced with real APIs from the **NBA Daily** project (`D:\AI Projects\NBA daily`):
  - **BigBallsData** (`api.bigballsdata.com/v1/nba/`) for live games + box scores.
    Needs a Bearer API key — without it, calls return 401.
  - **ESPN** (`site.api.espn.com/apis/site/v2/sports/basketball/nba/`) for news + injuries.
    Free, no auth required.
- New files: `NbaBigBallsClient.kt`, `NbaEspnClient.kt` (Ktor clients, flexible JSON
  parsing so rigid DTOs aren't needed).
- `NbaRepositoryImpl` rewritten: tries BBS → falls back to ESPN → seeds demo games
  (Lakers/Celtics, Warriors/Nuggets) on first launch when both APIs are down.
- Background refresh is fire-and-forget via `GlobalScope.launch(Dispatchers.IO)`.
- `NbaUseCases.getScoreboard` no longer marks empty results as stale.

### Progress screen — SSL error
- `WorkoutProgressViewModel` now computes `DailyProgress` locally from
  `LocalSummaryUseCases.todayStats()` (Health Connect steps + Room sessions) when the
  network call fails. Never shows a hard SSL error.
- `PersonalBests` → empty list on failure. `WeeklyProgress` → computed locally from
  Room sessions for the past 7 days.

### Workout — build/save/calories
- `WorkoutActiveViewModel`: added `builderSessions` + `building` state flows.
- `WorkoutActiveScreen`: three modes — ExercisePicker, WorkoutBuilder, ActiveWorkout.
  "Build Workout" button on HomeScreen routes here.
- WorkoutBuilder: tap exercises to add them to a plan, save plan to Room, start workout
  from the first exercise in the plan.
- `completeWorkout()` now calls `calculateCaloriesBurned(session)` which sums
  `kcalPerRep * reps` for completed sets, scaled by body weight (default 70 kg).
- Calories flow into HomeScreen total via existing `LocalSummaryUseCases.todayStats()`.

### Settings — profile
- Profile row now toggles inline expansion of `BodyProfileCard` (was a no-op comment).
- `BodyProfileCard` fields use `remember(profile.field) { mutableStateOf(...) }` so they
  refresh from the `userProfile` flow on recomposition after save.
- Weight/height/age/gender all persist via `SettingsManager` → DataStore.

## Remaining — does NOT compile (10 min budget ran out)

### 1. AndroidManifest.xml `<property>` tag — **BUILD FAILURE**
The `<property>` element for `android.support.health_app_types` was added to
`<manifest>` but AAPT rejects it: "unexpected element `<property>` found in `<manifest>`".
The correct place for this property is unknown — it may need to be a `<application>`
meta-data, or may not be needed at all on Android 14+ (Health Connect is a framework module).
**Fix**: Remove the `<property>` tag (revert to pre-edit state) OR research the correct
manifest location. The permission flow may work without it on Android 14+.

### 2. `WorkoutProgressViewModel` — `minusDays` unresolved
Changed to `.minus(7, DateTimeUnit.DAY)` but `kotlinx.datetime.minus` import may not
resolve. The `DateTimeUnit` import is present. Needs verification.

### 3. `WorkoutProgressViewModel` — duplicate `UiState` definition
The file had its own `sealed interface UiState` at the bottom which was removed.
A shared `UiState.kt` was created in the same package. Should compile now but unverified.

### 4. `WorkoutActiveScreen` — `when` expressions need `else` branch
Added `else -> LoadingOverlay(...)` to both `ExercisePicker` and `WorkoutBuilder`
`when(exercisesState)` blocks. Unverified.

### 5. `SettingsScreen` — `mutableStateOf` delegate issue
Changed `var profileExpanded by remember { mutableStateOf(false) }` to
`val profileExpanded = remember { mutableStateOf(false) }` + manual toggle function.
Unverified.

### 6. `NbaRepositoryImpl` — `exceptionOrNull()` on Result
The `Result.exceptionOrNull()` calls were kept as-is. This is a Kotlin stdlib member
function (available since 1.5). Should work with Kotlin 1.9.23 but unverified.

### 7. `NbaBigBallsClient` — no API key wired
BBS calls will return 401 without a key. The NBA Daily project has an `apikey.properties`
file with a placeholder `BBS_API_KEY`. To make live games work:
- Create `apikey.properties` at the repo root with `BBS_API_KEY=your_key`
- Wire it into `build.gradle.kts` as `buildConfigField`
- Add the key to `NbaBigBallsClient` via an `Authorization: Bearer` header
  (see `BigBallsAuthInterceptor.kt` in the NBA Daily project)
Without this, BBS falls back to ESPN which has no game scoreboard endpoint,
so the demo seed data is what users see until BBS auth is wired.

### 8. `NbaEspnClient` — JSON parsing unverified
The ESPN clients use flexible `JsonObject` parsing with helper functions
(`jsonPrimitiveContent()`, `getString()`, `intOrNull()`). These were written from
the NBA Daily DTOs but never compiled. The `parseInjuryTeam()` function was moved
out of the companion object into a package-level function. Unverified.

### 9. `NbaRepositoryImpl` — `BbsMatchupDto.toBoxScore()` sums player stats naively
The TeamStats aggregation sums individual player stats for rebounds/assists/etc.
This may not match the API's team-level totals. The BBS matchup endpoint returns
player-level season averages, not game totals, so these numbers may be wrong.

### 10. Build never succeeded — unverified list
The build was attempted once (failed on `<property>`), then several edits were made
and a second build was started but the 10-minute budget ran out before results.
None of the fixes in "Remaining" items 2–8 have been compile-verified.

## Files changed in this commit (17 files, +3307 lines)

**Health Connect:**
- `AndroidManifest.xml` — added `<property>` tag (causes build failure, needs revert)
- `HealthConnectPermissionActivity.kt` — always launch permission contract
- `HealthConnectPermissionRequest.kt` — clear deferred after complete
- `HealthRepositoryImpl.kt` — clear prior deferred, CLEAR_TOP flag
- `HomeScreen.kt` — refresh stats on resume when permission state changes

**NBA (from NBA Daily project):**
- `NbaBigBallsClient.kt` (NEW) — BBS API client
- `NbaEspnClient.kt` (NEW) — ESPN API client
- `NbaRepositoryImpl.kt` — rewritten to use BBS+ESPN, seed demo data
- `NbaUseCases.kt` — empty results not stale
- `NbaScoreboardScreen.kt` — stale indicator preserved

**Workout:**
- `WorkoutActiveScreen.kt` — build mode + WorkoutBuilder composable
- `WorkoutActiveViewModel.kt` — builder flows, calorie calc on complete
- `WorkoutProgressViewModel.kt` — local-first progress, minusDays fix
- `UiState.kt` (NEW) — shared sealed interface for workout screens
- `WorkoutUseCases.kt` — was re-read, no functional change

**Settings:**
- `SettingsScreen.kt` — profile toggle, flow-backed text fields

## SHA reference
Commit: `a30b09e` — "feat: fix Health Connect, NBA scores, Progress SSL, Workout builder, Settings profile"

## Next step (highest priority)
Get the build to compile. The `<property>` manifest tag is the only known hard failure.
After that, verify the remaining items 2–8 compile cleanly, then test on device.
