import { Prisma } from "@prisma/client";
import { prisma } from "./db";

/**
 * DB-side aggregations shared by the digest, progress and weekly review endpoints.
 *
 * Timestamps are stored as UTC `timestamp(3)`; "days" are the server's local calendar days
 * (same semantics as streak.ts / digest.ts), so day bucketing is done in Postgres with
 * `AT TIME ZONE <server IANA zone>` instead of loading every row into Node.
 */

export function serverTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
}

/** Local wall-clock expression for a UTC `timestamp` column. */
function localTs(column: Prisma.Sql, tz: string): Prisma.Sql {
  return Prisma.sql`((${column} AT TIME ZONE 'UTC') AT TIME ZONE ${tz})`;
}

function keyToLocalNoon(key: string): Date {
  const [y, m, d] = key.split("-").map(Number);
  return new Date(y, m - 1, d, 12);
}

/** Distinct local days (as local-noon Dates, ready for currentStreak) with Italian activity. */
export async function italianActiveDays(ownerId: string): Promise<Date[]> {
  const tz = serverTimeZone();
  const rows = await prisma.$queryRaw<{ d: string }[]>`
    SELECT DISTINCT to_char(${localTs(Prisma.sql`"createdAt"`, tz)}, 'YYYY-MM-DD') AS d
      FROM "PracticeLog" WHERE "ownerId" = ${ownerId}
    UNION
    SELECT DISTINCT to_char(${localTs(Prisma.sql`"completedAt"`, tz)}, 'YYYY-MM-DD') AS d
      FROM "Lesson" WHERE "ownerId" = ${ownerId} AND "completedAt" IS NOT NULL`;
  return rows.map((r) => keyToLocalNoon(r.d));
}

/** Distinct local days on which the user completed a task (any status, completedAt set). */
export async function taskCompletionDays(ownerId: string): Promise<Date[]> {
  const tz = serverTimeZone();
  const rows = await prisma.$queryRaw<{ d: string }[]>`
    SELECT DISTINCT to_char(${localTs(Prisma.sql`"completedAt"`, tz)}, 'YYYY-MM-DD') AS d
      FROM "Task" WHERE "ownerId" = ${ownerId} AND "completedAt" IS NOT NULL`;
  return rows.map((r) => keyToLocalNoon(r.d));
}

export function localDayKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Count of DONE tasks per local day between two local day starts [start, end). Missing days are 0. */
export async function doneTasksPerDay(ownerId: string, start: Date, end: Date): Promise<Map<string, number>> {
  const tz = serverTimeZone();
  const rows = await prisma.$queryRaw<{ d: string; n: bigint }[]>`
    SELECT to_char(${localTs(Prisma.sql`"completedAt"`, tz)}, 'YYYY-MM-DD') AS d, COUNT(*) AS n
      FROM "Task"
     WHERE "ownerId" = ${ownerId} AND "status" = 'DONE'
       AND "completedAt" >= ${start} AND "completedAt" < ${end}
     GROUP BY 1`;
  return new Map(rows.map((r) => [r.d, Number(r.n)]));
}

/** Calories per local day (grams/100 * caloriesPer100) between [start, end). Unrounded sums. */
export async function caloriesPerDay(ownerId: string, start: Date, end: Date): Promise<Map<string, number>> {
  const tz = serverTimeZone();
  const rows = await prisma.$queryRaw<{ d: string; kcal: number }[]>`
    SELECT to_char(${localTs(Prisma.sql`m."date"`, tz)}, 'YYYY-MM-DD') AS d,
           COALESCE(SUM(m."grams" / 100.0 * f."caloriesPer100"), 0)::float8 AS kcal
      FROM "MealLog" m JOIN "Food" f ON f."id" = m."foodId"
     WHERE m."ownerId" = ${ownerId} AND m."date" >= ${start} AND m."date" < ${end}
     GROUP BY 1`;
  return new Map(rows.map((r) => [r.d, r.kcal]));
}

/** Total rounded kcal for [start, end). */
export async function caloriesBetween(ownerId: string, start: Date, end: Date): Promise<number> {
  const rows = await prisma.$queryRaw<{ kcal: number }[]>`
    SELECT COALESCE(SUM(m."grams" / 100.0 * f."caloriesPer100"), 0)::float8 AS kcal
      FROM "MealLog" m JOIN "Food" f ON f."id" = m."foodId"
     WHERE m."ownerId" = ${ownerId} AND m."date" >= ${start} AND m."date" < ${end}`;
  return Math.round(rows[0]?.kcal ?? 0);
}

/** Distinct local days with a workout in [start, end). */
export async function workoutDays(ownerId: string, start: Date, end: Date): Promise<string[]> {
  const tz = serverTimeZone();
  const rows = await prisma.$queryRaw<{ d: string }[]>`
    SELECT DISTINCT to_char(${localTs(Prisma.sql`"date"`, tz)}, 'YYYY-MM-DD') AS d
      FROM "Workout"
     WHERE "ownerId" = ${ownerId} AND "date" >= ${start} AND "date" < ${end}
     ORDER BY 1`;
  return rows.map((r) => r.d);
}

/** Recurring = recurrence set and not "none". */
export const RECURRING: Prisma.TaskWhereInput = {
  AND: [{ recurrence: { not: null } }, { recurrence: { not: "none" } }]
};
