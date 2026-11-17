export const DEFAULT_PLATES_KG = [25, 20, 15, 10, 5, 2.5, 1.25];
export const DEFAULT_BAR_KG = 20;

export interface PlateResult {
  /** Plates for ONE side of the bar, heaviest first. */
  perSide: { plate: number; count: number }[];
  /** Weight actually loadable with the given plates (bar + 2 * per-side plates). */
  achievableKg: number;
  /** targetKg - achievableKg (> 0 means the target can't be hit exactly). */
  remainderKg: number;
  /** false when the target is lighter than the bar. */
  valid: boolean;
}

/** Greedy plate loading. Plates are the available denominations (any order). */
export function calculatePlates(
  targetKg: number,
  barKg: number = DEFAULT_BAR_KG,
  plates: number[] = DEFAULT_PLATES_KG
): PlateResult {
  if (!Number.isFinite(targetKg) || !Number.isFinite(barKg) || targetKg < barKg) {
    return { perSide: [], achievableKg: Math.max(barKg, 0), remainderKg: 0, valid: false };
  }
  const denoms = [...new Set(plates.filter((p) => p > 0))].sort((a, b) => b - a);
  // work in grams to avoid float drift (1.25kg plates)
  let left = Math.round(((targetKg - barKg) / 2) * 1000);
  const perSide: PlateResult["perSide"] = [];
  let loaded = 0;
  for (const p of denoms) {
    const g = Math.round(p * 1000);
    const count = Math.floor(left / g);
    if (count > 0) {
      perSide.push({ plate: p, count });
      left -= count * g;
      loaded += count * g;
    }
  }
  const achievableKg = barKg + (loaded * 2) / 1000;
  return { perSide, achievableKg, remainderKg: Math.round((targetKg - achievableKg) * 1000) / 1000, valid: true };
}
