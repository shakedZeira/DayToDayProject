# Steps Detail & History (Samsung Health-style) — Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Context note:** This plan is Android-only (`composeApp`). Do not touch `server/`, Prisma, or `client/`. Implement via dispatched subagents (one per task, fresh context) and review between tasks. The orchestrator should not implement inline.

**Goal:** Turn the Home screen's read-only "Steps" stat into an entry point for a real steps detail screen that mirrors the parts of Samsung Health users actually use: today's steps with a **daily goal ring**, a **Day / Week / Month / Year** history, a **bar chart with a goal line and today highlight**, tap-to-select bucket details, period **stats (average, best, total, goal met)**, a **recent-days list**, and honest **distance/calorie estimates**. The screen is offline, read-only, and built entirely from Health Connect data already available to the app.

**Architecture:**

```
Health Connect (via existing HealthRepository)
        │  stepsInRange() / totalSteps() / todaySteps()
        ▼
StepsHistoryUseCases  (androidMain, @Singleton)
  ├─ period ranges + pure aggregation  →  StepsAggregator
  ├─ goal from SettingsManager.goals (GoalType.STEPS_TODAY)
  ├─ distance/calories from UserProfile + CalorieCalculator
  └─ small in-memory TTL cache, keyed by (period, today, goal)
        ▼
StepsDetailViewModel  (Hilt, StateFlow)
        ▼
StepsDetailScreen  → StepsGoalRing + StepsBarChart (custom Compose Canvas)
        ▲
HomeScreen "Steps" card → Screen.StepsDetail → NavHost
```

No new chart dependency, no new Health Connect permission, no writes to Health Connect, and no network calls. Pure aggregation lives in `com.daytoday.usecase` so it is unit-testable on the JVM; Compose code lives in `com.daytoday.ui.screen.steps`.

**Tech Stack:** Kotlin 1.9.23, Jetpack Compose (BOM `2024.06.00`, Material 3), Hilt, Health Connect `1.1.0`, `java.time`, JUnit 4 + MockK. `compileSdk 36`, `minSdk 26`, `jvmTarget 17`.

**Source of truth for UI conventions:**
- Existing screens: `WorkoutProgressScreen.kt` (sub-screen with back arrow + loading/error), `GoalsScreen.kt` (`FilterChip`, dialogs, cards).
- Design components: `composeApp/src/commonMain/kotlin/com/daytoday/ui/theme/Components.kt` — `DayTodayCard`, `DayTodayTopAppBar`, `DayTodayButton`, `ErrorState`, `EmptyState`, `LoadingOverlay`.
- Formatters: `composeApp/src/androidMain/kotlin/com/daytoday/util/Formatters.kt`.

## Global Constraints

- Work only under `D:\AI Projects\DayToDayProject`. Windows `cmd.exe`; no heredocs, no POSIX-only commands.
- **Plan only:** this file is the deliverable. Do not implement source changes as part of authoring this plan.
- **No new Gradle dependencies.** Charts are custom `Canvas` (`drawRoundRect`, `Path`, `PathEffect`); no Vico/MPAndroidChart/Compose chart libs.
- **Read-only, no new permissions.** Reuse `HealthRepository.stepsInRange`, `totalSteps`, `todaySteps`, `getHealthPermissionState`, `requestStepsPermission`. Do not request new Health Connect record types.
- **Offline only.** Never call the server. Do not alter DTOs, Ktor clients, Room, or sync code.
- **Never invent steps.** A day absent from a successful `stepsInRange` map is shown as `—`, never as a fabricated value. Root totals and bars may render `0`, but the row must be visually distinguishable.
- **Preserve the dirty worktree.** At plan time `git status` already shows many modified files (`HomeScreen.kt`, `HealthRepository.kt`, `HealthRepositoryImpl.kt`, `LocalSummaryUseCases.kt`, `SettingsManager.kt`, `Formatters.kt`, `MainActivity.kt`, `build.gradle.kts`, notification files, plus untracked tests). Never run `git add -A`, `git reset`, `git checkout -- .`, or `git stash`. Stage only task-owned files/hunks (see each task's Commit section).
- `composeApp/src/androidUnitTest` already has JUnit4 + MockK; use the same style as `LocalSummaryUseCasesTest.kt`. Do not add `kotlinx-coroutines-test`.
- Week starts **Monday**, matching `LocalSummaryUseCases.weekStats()` and `GoalEntry` weekly resets for workouts. (Italian goals use Sunday; do not copy that here.)
- No step goal editing in this feature. Read the existing `GoalType.STEPS_TODAY` target and link to `Screen.Goals` for editing.

---

## Current State (baseline, verified 2026-10-23)

### Data layer
- `composeApp/src/androidMain/kotlin/com/daytoday/data/health/HealthRepository.kt`
  - L18 `suspend fun todaySteps(context): DailySteps`
  - L20 `suspend fun stepsInRange(context, from: LocalDate, to: LocalDate): Map<LocalDate, Long>`
  - L29 `suspend fun totalSteps(context, from, to): Long` — one aggregate over the whole window.
- `HealthRepositoryImpl.kt`
  - L159–187 `stepsInRange`: checks permission, discovers Samsung Health `DataOrigin`, loops one aggregate per day. On success the map contains **every** day in the inclusive range. Permission missing or any thrown failure returns `emptyMap()`.
  - L189–214 `totalSteps`: permission check + single aggregate, caps the window at `now`; returns `0` on missing permission/failure.
  - L302–373 `aggregateSteps`; L385–388 prefers `com.sec.android.app.shealth`, then `com.samsung.android.service.health`.
  - L427–442 `stepWindow` caps a window including today at `Instant.now()`.
- `LocalSummaryUseCases.kt`
  - L38–61 `LocalDayStats`, `LocalWeekStats`; L63 `@Singleton class LocalSummaryUseCases`.
  - L131–170 `weekStats()` computes Monday week start.
  - L257–266 `readWeekSteps()` uses `totalSteps` with a 2.5 s timeout.
- `SettingsManager.kt`
  - L137 `val userProfile: Flow<UserProfile>`; L231 `val goals: Flow<List<GoalEntry>>`; L273 `seedGoalsIfAbsent()`.
- `GoalEntry.kt` L43–50: `GoalType.STEPS_TODAY` has `defaultValue = 8_000.0`, `maxValue = 100_000`, `source = TODAY`.
- `UserProfile.kt`: `weightKg = 70.0`, `heightCm = 175.0`, `ageYears = 30`, `isMale = true`.
- `CalorieCalculator.kt` L22–25 `stepsCalories(steps, weightKg)`, factor `0.04` per step at 70 kg.
- `Formatters.kt` L42–43 `formatSteps(Long)` (locale grouping), L34–35 `formatCalories(Int)` (appends `" cal"`).

### UI layer
- `HomeScreen.kt` L354–383 builds a `listOf(QuickStat(...))` in a 2-column `LazyVerticalGrid`: Steps, Steps Calories, Workout Calories, Total Calories. L515–520 `data class QuickStat`; L522–550 `QuickStatCard`.
- `HomeScreen.kt` L452–488 is the existing pattern for a clickable full-width card navigating to `Screen.WorkoutProgress.route`; L239–250 passes `onNavigate = { route -> navController.navigate(route) }`.
- `Screen.kt` is a `sealed interface` of `data object`s with a `route` string.
- `NavHost.kt` L28–72 registers every route; `WorkoutProgress` at L54–56.
- `MainActivity.kt` L155 always renders `BottomNavHost(navController)` around `DayTodayNavHost`, so a nested screen keeps the bottom bar.
- `BottomNavHost.kt` L33–40 defines the six tabs; L42–54 currently special-cases NBA detail routes so the NBA tab stays selected.
- `WorkoutProgressScreen.kt` L50–71 is the sub-screen template: `Scaffold` + `DayTodayTopAppBar` with an `ArrowBack` `IconButton` + `navController.navigateUp()`.
- `Components.kt` (commonMain) L111–131 `DayTodayCard`, L171–187 `DayTodayTopAppBar`, L210–244 `LoadingOverlay`, L247–281 `EmptyState`, L284–325 `ErrorState`.

### Verified gaps
- No steps detail screen or route exists; the Home Steps card is not clickable.
- No per-day/week/month/year aggregation for display; only today's total and this week's total.
- No step goal is read on any UI surface; Goals shows `STEPS_TODAY` only if the user added it.
- No chart code exists in `composeApp`; `WorkoutProgressScreen` uses only text/progress rows.
- `HealthRepository` cannot distinguish "no steps recorded" from "read failed" for a single day or for an aggregate; this plan surfaces that limitation instead of hiding it.

### Worktree warning
Already-modified files have user work in them. The tasks below touch some of those files (`HomeScreen.kt`, `Formatters.kt`) and add new ones. Use hunk-level staging and never revert existing content.

---

## Samsung Health research (what we copy, what we deliberately don't)

Sources (accessed 2026-10-23):
1. Samsung US Support, "Track your steps with Samsung Health" — <https://www.samsung.com/us/support/answer/ANS10001370/>
2. Samsung UAE Support, "View your step count in Samsung Health" — <https://www.samsung.com/ae/support/apps-services/view-your-step-count-in-samsung-health/>
3. Samsung US, Samsung Health product page (Weekly Report) — <https://www.samsung.com/us/apps/samsung-health/>
4. Samsung Community, default step goal 6,000 — <https://us.community.samsung.com/t5/Galaxy-Watch/How-do-I-adjust-the-step-goals/td-p/2046410>
5. Reddit r/GalaxyWatch, 12-month step history shows average steps/day (secondary, unverified) — <https://www.reddit.com/r/GalaxyWatch/comments/1bp63em/monthly_step_count/>

| Samsung behavior | Source | Our decision |
|---|---|---|
| Tap the Steps card to open a dedicated detail view with goal progress and history | 1, 2 | **Copy.** Home Steps card navigates to `StepsDetail`. |
| Graph toggle Hours / Days / Weeks / Months; swipe the graph for earlier periods | 1 | **Partial.** Day / Week / Month / Year chips. **No Hours view**: Health Connect exposes no hourly step read in the current repository, and adding one would require new queries. Document this deviation in the UI? No — just omit the option. |
| Tap a date to see that day's count; daily goal at the top | 1, 2 | **Copy.** Tap a bar to select it and show its exact values below the chart. |
| Set target from the detail screen | 1 | **Partial.** Show the current goal with an "Edit" link to the existing Goals screen. No goal editor here. |
| Distance calculated from stride length | 2 | **Copy as an estimate.** Formula below, clearly labeled "est.". |
| Weekly Report compares week / month / year | 3 | **Copy.** Every period shows a percent change vs the previous comparable period. |
| Default daily goal = 6,000 | 4 | **Do not copy.** The app already defaults `STEPS_TODAY` to **8,000**; keeping one source of truth is more important. |
| 12-month history shows average steps/day | 5 (secondary) | **Partial.** Year view aggregates 12 monthly buckets and shows daily average + best month; it does not fabricate daily rows for the year. |

---

## Data & behavior decisions (read before coding)

### Periods (all relative to `LocalDate.now(ZoneId.systemDefault())`)
| Period | Range shown | Loaded from Health Connect | Chart buckets |
|---|---|---|---|
| DAY | today..today | `stepsInRange(today, today)` | 1 bar (today) |
| WEEK | current Monday..Sunday | `stepsInRange(monday, today)` | 7 bars, Mon..Sun; future days empty |
| MONTH | 1st..last day of current month | `stepsInRange(monthStart, today)` | one bar per calendar day; future days empty |
| YEAR | Jan 1..Dec 31 of current year | 12 × `totalSteps(monthStart, min(monthEnd, today))` + `stepsInRange(today, today)` for the today card | 12 monthly bars; future months empty |

- `previousRange` is the immediately preceding day / Monday–Sunday week / calendar month / calendar year.
- Previous total comes from **one** `totalSteps(previousRange)` call. `changePercent` is `null` when the previous total is `null` or `<= 0` (avoids divide-by-zero and fake "+100%").
- **Average** uses completed days only (`date < today`, `hasData == true`) for DAY/WEEK/MONTH. This avoids understating the average with a partial day. If there are no completed days (e.g. it is Monday), fall back to today's steps. For YEAR the daily average is `total / today.dayOfYear` because monthly buckets cannot split out a partial day — label it "daily avg" and document the one-day imprecision in code comments.
- **Goal met** counts `hasData && steps >= dailyGoal` among days up to today (today included). A missing day is **not** counted as met.
- **Pace target** for a period = `dailyGoal * elapsedDays` where `elapsedDays` is the number of calendar days from range start through today. The UI labels this "target" and shows `total / paceTarget`; it does not pretend the user is behind on a full future week.
- **Daily goal source:** first `GoalEntry` with `type == GoalType.STEPS_TODAY`; use `target.toLong().coerceAtLeast(1)`; if absent, `GoalType.STEPS_TODAY.defaultValue.toLong()` (8,000). Do not call `seedGoalsIfAbsent()` from this screen — reading must not mutate settings.
- **Stride/distance estimate:** `strideMeters = heightCm * (if (isMale) 0.415 else 0.413) / 100`; `distanceKm = steps * strideMeters / 1000`. Label "est.".
- **Calories estimate:** `CalorieCalculator.stepsCalories(totalSteps, weightKg).roundToInt()`. Label "est.".
- **Caching:** `StepsHistoryUseCases` is `@Singleton`; cache at most 8 entries for 60 s, keyed by `(period, today, goal)`. `forceRefresh` bypasses and replaces. Never cache `PermissionMissing` or `Failure`.
- **Timeouts:** DAY 5 s, WEEK 8 s, MONTH 15 s, YEAR 20 s via `withTimeoutOrNull`. On timeout return `Failure("Reading steps timed out. Try again.")`. Re-throw real cancellation.
- **Empty maps mean failure, not zero,** for the range read. A successful `stepsInRange` always contains every day (L173–180 of the impl), so an empty map is a failed/permission-denied read and must show `ErrorState` with retry. For YEAR, `totalSteps` returns `0` on both "no data" and failure; it cannot be distinguished — surface a note only when the whole year is zero.

---

## Models and signatures (the contract every task codes against)

```kotlin
package com.daytoday.usecase

import java.time.LocalDate

enum class StepsPeriod(val label: String) {
    DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year")
}

data class DayRange(val start: LocalDate, val endInclusive: LocalDate) {
    init { require(!endInclusive.isBefore(start)) }
    val days: Int get() = (endInclusive.toEpochDay() - start.toEpochDay() + 1).toInt()
}

data class StepsDay(
    val date: LocalDate,
    val steps: Long,
    /** true only when the Health Connect read returned this date. */
    val hasData: Boolean,
    val isFuture: Boolean,
    val isToday: Boolean,
)

data class StepsBucket(
    val label: String,              // "Today", "Mon".."Sun", "1".."31", "Jan".."Dec"
    val start: LocalDate,           // day or first day of month
    val endInclusive: LocalDate,
    val steps: Long,
    /** Goal for this bucket: dailyGoal for day bars; dailyGoal * elapsed days for month bars. */
    val goal: Long,
    val isCurrent: Boolean,         // today's bar / current month's bar
    val hasData: Boolean,
)

data class StepsListRow(
    val label: String,              // "Today", "Mon 20", "Mar"
    val steps: Long,
    val goal: Long,
    val hasData: Boolean,
    val isCurrent: Boolean,
)

data class StepsStats(
    val total: Long,
    val averagePerDay: Long,
    val bestDate: LocalDate?,
    val bestSteps: Long?,
    val daysGoalMet: Int?,          // null for YEAR (daily split unavailable)
    val elapsedDays: Int,
    val daysWithData: Int,
)

data class StepsHistory(
    val period: StepsPeriod,
    val range: DayRange,
    val goal: Long,                 // daily goal
    val paceTarget: Long,           // goal * elapsedDays
    val todaySteps: Long,
    val todayHasData: Boolean,
    val days: List<StepsDay>,       // daily data for DAY/WEEK/MONTH; empty for YEAR
    val buckets: List<StepsBucket>,
    val listRows: List<StepsListRow>,
    val stats: StepsStats,
    val previousTotal: Long?,
    val changePercent: Int?,
    val totalDistanceKm: Double = 0.0,
    val totalCalories: Int = 0,
    val generatedAtMs: Long = 0L,
)

sealed interface StepsLoadOutcome {
    data class Success(val history: StepsHistory) : StepsLoadOutcome
    data object PermissionMissing : StepsLoadOutcome
    data class Failure(val message: String) : StepsLoadOutcome
}
```

```kotlin
object StepsAggregator {
    fun periodRange(period: StepsPeriod, today: LocalDate): DayRange
    fun previousRange(period: StepsPeriod, today: LocalDate): DayRange
    fun buildDays(range: DayRange, today: LocalDate, stepsByDate: Map<LocalDate, Long>): List<StepsDay>
    fun dailyBuckets(period: StepsPeriod, days: List<StepsDay>, dailyGoal: Long): List<StepsBucket>
    fun monthlyBuckets(today: LocalDate, monthTotals: Map<Int, Long>, dailyGoal: Long): List<StepsBucket>
    fun stats(days: List<StepsDay>, dailyGoal: Long, today: LocalDate): StepsStats
    fun yearStats(buckets: List<StepsBucket>, dailyGoal: Long, today: LocalDate): StepsStats
    fun dayListRows(days: List<StepsDay>, dailyGoal: Long): List<StepsListRow>
    fun monthListRows(buckets: List<StepsBucket>): List<StepsListRow>
    fun changePercent(currentTotal: Long, previousTotal: Long?): Int?
    fun percentage(total: Long, target: Long): Int
}
```

```kotlin
object StepMetrics {
    fun strideMeters(heightCm: Double, isMale: Boolean): Double
    fun distanceKm(steps: Long, heightCm: Double, isMale: Boolean): Double
    fun calories(steps: Long, weightKg: Double): Int
}
```

```kotlin
@Singleton
class StepsHistoryUseCases @Inject constructor(
    private val healthRepository: HealthRepository,
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context,
) {
    suspend fun load(period: StepsPeriod, forceRefresh: Boolean = false): StepsLoadOutcome
    suspend fun hasPermission(): Boolean
    suspend fun requestPermission(): Boolean
    suspend fun hasBackgroundReadPermission(): Boolean
    suspend fun requestBackgroundReadPermission(): Boolean
    suspend fun healthSdkStatus(): HealthSdkStatus
    suspend fun invalidateCache()
}
```

---

## Task S1: Pure models + aggregator + JVM tests

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/daytoday/usecase/StepsHistoryModels.kt`
- Create: `composeApp/src/androidMain/kotlin/com/daytoday/usecase/StepsAggregator.kt`
- Create: `composeApp/src/androidUnitTest/kotlin/com/daytoday/usecase/StepsAggregatorTest.kt`

**Interfaces:**
- Consumes: nothing (pure `java.time`; no Android imports).
- Produces: every data class and `StepsAggregator` function listed above. Later tasks import these types and must not redefine them.

- [ ] **Step 1: Add the models file**

Create `StepsHistoryModels.kt` with `package com.daytoday.usecase`, the `StepsPeriod`, `DayRange`, `StepsDay`, `StepsBucket`, `StepsListRow`, `StepsStats`, `StepsHistory`, and `StepsLoadOutcome` declarations exactly as in the contract above. Keep `StepsHistory` defaults so both the pure aggregator and the health-backed use case can build it incrementally. Add short KDoc on `hasData` ("false = Health Connect did not return this day or it is in the future") and on `daysGoalMet` ("null means daily data is unavailable, e.g. YEAR").

- [ ] **Step 2: Implement ranges**

`StepsAggregator.periodRange`:

```kotlin
fun periodRange(period: StepsPeriod, today: LocalDate): DayRange = when (period) {
    StepsPeriod.DAY -> DayRange(today, today)
    StepsPeriod.WEEK -> {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        DayRange(start, start.plusDays(6))
    }
    StepsPeriod.MONTH -> DayRange(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()))
    StepsPeriod.YEAR -> DayRange(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()))
}
```

`previousRange` returns the adjacent full range: yesterday; previous Monday–Sunday; previous calendar month; previous calendar year. Use `range.start.minusDays(1)` as the previous end for WEEK and MONTH; for YEAR use `LocalDate.of(today.year - 1, 1, 1)`..`LocalDate.of(today.year - 1, 12, 31)`.

Import `java.time.DayOfWeek`, `java.time.temporal.TemporalAdjusters`.

- [ ] **Step 3: Implement `buildDays`**

Iterate inclusively from `range.start` to `range.endInclusive` with `while`. For each `date`:

```kotlin
val future = date.isAfter(today)
val hasData = !future && stepsByDate.containsKey(date)
StepsDay(
    date = date,
    steps = if (hasData) stepsByDate.getValue(date) else 0L,
    hasData = hasData,
    isFuture = future,
    isToday = date == today,
)
```

Do **not** default a missing day to `hasData = true`. Future days are `hasData = false` but are excluded from stats.

- [ ] **Step 4: Implement `dailyBuckets`**

- DAY: one bucket `label = "Today"`, `steps = today's steps`, `goal = dailyGoal`, `hasData = today's hasData`.
- WEEK: one bucket per day in the week. Label via `DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)` (`Mon`…`Sun`). `isCurrent = day.isToday`.
- MONTH: one bucket per day, `label = date.dayOfMonth.toString()`, `isCurrent = day.isToday`.

All buckets use `goal = dailyGoal` (daily bars are normalized against the daily goal). Use `days` as passed; do not drop future days in `dailyBuckets` so the week layout stays Mon–Sun — future days simply have `hasData = false` and zero height.

- [ ] **Step 5: Implement `monthlyBuckets`**

`monthTotals` is keyed by month number `1..12`. For `month in 1..12`:

```kotlin
val monthStart = LocalDate.of(today.year, month, 1)
val monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth())
val isFuture = monthStart.isAfter(today)
val isCurrent = month == today.monthValue
val elapsedInMonth = when {
    isFuture -> 0
    isCurrent -> today.dayOfMonth
    else -> monthEnd.dayOfMonth
}
StepsBucket(
    label = monthStart.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)),
    start = monthStart,
    endInclusive = monthEnd,
    steps = if (isFuture) 0L else monthTotals[month] ?: 0L,
    goal = dailyGoal * elapsedInMonth,
    isCurrent = isCurrent,
    hasData = !isFuture && monthTotals.containsKey(month),
)
```

Important: a monthly bucket that is current has a **prorated goal** (`dailyGoal * dayOfMonth`), so the dashed goal line at `1.0` stays meaningful across mixed elapsed/full months.

- [ ] **Step 6: Implement day stats**

`stats(days, dailyGoal, today)`:

- `elapsedDays = days.count { !it.isFuture }` (>= 1 for DAY/WEEK/MONTH).
- `daysWithData = days.count { !it.isFuture && it.hasData }`.
- `total = days.filter { it.hasData }.sumOf { it.steps }` (future days are `hasData = false`).
- `completed = days.filter { !it.isFuture && !it.isToday && it.hasData }`.
- `averagePerDay = if (completed.isNotEmpty()) completed.sumOf { it.steps } / completed.size else todaySteps` where `todaySteps = days.firstOrNull { it.isToday }?.takeIf { it.hasData }?.steps ?: 0L`. Integer division is intentional; no floats in the model.
- `best = days.filter { !it.isFuture && it.hasData }.maxByOrNull { it.steps }`; set `bestDate`/`bestSteps` only when `best != null && best.steps > 0`, else both null.
- `daysGoalMet = days.count { !it.isFuture && it.hasData && it.steps >= dailyGoal }` (includes today if already met).
- Guard `dailyGoal.coerceAtLeast(1)` in `percentage`.

- [ ] **Step 7: Implement year stats + list rows**

`yearStats(buckets, dailyGoal, today)`:
- `total = buckets.sumOf { it.steps }`.
- `elapsedDays = today.dayOfYear` (includes today; comment why).
- `averagePerDay = if (elapsedDays > 0) total / elapsedDays else 0`.
- `best = buckets.filter { it.hasData && it.steps > 0 }.maxByOrNull { it.steps }`; `bestDate = best?.start`, `bestSteps = best?.steps`.
- `daysGoalMet = null`, `daysWithData = buckets.count { it.hasData }` (used as "months with data"), `elapsedDays = today.dayOfYear`.
- `totalSteps` for the year may include months with no data as zero; `hasData` on buckets preserves what the month read reported.

`dayListRows`: ascending days, label `"Today"` for today, else `date.format(ofPattern("EEE d", Locale.ENGLISH))`; `goal = dailyGoal`.
`monthListRows`: map monthly buckets to `label = bucket.label`, same steps/goal/hasData/isCurrent.

- [ ] **Step 8: Implement `changePercent` and `percentage`**

```kotlin
fun changePercent(currentTotal: Long, previousTotal: Long?): Int? {
    if (previousTotal == null || previousTotal <= 0L) return null
    return (((currentTotal - previousTotal) * 100.0) / previousTotal).roundToInt()
}

fun percentage(total: Long, target: Long): Int {
    if (target <= 0L) return 0
    return ((total * 100.0) / target).roundToInt()
}
```

`percentage` is allowed to exceed 100.

- [ ] **Step 9: Write `StepsAggregatorTest.kt` (JUnit 4 + `org.junit.Assert`)**

Cover at least:
1. `periodRange(WEEK, Wed 2026-10-21)` → Mon 2026-10-19..Sun 2026-10-25; on Monday and Sunday too (boundary correctness).
2. `periodRange(MONTH, 2026-02-10)` → Feb 1..Feb 28; leap year 2028 → Feb 29.
3. `periodRange(YEAR, 2026-10-23)` → Jan 1..Dec 31.
4. `previousRange` for all four periods (week = Oct 12..18, month = Sep 1..30, year = 2025).
5. `buildDays`: a missing past day → `hasData=false, steps=0`; a future day → `isFuture=true, hasData=false`; today → `isToday=true`.
6. `dailyBuckets(WEEK)` length 7, labels Mon..Sun, current index correct, future buckets zero.
7. `monthlyBuckets` length 12; current month goal `dailyGoal * today.dayOfMonth`; a past month goal `dailyGoal * monthLength`; a future month `goal=0, hasData=false, steps=0`.
8. `stats`: with Mon 4,000 (data), Tue 9,000 (data), Wed/today 3,000 (data), goal 8,000 → total 16,000, average 6,500 (today excluded), best Tue/9,000, `daysGoalMet=1` (Tue), `elapsedDays=3`, `daysWithData=3`.
9. `stats` with Monday only (today): average falls back to today's steps.
10. `stats` with a missing completed day: `daysWithData` lower, missing day not counted as goal met.
11. `yearStats`: best month, `daysGoalMet == null`, average = total / dayOfYear.
12. `changePercent(12000, 10000) == 20`, `changePercent(5000, 0) == null`, `changePercent(5000, null) == null`, negative change.
13. `percentage(9000, 8000) == 113`.

- [ ] **Step 10: Verify S1**

```bat
if not exist composeApp\build mkdir composeApp\build
cmd /c "gradlew.bat :composeApp:testDebugUnitTest --tests "com.daytoday.usecase.StepsAggregatorTest" --console=plain > composeApp\build\steps-s1-test.log 2>&1"
echo Exit code: %ERRORLEVEL%
```

Then inspect the log:

```bat
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-s1-test.log' -Pattern 'FAILED|BUILD FAILED|tests completed' | Select-Object -Last 20"
```

Expected: `BUILD SUCCESSFUL`, every S1 test passes.

- [ ] **Step 11: Commit S1**

Stage only the three new files:

```bat
git add composeApp/src/androidMain/kotlin/com/daytoday/usecase/StepsHistoryModels.kt composeApp/src/androidMain/kotlin/com/daytoday/usecase/StepsAggregator.kt composeApp/src/androidUnitTest/kotlin/com/daytoday/usecase/StepsAggregatorTest.kt
git diff --cached --stat
git commit -m "feat(steps): add pure steps history models and aggregator"
```

Do not stage anything else. `git diff --cached --stat` must list exactly three files.

---

## Task S2: Health-backed use case + metrics + TTL cache + tests

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/daytoday/usecase/StepMetrics.kt`
- Create: `composeApp/src/androidMain/kotlin/com/daytoday/usecase/StepsHistoryUseCases.kt`
- Create: `composeApp/src/androidUnitTest/kotlin/com/daytoday/usecase/StepMetricsTest.kt`
- Create: `composeApp/src/androidUnitTest/kotlin/com/daytoday/usecase/StepsHistoryUseCasesTest.kt`

**Interfaces:**
- Consumes: `HealthRepository` (all step + permission methods), `SettingsManager.goals`/`userProfile`, `CalorieCalculator`, models/aggregator from S1.
- Produces: `@Singleton StepsHistoryUseCases` with `load(period, forceRefresh)` returning `StepsLoadOutcome`, plus permission/SDK helpers for the screen. `StepMetrics` pure object.

- [ ] **Step 1: `StepMetrics.kt`**

```kotlin
package com.daytoday.usecase

import com.daytoday.util.CalorieCalculator
import kotlin.math.roundToInt

object StepMetrics {
    private const val MALE_STRIDE_RATIO = 0.415
    private const val FEMALE_STRIDE_RATIO = 0.413

    fun strideMeters(heightCm: Double, isMale: Boolean): Double {
        if (heightCm <= 0.0 || !heightCm.isFinite()) return 0.0
        val ratio = if (isMale) MALE_STRIDE_RATIO else FEMALE_STRIDE_RATIO
        return heightCm * ratio / 100.0
    }

    fun distanceKm(steps: Long, heightCm: Double, isMale: Boolean): Double {
        if (steps <= 0L) return 0.0
        return steps * strideMeters(heightCm, isMale) / 1000.0
    }

    fun calories(steps: Long, weightKg: Double): Int {
        if (steps <= 0L || weightKg <= 0.0) return 0
        return CalorieCalculator.stepsCalories(steps, weightKg).roundToInt()
    }
}
```

- [ ] **Step 2: `StepsHistoryUseCases.kt` skeleton**

```kotlin
package com.daytoday.usecase

import android.content.Context
import com.daytoday.data.health.HealthPermissionState
import com.daytoday.data.health.HealthRepository
import com.daytoday.data.health.HealthSdkStatus
import com.daytoday.model.UserProfile
import com.daytoday.settings.GoalType
import com.daytoday.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class StepsHistoryUseCases @Inject constructor(
    private val healthRepository: HealthRepository,
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context,
) {
    // cache/clock hooks below are internal so JVM tests can control time without new deps
    internal var cacheTtlMs: Long = 60_000L
    internal var nowMs: () -> Long = { System.currentTimeMillis() }
    // ...
}
```

- [ ] **Step 3: Implement `load`**

Use `withContext(Dispatchers.IO)`.

1. Resolve `today = LocalDate.now(ZoneId.systemDefault())`.
2. `hasPermission()` via `getHealthPermissionState(context).getOrNull() == GRANTED`; if false return `PermissionMissing`.
3. Read goal: `settingsManager.goals.first().firstOrNull { it.type == GoalType.STEPS_TODAY }?.target?.toLong()?.coerceAtLeast(1L) ?: GoalType.STEPS_TODAY.defaultValue.toLong()`.
4. Cache lookup with key `CacheKey(period, today, goal)`; return cached `StepsHistory` unless `forceRefresh`.
5. Dispatch by period inside `withTimeoutOrNull(timeoutFor(period))`:
   - **DAY:** `map = stepsInRange(today, today)`. If `map.isEmpty()` → `Failure("Could not read steps from Health Connect. Check the permission and try again.")`. `days = buildDays(range, today, map)`, `buckets = dailyBuckets(DAY, days, goal)`.
   - **WEEK / MONTH:** `effectiveEnd = min(range.endInclusive, today)`; `map = stepsInRange(range.start, effectiveEnd)`. If empty → `Failure(...)`. `days = buildDays(DayRange(range.start, range.endInclusive), today, map)` so future days still render; `buckets = dailyBuckets(period, days, goal)`.
   - **YEAR:** `monthTotals = mutableMapOf<Int, Long>()`; for each month `1..12` whose start is not after today, call `totalSteps(monthStart, min(monthEnd, today))`. Month reads that fail return `0` (repository contract) — do not throw. Then `todayMap = stepsInRange(today, today)`; `todaySteps = todayMap[today] ?: 0L`, `todayHasData = todayMap.isNotEmpty()`. `buckets = monthlyBuckets(today, monthTotals, goal)`; `days = emptyList()`; `stats = yearStats(buckets, goal, today)`.
6. For DAY/WEEK/MONTH: `todaySteps = map[today] ?: 0L`; `todayHasData = map.containsKey(today)`; `stats = stats(days, goal, today)`.
7. `previousRange = previousRange(period, today)`; `previousTotal = healthRepository.totalSteps(context, previousRange.start, previousRange.endInclusive)`. This is one extra aggregate. (Always call it, including DAY, so Yesterday comparison works.)
8. `paceTarget = goal * stats.elapsedDays`.
9. `profile = runCatching { settingsManager.userProfile.first() }.getOrDefault(UserProfile())`.
10. Build `StepsHistory` with:
    - `totalDistanceKm = StepMetrics.distanceKm(stats.total, profile.heightCm, profile.isMale)`
    - `totalCalories = StepMetrics.calories(stats.total, profile.weightKg)`
    - `listRows = if (period == YEAR) monthListRows(buckets) else dayListRows(days, goal)`
    - `changePercent = changePercent(stats.total, previousTotal)`
    - `generatedAtMs = nowMs()`.
11. On cacheable success, store under the mutex. Return `Success(history)`.

On `withTimeoutOrNull` returning null: `Failure("Reading steps timed out. Try again.")`. Catch `CancellationException` and rethrow; catch other `Exception` and return `Failure(e.message ?: "Could not load steps")` (do not leak stack traces).

- [ ] **Step 4: Cache implementation**

Inside the class:

```kotlin
private data class CacheKey(val period: StepsPeriod, val today: LocalDate, val goal: Long)
private data class CacheEntry(val history: StepsHistory, val storedAtMs: Long)

private val cacheMutex = Mutex()
private val cache = object : LinkedHashMap<CacheKey, CacheEntry>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, CacheEntry>?) = size > 8
}

private suspend fun readCache(key: CacheKey): StepsHistory? = cacheMutex.withLock {
    val entry = cache[key] ?: return@withLock null
    if (nowMs() - entry.storedAtMs > cacheTtlMs) {
        cache.remove(key)
        null
    } else entry.history
}

private suspend fun writeCache(key: CacheKey, history: StepsHistory) = cacheMutex.withLock {
    cache[key] = CacheEntry(history, nowMs())
}

suspend fun invalidateCache() { cacheMutex.withLock { cache.clear() } }
```

`invalidateCache()` is `suspend` because the map is guarded by `cacheMutex`; call it from a coroutine (the ViewModel's `viewModelScope`) if it is used.

- [ ] **Step 5: Permission/SDK helpers**

```kotlin
suspend fun hasPermission(): Boolean = withContext(Dispatchers.IO) {
    healthRepository.getHealthPermissionState(context).getOrNull() == HealthPermissionState.GRANTED
}

suspend fun requestPermission(): Boolean = withContext(Dispatchers.IO) {
    runCatching { healthRepository.requestStepsPermission(context) }.getOrDefault(false)
}

suspend fun hasBackgroundReadPermission(): Boolean = withContext(Dispatchers.IO) {
    runCatching { healthRepository.hasBackgroundReadPermission(context) }.getOrDefault(false)
}

suspend fun requestBackgroundReadPermission(): Boolean = withContext(Dispatchers.IO) {
    runCatching { healthRepository.requestBackgroundReadPermission(context) }.getOrDefault(false)
}

suspend fun healthSdkStatus(): HealthSdkStatus = withContext(Dispatchers.IO) {
    runCatching { healthRepository.isSdkAvailable(context) }.getOrDefault(HealthSdkStatus.UNAVAILABLE)
}
```

- [ ] **Step 6: `StepMetricsTest.kt`**

1. Male 175 cm → stride `0.72625` m (delta `1e-6`); 10,000 steps → `7.2625` km.
2. Female 165 cm → stride `0.68145` m.
3. Invalid/zero/NaN height → 0.0; negative steps → 0.0.
4. Calories: 10,000 steps at 70 kg → `400` and matches `CalorieCalculator.stepsCalories(10_000, 70.0).roundToInt()`.
5. Zero/negative weight → 0.

- [ ] **Step 7: `StepsHistoryUseCasesTest.kt`**

Set up:
```kotlin
private val context: Context = mockk(relaxed = true)
private val healthRepository: HealthRepository = mockk()
private val settingsManager: SettingsManager = mockk()
private val useCases = StepsHistoryUseCases(healthRepository, settingsManager, context)

@Before fun setUp() {
    coEvery { healthRepository.getHealthPermissionState(any()) } returns
        com.daytoday.repository.Result.success(HealthPermissionState.GRANTED)
    every { settingsManager.goals } returns flowOf(listOf(GoalEntry("g", GoalType.STEPS_TODAY, 8_000.0)))
    every { settingsManager.userProfile } returns flowOf(UserProfile())
    useCases.cacheTtlMs = 0L
}
```

Tests (use `runBlocking`):
1. Permission denied → `PermissionMissing`, and `stepsInRange` is never called.
2. DAY success: `stepsInRange(today, today)` returns `mapOf(today to 8_432)`; outcome `Success`, `todaySteps == 8_432`, `paceTarget == 8_000`, `buckets.size == 1`.
3. WEEK: returns a Monday..today map; `days.size == 7`, future days `isFuture`, `stats.averagePerDay` excludes today.
4. WEEK empty map (granted) → `Failure`, not `Success`/zero.
5. Goal fallback: `goals = flowOf(emptyList())` → `goal == 8_000`.
6. Custom goal: `target = 12_000.0` → `goal == 12_000` and `paceTarget == 12_000 * elapsedDays`.
7. YEAR: `totalSteps` returns values by month (`coEvery { totalSteps(any(), any(), any()) } answers { ... }` based on `firstArg<LocalDate>().monthValue`); assert 12 buckets, current-month prorated goal, `stats.daysGoalMet == null`, `todaySteps` from the today read.
8. Cache: `cacheTtlMs = 60_000`; two loads in a row → `stepsInRange` called once; `load(forceRefresh = true)` → called twice. Mock `nowMs` to advance > TTL and assert a fresh read.
9. Timeout: `stepsInRange` `coAnswers { delay(10_000); ... }`; assert outcome is `Failure` within < 6 s for DAY (set a short wait; use `useCases` with day timeout, no new deps).
10. Cancellation from repository is rethrown (`assertThrows(CancellationException::class.java)`).
11. `previousTotal` zero/absent → `changePercent == null`.

Do not assert cache eviction order; it is an implementation detail.

- [ ] **Step 8: Verify S2**

```bat
cmd /c "gradlew.bat :composeApp:testDebugUnitTest --tests "com.daytoday.usecase.StepMetricsTest" --tests "com.daytoday.usecase.StepsHistoryUseCasesTest" --console=plain > composeApp\build\steps-s2-test.log 2>&1"
echo Exit code: %ERRORLEVEL%
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-s2-test.log' -Pattern 'FAILED|BUILD FAILED|tests completed' | Select-Object -Last 20"
```

Expected: all tests pass, including S1 (Gradle may re-run affected tests).

- [ ] **Step 9: Commit S2**

```bat
git add composeApp/src/androidMain/kotlin/com/daytoday/usecase/StepMetrics.kt composeApp/src/androidMain/kotlin/com/daytoday/usecase/StepsHistoryUseCases.kt composeApp/src/androidUnitTest/kotlin/com/daytoday/usecase/StepMetricsTest.kt composeApp/src/androidUnitTest/kotlin/com/daytoday/usecase/StepsHistoryUseCasesTest.kt
git diff --cached --stat
git commit -m "feat(steps): load period history from Health Connect with caching"
```

---

## Task S3: Custom Canvas goal ring + bar chart

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/steps/StepsGoalRing.kt`
- Create: `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/steps/StepsBarChart.kt`

**Interfaces:**
- Consumes: `StepsBucket`, `formatSteps`.
- Produces:
  - `@Composable fun StepsGoalRing(todaySteps: Long, goal: Long, modifier: Modifier = Modifier)`
  - `@Composable fun StepsBarChart(buckets: List<StepsBucket>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun StepsChartLabels(buckets: List<StepsBucket>, selectedIndex: Int, modifier: Modifier = Modifier)`

- [ ] **Step 1: `StepsGoalRing`**

`Box(contentAlignment = Center)` containing a `Canvas(Modifier.size(168.dp))` and a centered `Column`:
- `Text(formatSteps(todaySteps), headlineMedium, bold)`.
- `Text("of ${formatSteps(goal)}", bodySmall, onSurfaceVariant)`.
- If `todaySteps >= goal`, a small `Text("Goal reached", color = primary, labelSmall, bold)`.

Canvas:
- `val stroke = 14.dp.toPx()`.
- Background arc: `startAngle = -90f`, `sweepAngle = 360f`, `useCenter = false`, `style = Stroke(stroke, cap = StrokeCap.Round)`, color `primaryContainer`.
- Progress arc: sweep `360f * progress.coerceIn(0f, 1f)`, color `primary`, same style.
- If `goal <= 0`, progress is `0f`; still render the ring.
- Add `Modifier.semantics { contentDescription = "$todaySteps of $goal steps today" }` (use `formatSteps` for the description).

Imports: `androidx.compose.foundation.Canvas`, `androidx.compose.ui.graphics.drawscope.Stroke`, `androidx.compose.ui.graphics.StrokeCap`, `androidx.compose.ui.semantics.*`.

- [ ] **Step 2: `StepsBarChart`**

Composable signature with Material 3 colors read **before** the Canvas (Canvas draw lambda is not `@Composable`):

```kotlin
@Composable
fun StepsBarChart(
    buckets: List<StepsBucket>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val current = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val selectedWash = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val goalDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(buckets) {
                detectTapGestures { offset ->
                    val slot = size.width.toFloat() / buckets.size
                    if (slot > 0f) {
                        onSelect((offset.x / slot).toInt().coerceIn(0, buckets.lastIndex))
                    }
                }
            }
    ) {
        val slot = size.width / buckets.size
        val barWidth = (slot * 0.56f).coerceAtLeast(2f)
        val baseline = size.height - 2.dp.toPx()
        val topPad = 12.dp.toPx()
        val chartHeight = baseline - topPad

        // Over-goal bars must remain visible: scale to at least 1.0 goal, or the
        // largest bucket when the user exceeded the goal.
        val maxFraction = buckets.maxOf { bucket ->
            if (bucket.goal > 0L) bucket.steps.toFloat() / bucket.goal else 0f
        }.coerceAtLeast(1f)

        buckets.forEachIndexed { index, bucket ->
            val left = index * slot
            if (index == selectedIndex) {
                drawRoundRect(
                    color = selectedWash,
                    topLeft = Offset(left + 1.dp.toPx(), 0f),
                    size = Size(slot - 2.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            val fraction = if (bucket.goal > 0L) bucket.steps.toFloat() / bucket.goal else 0f
            val barHeight = (chartHeight * (fraction / maxFraction)).coerceIn(0f, chartHeight)
            if (barHeight > 0f) {
                drawRoundRect(
                    color = if (bucket.isCurrent) current else primary,
                    topLeft = Offset(left + (slot - barWidth) / 2f, baseline - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            } else {
                // hairline "empty" bar so a known zero is distinguishable from a gap
                drawRoundRect(
                    color = track,
                    topLeft = Offset(left + (slot - barWidth) / 2f, baseline - 1.dp.toPx()),
                    size = Size(barWidth, 1.dp.toPx()),
                )
            }
        }

        // dashed goal line at 1.0 normalized
        val goalY = baseline - chartHeight * (1f / maxFraction)
        drawLine(
            color = goalColor,
            start = Offset(0f, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = goalDash,
        )
    }
}
```

Notes:
- `PointerInputScope.size` is `IntSize`; if the compiler complains about `size.width.toFloat()`, use `this.size.width.toFloat()`.
- Keep the goal line only when at least one bucket has `goal > 0`.
- Add semantics on the Canvas: `contentDescription = "Steps chart, ${buckets.size} bars; selected ${buckets[selectedIndex].label}"` via `Modifier.semantics`.

- [ ] **Step 3: `StepsChartLabels`**

Render a `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween)` of labels chosen for density:
- ≤ 7 buckets: all labels.
- 28–31 (month): indices `0, 6, 13, 20, last` mapped to `bucket.label` (day numbers).
- 12 (year): every other month (`index % 2 == 0`), plus the current month.

Use `MaterialTheme.typography.labelSmall`, highlight the selected/current label with `primary` + `FontWeight.Bold`, others `onSurfaceVariant`. No `drawText`/`TextMeasurer`, so this stays simple and stable on Compose BOM 2024.06.

- [ ] **Step 4: Verify S3**

```bat
cmd /c "gradlew.bat :composeApp:compileDebugKotlinAndroid --console=plain > composeApp\build\steps-s3-compile.log 2>&1"
echo Exit code: %ERRORLEVEL%
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-s3-compile.log' -Pattern '^e: | error: |BUILD FAILED' | Select-Object -First 40"
```

Expected: no `e:` lines, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit S3**

```bat
git add composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/steps/StepsGoalRing.kt composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/steps/StepsBarChart.kt
git diff --cached --stat
git commit -m "feat(steps): add Compose canvas goal ring and bar chart"
```

---

## Task S4: `StepsDetailScreen` UI (states, permission, chart, stats, history)

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/steps/StepsDetailScreen.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/util/Formatters.kt` (add one formatter only; file is already dirty — stage hunks carefully)

**Interfaces:**
- Consumes: `StepsDetailViewModel` (S5 — if S4 is implemented before S5, code against the exact VM API below), `StepsUiState`, `StepsHistory`, `StepsPeriod`, chart/ring from S3, `DayToday*` components, `formatSteps`, `formatNumber`, `formatCalories`, `formatDistanceKm`.
- Produces: `@Composable fun StepsDetailScreen(viewModel: StepsDetailViewModel = hiltViewModel(), navController: NavController = rememberNavController())`.

Expected VM API (implemented in S5):
```kotlin
val uiState: StateFlow<StepsUiState>        // Loading | PermissionMissing | Error | Success
val period: StateFlow<StepsPeriod>
fun selectPeriod(period: StepsPeriod)
fun refresh()
fun onResume()
fun onPermissionResult(granted: Boolean)
fun requestPermission()
```

- [ ] **Step 1: Add `formatDistanceKm` to `Formatters.kt`**

Append next to `formatSteps`:

```kotlin
/** Distance estimate with up to two decimals, e.g. "7.26 km". */
fun formatDistanceKm(km: Double, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.2f km", if (km.isFinite()) km else 0.0)
```

Do not reformat or reorder the existing file. The worktree already has user edits in it; a later commit must use `git add -p`.

- [ ] **Step 2: Screen scaffold, permission launcher, lifecycle**

```kotlin
@Composable
fun StepsDetailScreen(
    viewModel: StepsDetailViewModel = hiltViewModel(),
    navController: NavController = rememberNavController(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()

    val permissions = remember {
        setOf(HealthPermission.getReadPermission(StepsRecord::class))
    }
    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onPermissionResult(
            granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "Steps",
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                StepsUiState.Loading -> LoadingOverlay("Loading steps...")
                StepsUiState.PermissionMissing -> StepsPermissionState(
                    onConnect = {
                        try { launcher.launch(permissions) }
                        catch (_: Exception) { viewModel.requestPermission() }
                    }
                )
                is StepsUiState.Error -> ErrorState(state.message, onRetry = { viewModel.refresh() })
                is StepsUiState.Success -> StepsContent(
                    history = state.history,
                    period = period,
                    onPeriodSelected = viewModel::selectPeriod,
                    onEditGoal = { navController.navigate(Screen.Goals.route) },
                )
            }
        }
    }
}
```

`StepsPermissionState` uses `EmptyState(icon = { Icon(Icons.Default.DirectionsWalk, ...) }, title = "Connect Health Connect", subtitle = "Allow step access to see your daily and weekly history. Nothing leaves your device.", action = { DayTodayButton(text = "Connect Health Connect", onClick = onConnect) })`.

- [ ] **Step 3: `StepsContent` layout**

`LazyColumn` with `contentPadding = PaddingValues(16.dp)`, `verticalArrangement = Arrangement.spacedBy(16.dp)`.

1. **Period selector** — `Row(horizontalArrangement = Arrangement.spacedBy(8.dp))` of four `FilterChip`s (`Day`, `Week`, `Month`, `Year`). `selected = period == p`, `onClick = { onPeriodSelected(p) }`.
2. **Today card** (`DayTodayCard`, full width) — `Row`: `StepsGoalRing(todaySteps, goal)` on the left; on the right a `Column`:
   - `Text("Today", titleMedium)`.
   - `Text(formatSteps(history.todaySteps), headlineSmall, bold)`.
   - `Text("${percentage(today, goal)}% of ${formatSteps(goal)} goal", bodySmall, onSurfaceVariant)`.
   - If `!history.todayHasData`: `Text("No data from Health Connect today", bodySmall, color = error)`.
   - Inline `TextButton(onClick = onEditGoal) { Text("Edit goal") }`.
3. **Period summary card** — title `"This week"` / `"This month"` / `"This year"` / `"Today"`.
   - `Text("${formatSteps(stats.total)} / ${formatSteps(history.paceTarget)} steps")` where the denominator is the pace target.
   - A `LinearProgressIndicator(progress = (total / paceTarget).coerceIn(0f,1f))`.
   - `Text("${pacePercent}% of target (goal × ${stats.elapsedDays} days)", bodySmall)`.
   - If `changePercent != null`: `"${if (change >= 0) "+" else ""}$change% vs previous ${periodLabel.lowercase()}"` in primary (green-ish not required) or error for negative; if null: `"No previous ${periodLabel.lowercase()} data to compare"`.
   - Distance/calories row: `"≈ ${formatDistanceKm(history.totalDistanceKm)}"` and `"≈ ${formatCalories(history.totalCalories)}"` with a shared caption `"Estimates from height and weight"`.
4. **Chart card** — `Text("Steps trend", titleLarge)`. `StepsBarChart(buckets, selectedIndex, onSelect)`, `StepsChartLabels(...)`. Selection resets to the current bucket when `history` changes:
   ```kotlin
   var selectedIndex by remember(history) {
       mutableIntStateOf(history.buckets.indexOfFirst { it.isCurrent }.takeIf { it >= 0 } ?: history.buckets.lastIndex.coerceAtLeast(0))
   }
   ```
   Below the chart, show the selected bucket's detail in a tinted `Row`:
   - Day bucket: `"${bucket.label} · ${formatSteps(bucket.steps)} / ${formatSteps(bucket.goal)}"` + percentage.
   - Year bucket: `"${bucket.label} · ${formatSteps(bucket.steps)} steps · goal ${formatSteps(bucket.goal)}"`.
   - If `!bucket.hasData`: append `" · no data"` instead of pretending zero.
5. **Stats grid** — two rows of two `DayTodayCard`s (or one card with a 2×2 `Row`/`Column`):
   - `"Average"`: `formatSteps(stats.averagePerDay)` + `"per day"` (`"per day*"` for YEAR with a footnote `"* includes today"`).
   - `"Best"`: if `stats.bestDate != null` → `formatSteps(stats.bestSteps ?: 0)`, sublabel `formatStatsBestLabel(period, stats.bestDate)` (`"Mon, Oct 20"` for day periods, `"March"` for YEAR`); else `"—"`.
   - `"Total"`: `formatSteps(stats.total)`.
   - `"Goal met"`: if `stats.daysGoalMet != null` → `"${stats.daysGoalMet} / ${stats.elapsedDays} days"`; else `"Best month: ${stats.bestSteps?.let(::formatSteps) ?: "—"}"`.
6. **Recent list** — title `if (period == YEAR) "Recent months" else "Recent days"`. Rows = `history.listRows.takeLast(if (period == YEAR) 6 else 7).reversed()`. Each row:
   - `Row(verticalAlignment = CenterVertically)`: label (`Today` bold if `isCurrent`), spacer, `if (hasData) formatSteps(steps) else "—"`.
   - A slim `LinearProgressIndicator(progress = (steps / goal).coerceIn(0f,1f))`, `trackColor = surfaceVariant`; `"—"` rows show a disabled/empty track.
   - For no rows: `Text("No step history yet for this period.", bodySmall)`.
7. **Empty-period note** — if `stats.total == 0L`: `DayTodayCard` with `Text("No steps recorded in this period. Samsung Health may not have synced yet.")` and a `TextButton("Refresh")`.
8. Bottom spacer `24.dp` so the last row clears the app's bottom navigation bar.

- [ ] **Step 4: Helpers in the same file**

- `periodTitle(period): String` = "Today" / "This week" / "This month" / "This year".
- `formatStatsBestLabel(period, date)`:
  - YEAR: `date.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))`.
  - else: `date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))`.
- `percentOf(steps, goal) = StepsAggregator.percentage(steps, goal)`; do not duplicate the math.
- All text is English, matching the rest of the app.

- [ ] **Step 5: Compile-verify S4**

Because S5 does not exist yet, compile only after S5 (or temporarily stub the VM). If executing tasks strictly in order, defer this verification to S5 Step 5. If S5 is done first, run:

```bat
cmd /c "gradlew.bat :composeApp:compileDebugKotlinAndroid --console=plain > composeApp\build\steps-s4-compile.log 2>&1"
echo Exit code: %ERRORLEVEL%
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-s4-compile.log' -Pattern '^e: | error: |BUILD FAILED' | Select-Object -First 40"
```

- [ ] **Step 6: Commit S4 (after S5 compiles)**

Stage the new screen and only the formatter hunk:

```bat
git add composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/steps/StepsDetailScreen.kt
git add -p composeApp/src/androidMain/kotlin/com/daytoday/util/Formatters.kt
git diff --cached --stat
```

In `git add -p`, accept **only** the `formatDistanceKm` hunk. If the file's other hunks are interleaved and cannot be isolated safely, leave `Formatters.kt` unstaged and note it for the user instead of risking their work.

```bat
git commit -m "feat(steps): add steps detail screen with chart, stats and history"
```

---

## Task S5: `StepsDetailViewModel` (Hilt, period switching, permission lifecycle)

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/steps/StepsDetailViewModel.kt`

**Interfaces:**
- Consumes: `StepsHistoryUseCases`, `StepsLoadOutcome`, `StepsPeriod`, `StepsHistory`.
- Produces: `StepsDetailViewModel` + `sealed interface StepsUiState` exactly as used by S4.

- [ ] **Step 1: State + fields**

```kotlin
package com.daytoday.ui.screen.steps

sealed interface StepsUiState {
    data object Loading : StepsUiState
    data object PermissionMissing : StepsUiState
    data class Error(val message: String) : StepsUiState
    data class Success(val history: StepsHistory) : StepsUiState
}

@HiltViewModel
class StepsDetailViewModel @Inject constructor(
    private val useCases: StepsHistoryUseCases,
) : ViewModel() {
    private val _uiState = MutableStateFlow<StepsUiState>(StepsUiState.Loading)
    val uiState: StateFlow<StepsUiState> = _uiState.asStateFlow()

    private val _period = MutableStateFlow(StepsPeriod.DAY)
    val period: StateFlow<StepsPeriod> = _period.asStateFlow()

    private var loadJob: Job? = null

    init { load(StepsPeriod.DAY) }
}
```

- [ ] **Step 2: Load with cancellation**

```kotlin
fun selectPeriod(period: StepsPeriod) {
    if (period == _period.value && _uiState.value is StepsUiState.Success) return
    _period.value = period
    load(period)
}

fun refresh() = load(_period.value, forceRefresh = true)

fun onPermissionResult(granted: Boolean) {
    if (granted) refresh() else _uiState.value = StepsUiState.PermissionMissing
}

fun requestPermission() {
    viewModelScope.launch {
        if (useCases.requestPermission()) refresh() else _uiState.value = StepsUiState.PermissionMissing
    }
}

fun onResume() {
    viewModelScope.launch {
        if (useCases.hasPermission()) {
            if (_uiState.value !is StepsUiState.Success) refresh()
        } else if (_uiState.value is StepsUiState.Success) {
            _uiState.value = StepsUiState.PermissionMissing
        }
    }
}

private fun load(period: StepsPeriod, forceRefresh: Boolean = false) {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
        _uiState.value = StepsUiState.Loading
        _uiState.value = when (val outcome = useCases.load(period, forceRefresh)) {
            is StepsLoadOutcome.Success -> StepsUiState.Success(outcome.history)
            StepsLoadOutcome.PermissionMissing -> StepsUiState.PermissionMissing
            is StepsLoadOutcome.Failure -> StepsUiState.Error(outcome.message)
        }
    }
}
```

- [ ] **Step 3: Behavior rules**

- Rapid chip taps must not show stale results: `loadJob?.cancel()` + the use case rethrows `CancellationException` makes this safe.
- Permission missing is a first-class state (not an error) so the CTA is shown.
- `onResume()` must not reload a successful screen on every resume; it only repairs permission state.
- Do not cache UI state in the VM; the use case owns the TTL cache.
- Keep `selectPeriod` idempotent for the same period when already successful (prevents chip double-tap refetches).

- [ ] **Step 4: Verify S5 + S4**

```bat
cmd /c "gradlew.bat :composeApp:compileDebugKotlinAndroid --console=plain > composeApp\build\steps-s5-compile.log 2>&1"
echo Exit code: %ERRORLEVEL%
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-s5-compile.log' -Pattern '^e: | error: |BUILD FAILED' | Select-Object -First 40"
```

Expected: `BUILD SUCCESSFUL`; no unresolved references between the screen and VM.

- [ ] **Step 5: Commit S5**

```bat
git add composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/steps/StepsDetailViewModel.kt
git diff --cached --stat
git commit -m "feat(steps): add steps detail view model"
```

---

## Task S6: Wire Home card, route, NavHost, BottomNav

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/Screen.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/NavHost.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/BottomNavHost.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/HomeScreen.kt`

**Interfaces:**
- Consumes: `StepsDetailScreen` from S4.
- Produces: route `"StepsDetail"`; Home Steps card navigates to it; bottom bar keeps Home selected.

- [ ] **Step 1: Add the route**

In `Screen.kt`, after `WorkoutProgress` (or next to Home-related entries):

```kotlin
data object StepsDetail : Screen {
    override val route: String = "StepsDetail"
}
```

- [ ] **Step 2: Register in `NavHost.kt`**

Add `import com.daytoday.ui.screen.steps.StepsDetailScreen` and:

```kotlin
composable(Screen.StepsDetail.route) {
    StepsDetailScreen(navController = navController)
}
```

Place it after the `WorkoutProgress` block. No arguments.

- [ ] **Step 3: Keep Home selected in `BottomNavHost.kt`**

Extend the existing `selected` expression (L48–54) inside the `items.forEach`:

```kotlin
val selected = currentRoute == route || (
    item.screen == Screen.Home && currentRoute == Screen.StepsDetail.route
) || (
    item.screen == Screen.NbaScoreboard && (
        currentRoute == Screen.NbaNews.route ||
        currentRoute == Screen.NbaInjuries.route ||
        currentRoute.startsWith(Screen.NbaGameDetail.route)
    )
)
```

Do not add a new bottom tab.

- [ ] **Step 4: Make the Home Steps card clickable**

In `HomeScreen.kt`:

1. Extend `QuickStat` with optional fields (keep existing positional constructors valid):

```kotlin
data class QuickStat(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val color: Color,
    val hint: String? = null,
    val onClick: (() -> Unit)? = null,
)
```

2. In `QuickStatCard`, under the title `Text`, render the hint when present:

```kotlin
stat.hint?.let {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = it,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
}
```

3. Update the Steps entry at L356–361:

```kotlin
QuickStat(
    title = "Steps",
    value = formatSteps(stats.steps),
    icon = Icons.Default.DirectionsWalk,
    color = tertiaryStatColor,
    hint = "Tap for history",
    onClick = { onNavigate(Screen.StepsDetail.route) },
),
```

4. In the `items(...)` block at L381–383, make the card clickable only when a handler exists:

```kotlin
) { stat ->
    DayTodayCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (stat.onClick != null) Modifier.clickable { stat.onClick.invoke() }
                else Modifier
            ),
    ) { QuickStatCard(stat) }
}
```

Use `stat.onClick?.invoke()` instead if the smart cast is rejected (captured `stat` is a local `val`, so it should be fine). Keep all other cards unchanged.

5. Do not alter the existing permission gate: when permission is missing, the Steps card is not rendered anyway (the connect card replaces the grid). The detail screen has its own gate for deep links/route restoration.

- [ ] **Step 5: Compile + run unit tests**

```bat
cmd /c "gradlew.bat :composeApp:compileDebugKotlinAndroid --console=plain > composeApp\build\steps-s6-compile.log 2>&1"
echo Exit code: %ERRORLEVEL%
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-s6-compile.log' -Pattern '^e: | error: |BUILD FAILED' | Select-Object -First 40"
```

Then:

```bat
cmd /c "gradlew.bat :composeApp:testDebugUnitTest --console=plain > composeApp\build\steps-s6-test.log 2>&1"
echo Exit code: %ERRORLEVEL%
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-s6-test.log' -Pattern 'FAILED|BUILD FAILED' | Select-Object -First 40"
```

Expected: compile clean; all existing + new JVM tests pass. (Some pre-existing tests may require Robolectric; do not "fix" them by editing unrelated code.)

- [ ] **Step 6: Commit S6**

`Screen.kt` and `NavHost.kt` and `BottomNavHost.kt` are task-owned changes in files that may already be dirty. Check first:

```bat
git diff -- composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/Screen.kt composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/NavHost.kt composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/BottomNavHost.kt composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/HomeScreen.kt
```

Stage hunks, never whole dirty files blindly:

```bat
git add -p composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/Screen.kt
git add -p composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/NavHost.kt
git add -p composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/BottomNavHost.kt
git add -p composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/HomeScreen.kt
git diff --cached --stat
git commit -m "feat(steps): open steps history from the home card"
```

If `HomeScreen.kt`'s pre-existing user hunks overlap the Steps card lines and cannot be isolated, stop and report it rather than committing the user's unrelated changes. The implementation is still valid uncommitted.

---

## Task S7: End-to-end verification + manual QA

**Files:**
- No source files. Verification logs go to `composeApp/build/` (already git-ignored via `build/`).

**Interfaces:**
- Consumes: everything above.
- Produces: evidence that the Kotlin Android target compiles and the JVM test suite passes, plus a manual QA checklist result.

- [ ] **Step 1: Clean compile (required)**

```bat
if not exist composeApp\build mkdir composeApp\build
cmd /c "gradlew.bat :composeApp:compileDebugKotlinAndroid --console=plain > composeApp\build\steps-detail-compile.log 2>&1"
echo Exit code: %ERRORLEVEL%
```

Inspect for Kotlin errors:

```bat
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-detail-compile.log' -Pattern '^e: | error: |BUILD FAILED' | Select-Object -First 50"
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-detail-compile.log' -Pattern 'BUILD SUCCESSFUL'"
```

Required: exit code `0`, first query prints nothing, second prints `BUILD SUCCESSFUL`.

- [ ] **Step 2: Full unit test suite (required)**

```bat
cmd /c "gradlew.bat :composeApp:testDebugUnitTest --console=plain > composeApp\build\steps-detail-test.log 2>&1"
echo Exit code: %ERRORLEVEL%
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-detail-test.log' -Pattern 'FAILED|BUILD FAILED' | Select-Object -First 50"
powershell -NoProfile -Command "Select-String -Path 'composeApp/build/steps-detail-test.log' -Pattern 'BUILD SUCCESSFUL'"
```

If a test fails only because of an unrelated pre-existing test, report the exact failing test and do not modify unrelated code.

- [ ] **Step 3: Focused new tests (required)**

```bat
cmd /c "gradlew.bat :composeApp:testDebugUnitTest --tests "com.daytoday.usecase.StepsAggregatorTest" --tests "com.daytoday.usecase.StepMetricsTest" --tests "com.daytoday.usecase.StepsHistoryUseCasesTest" --console=plain > composeApp\build\steps-detail-focused.log 2>&1"
echo Exit code: %ERRORLEVEL%
```

- [ ] **Step 4: Check the final diff for contamination**

```bat
git status --short
git diff --stat
```

Confirm:
- Only expected files changed.
- No server/client/Prisma/network/Room files were touched.
- The pre-existing modified files still contain their user work (compare with the baseline list above; do not revert anything).
- `composeApp/build/*.log` does not appear in `git status` (build output ignored).

- [ ] **Step 5: Manual QA checklist (device with Samsung Health + Health Connect)**

- [ ] Home Steps card shows "Tap for history" and opens the Steps screen.
- [ ] Bottom bar remains visible and **Home** stays selected on the Steps screen.
- [ ] Back arrow returns to Home; system back also works.
- [ ] Today ring matches the Home Steps card value (same `todaySteps` source; allow for a sync between reads).
- [ ] Day / Week / Month / Year chips load independently; rapid tapping does not flash a stale period.
- [ ] Week bars start on Monday; today's bar is highlighted; future days are empty.
- [ ] Month chart shows day numbers; the selected day detail updates on tap.
- [ ] Year chart has 12 bars, future months empty, current month highlighted.
- [ ] Goal line is dashed; bars above goal still render (chart rescales).
- [ ] Goal comes from Goals → "Steps today"; changing it in Goals and returning updates this screen (after refresh/resume).
- [ ] Revoke step permission in Health Connect settings, then resume: the Connect CTA appears (not a stale chart).
- [ ] Grant permission from the CTA: data loads without leaving the screen.
- [ ] Airplane mode: everything still works (no network calls).
- [ ] Distance/calories are labeled as estimates and change when height/weight/gender are changed in Settings.
- [ ] A day with no Health Connect data shows `—` in the recent list, not `0`.
- [ ] Rotate the device: no crash; selected period survives the ViewModel.

- [ ] **Step 6: Final report**

Report:
1. Absolute path of this plan.
2. Files changed/created.
3. Compile/test exit codes and the `BUILD SUCCESSFUL` evidence lines.
4. Any manual QA items not run.
5. Any user-work hunks deliberately left uncommitted.

No commit is required for S7 (logs are ignored). If the worker wants a final commit, stage only new source files plus verified task hunks; never `git add -A`.

---

## Deferred (explicitly out of scope)

- Samsung's **Hours** view (hourly buckets) — no hourly read path exists; adding one would touch `HealthRepository` and possibly Health Connect query cost.
- Step goal editing on this screen (link to Goals instead).
- Background sync, notification changes, widgets, streak/achievement badges, social comparison.
- Per-source breakdown (phone vs watch vs Samsung Health), manual entries, or writing anything back to Health Connect.
- Export/CSV, share cards, animated chart transitions, and haptics.
- Distance from an actual measured stride length (we only have height/gender).
- A separate chart for calories/distance by period.

## Risks / Open Questions

1. **Month load cost.** `stepsInRange` performs one Health Connect aggregate per day. A 31-day month is 31 sequential calls, bounded by the 15 s timeout. If devices are slow, the screen will show the timeout error. The cache makes repeat visits instant. A future optimization is a batched aggregate with `groupBy`/bucket support, but that requires changing `HealthRepository` and is out of scope.
2. **Year accuracy/failure ambiguity.** `totalSteps` returns `0` for both "no data" and "read failure", so a failed month is indistinguishable from a zero month. We mark months as `hasData` only when the call is part of an elapsed month, but a real failure still looks like zero. The screen offers Refresh; this is the honest limit of the current repository API.
3. **Single-day ambiguity.** For DAY, an empty map is treated as a failure. A genuinely empty day should still come back as `mapOf(today to 0)` on a successful read. If Health Connect returns an empty map for a valid no-data day on some OEM build, the user sees an error instead of `0`. Verify on the device during QA; if it occurs, relax DAY to `Success` with `todayHasData=false` and revisit the WEEK/MONTH rule.
4. **Average rule.** Average excludes today for daily-data periods, which means on a Monday it equals today's steps (no completed days). YEAR average includes today because monthly buckets cannot split a partial day. Both are documented in code and in the UI footnote; decide during QA whether YEAR should instead read today separately and subtract it.
5. **Goal default mismatch.** Samsung Health's default is 6,000; this app's `STEPS_TODAY` default is 8,000. We intentionally keep 8,000 so Home, Goals, and the detail screen agree. If product wants 6,000, change `GoalType.STEPS_TODAY.defaultValue` in a separate change and update its tests.
6. **Pace target semantics.** The summary compares total steps to `dailyGoal × elapsedDays`, including today. A user checking at 9 a.m. will appear "behind" because today's goal is counted as a full day. This matches Samsung's daily-goal mental model but may be worth softening with "so far" copy after user testing.
7. **Chart density.** A 31-bar month chart on a narrow phone gives ~10 dp per slot. Bars remain tappable (tap maps to the nearest slot) but labels are sparse. If QA finds it unusable, switch MONTH to weekly buckets (ISO weeks) — that changes aggregator semantics, so decide before implementing.
8. **Dirty worktree.** `HomeScreen.kt` and `Formatters.kt` already contain user edits. A subagent that stages whole files will commit unrelated work. The hunk-level instructions are mandatory; when in doubt, leave the file uncommitted and report it.

## Self-Review (coverage vs. requirements)

- **Samsung-like today card + goal ring:** S3 `StepsGoalRing`, S4 today card, goal from `GoalType.STEPS_TODAY`.
- **Day/Week/Month/Year:** S1 ranges/buckets, S2 loading, S4 chips.
- **Bar chart with goal line, today highlight, tap-to-select:** S3 Canvas + S4 selection state.
- **Stats (average/best/total/goal met):** S1 `stats`/`yearStats`, S4 stats grid.
- **Recent-days list:** S1 `listRows`, S4 recent list.
- **Loading / no-permission / error / empty states:** S4 `LoadingOverlay`, `StepsPermissionState`, `ErrorState`, empty-period card.
- **Hilt ViewModel + caching:** S2 TTL/LRU cache, S5 VM.
- **Pure aggregation unit tests:** S1 and S2 test tasks.
- **Read-only, offline, no new permissions/dependencies:** Global Constraints + load code only calls existing repository reads.
- **Partial data honesty:** `hasData`/`isFuture`, `—` rows, `Failure` on empty maps, Year ambiguity documented.
- **Home → detail wiring:** S6 route, NavHost, clickable Steps card, bottom-bar selection.
- **Preserve user work:** Global Constraints, S4/S6 hunk-level staging, S7 contamination check.
