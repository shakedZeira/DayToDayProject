function norm(s: string): string {
  return s
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function levenshtein(a: string, b: string): number {
  if (a === b) return 0;
  if (!a.length) return b.length;
  if (!b.length) return a.length;
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    prev = cur;
  }
  return prev[b.length];
}

function similarity(a: string, b: string): number {
  const max = Math.max(a.length, b.length);
  return max === 0 ? 1 : 1 - levenshtein(a, b) / max;
}

export interface SpeechScore {
  /** 0..100 */
  score: number;
  charScore: number;
  tokenScore: number;
  missing: string[];
}

/** Scores a transcript against a target phrase (accent/case/punctuation-insensitive). */
export function scoreSpeech(transcript: string, target: string): SpeechScore {
  const t = norm(transcript);
  const g = norm(target);
  if (!g) return { score: 0, charScore: 0, tokenScore: 0, missing: [] };
  if (!t) return { score: 0, charScore: 0, tokenScore: 0, missing: g.split(" ") };
  const flat = similarity(t.replace(/ /g, ""), g.replace(/ /g, ""));
  const charScore = Math.max(similarity(t, g), flat);
  const spoken = t.split(" ");
  const missing: string[] = [];
  let sum = 0;
  for (const w of g.split(" ")) {
    const best = Math.max(...spoken.map((s) => similarity(s, w)));
    sum += best >= 0.75 ? best : 0;
    if (best < 0.75) missing.push(w);
  }
  // Word-boundary differences ("buon giorno" vs "buongiorno") should not be punished.
  const tokenScore = flat >= 0.9 ? Math.max(flat, sum / g.split(" ").length) : sum / g.split(" ").length;
  return {
    score: Math.round((charScore * 0.5 + tokenScore * 0.5) * 100),
    charScore: Math.round(charScore * 100) / 100,
    tokenScore: Math.round(tokenScore * 100) / 100,
    missing: flat >= 0.9 ? [] : missing
  };
}
