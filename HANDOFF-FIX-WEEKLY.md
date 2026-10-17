# HANDOFF: Fix "This Week" Progress Showing 0

## Context
Project: DayToDayProject (KMP Compose Android + Node/Express+Prisma + React client + shared TS)
Phone connected via USB.

Graphify already built (3275 nodes / 8024 edges / 182 communities). Other tasks completed by subagents (Italian writing exercises removed, daily/weekly notifications added, NBA injuries deduped, PDF tab removed). 

The "fix this week progress showing 0" task was launched multiple times but subagents got cancelled/stuck. This handoff gives a concrete starting point.

## Observed Symptoms
- Goals tab: thisWeekCount = 0 everywhere
- Workout "This Week": thisWeekWorkouts = 0 everywhere
- Other weekly indicators also 0 despite having data

## Suspects (from grep)
- server/src/goals.ts: `computeThisWeekCount(goal)` used by GET /api/goals (see line 45: `thisWeekCount: computeThisWeekCount(goal),`)
- server/src/progression.ts: `thisWeekWorkouts = await prisma.workout.count({ where: { userId, createdAt: { gte: weekStart } } })` (line ~120)
- client/src/Goals.tsx, Analytics.tsx read server values
- composeApp/.../ui/screen/goals/GoalsScreen.kt and .../workout/WorkoutProgressScreen.kt display weekly values (Android)

## Tests related
- server/src/goals.test.ts: expects thisWeekCount behavior (0 initially, increments on checkoff, decrements on undo)
- server/src/italianPath.test.ts: checks thisWeekCount for Italian goals
- Also italianPathGrade.test.ts still passes offline

## Investigation Plan
1. **Find week boundary logic**
   - Look up `computeThisWeekCount` implementation in server/src/goals.ts (and any helpers). Check start of week: is it Sunday or Monday? Is it locale-dependent?
   - In progression.ts, how is `weekStart` computed? `startOfWeek(new Date())` from date-fns/lodash? Check import and options (weekStartsOn).
   - Check timezone: are we using UTC (Date.now(), new Date().toISOString()) vs local time? Timestamps in DB (Prisma DateTime) — how stored?

2. **Compare with how data is stored**
   - Goal checkoffs: look at Goal/GoalCheckoff schema in prisma/schema.prisma and how createdAt/set. 
   - Workouts: Workout model createdAt.
   - In tests, "this week" is computed relative to test time — maybe the implementation assumes UTC midnight start but DB has local times? Or uses `Date()` in server environment (UTC in Node) vs client expectations.

3. **Android side**
   - GoalsScreen.kt and WorkoutProgressScreen.kt: do they show server-provided thisWeekCount/thisWeekWorkouts (from API) or recompute locally? If they recompute, their week start might differ from server.

## Fix
Prefer fixing at the **server data layer** (computeThisWeekCount, progression weekStart) so both web and Android benefit. Common issues:
- Using `new Date()` and taking local midnight but filtering DB rows that are stored as UTC? Or vice versa.
- `startOfWeek` defaulting to Sunday in some libs, Monday in others — but app might expect Monday? Check existing behavior/tests.
- Off-by-one around timezone (e.g. server in UTC, user in different TZ).

## Verification
- Run server tests: `npm run test --workspace server` (or cd server && npx vitest run). Focus on goals.test.ts and italianPath.test.ts.
- If Android code changed: `gradlew.bat :composeApp:compileDebugKotlin`
- Don't commit; don't touch Italian exercises, NBA injuries, notifications, PDF tab.

## Quick grep to continue
```bash
grep -rn "computeThisWeekCount" server/src
grep -rn "startOfWeek\|weekStart\|thisWeek" server/src/goals.ts server/src/progression.ts
grep -rn "thisWeekCount\|thisWeekWorkouts" composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/goals composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/workout
```

## Files to inspect first
- server/src/goals.ts
- server/src/progression.ts
- server/src/shared.d.ts (type of Goal)
- composeApp/.../GoalsScreen.kt
- composeApp/.../WorkoutProgressScreen.kt
- prisma/schema.prisma (Goal, GoalCheckoff, Workout models)
