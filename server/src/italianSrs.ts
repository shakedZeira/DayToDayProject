export interface SrsState {
  ease: number;
  intervalDays: number;
  repetitions: number;
}

export const INITIAL_SRS: SrsState = { ease: 2.5, intervalDays: 0, repetitions: 0 };
const MIN_EASE = 1.3;

/** SM-2: quality 0..5; <3 resets repetitions and schedules the item again tomorrow. */
export function sm2(state: SrsState, quality: number): SrsState {
  const q = Math.max(0, Math.min(5, Math.round(quality)));
  const ease = Math.max(MIN_EASE, state.ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)));
  if (q < 3) return { ease, intervalDays: 1, repetitions: 0 };
  const repetitions = state.repetitions + 1;
  const intervalDays =
    repetitions === 1 ? 1 : repetitions === 2 ? 6 : Math.max(1, Math.round(state.intervalDays * ease));
  return { ease, intervalDays, repetitions };
}

export function dueDate(from: Date, intervalDays: number): Date {
  return new Date(from.getTime() + intervalDays * 86_400_000);
}
