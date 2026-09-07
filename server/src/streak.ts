export function dayKey(d: Date): number {
  return Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
}

export function currentStreak(dates: Date[], now: Date = new Date()): number {
  const days = new Set<number>(dates.map(dayKey));
  let cursor = dayKey(now);
  let streak = 0;
  while (days.has(cursor)) {
    streak += 1;
    cursor -= 86_400_000;
  }
  return streak;
}
