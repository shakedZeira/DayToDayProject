export type Groups = (number | null | undefined)[];

/** Toggles "superset with the previous exercise" at idx; returns a new groups array. */
export function toggleLinkWithPrevious(groups: Groups, idx: number): (number | null)[] {
  const next = groups.map((g) => g ?? null);
  if (idx <= 0 || idx >= next.length) return next;
  if (next[idx] !== null && next[idx] === next[idx - 1]) {
    next[idx] = null; // unlink
    return normalizeGroups(next);
  }
  let g = next[idx - 1];
  if (g === null) {
    g = Math.max(0, ...next.map((x) => x ?? 0)) + 1;
    next[idx - 1] = g;
  }
  next[idx] = g;
  return normalizeGroups(next);
}

/** Drops groups with a single member (not a superset) and renumbers the rest 1..n in order. */
export function normalizeGroups(groups: Groups): (number | null)[] {
  const counts = new Map<number, number>();
  for (const g of groups) if (g != null) counts.set(g, (counts.get(g) ?? 0) + 1);
  const remap = new Map<number, number>();
  return groups.map((g) => {
    if (g == null || (counts.get(g) ?? 0) < 2) return null;
    if (!remap.has(g)) remap.set(g, remap.size + 1);
    return remap.get(g)!;
  });
}

/** True when the next exercise is in the same superset, so no rest is needed after this one. */
export function isSupersetContinuation(groups: Groups, idx: number): boolean {
  const g = groups[idx];
  return g != null && groups[idx + 1] === g;
}

export function supersetLabel(group: number | null | undefined): string | null {
  return group == null ? null : `Superset ${String.fromCharCode(64 + ((group - 1) % 26) + 1)}`;
}
