import { extractJson, type LLMProvider } from "./providers/llm";
import type { ItalianCorrection, ItalianPhrase, MistakeType } from "shared";

export const MISTAKE_TYPES: readonly MistakeType[] = [
  "correct", "minor", "vocabulary", "grammar", "word-order", "incomplete", "other"
];

export function normalizeForCompare(text: string): string {
  return text
    .toLowerCase()
    .replace(/[\u2018\u2019`']/g, "'")
    .replace(/[-.,!?;:«»…—–()]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function correctionPrompt(phraseEnglish: string, targetItalian: string, userAttempt: string): string {
  return [
    "You are an Italian tutor. The learner tried to say a phrase in Italian.",
    `English prompt: ${phraseEnglish}`,
    `Learner said: ${userAttempt}`,
    `Correct Italian phrase: ${targetItalian}`,
    'Reply with ONLY JSON: {"isCorrect": boolean, "mistakeType": "correct"|"minor"|"vocabulary"|"grammar"|"word-order"|"incomplete"|"other", "hint": string, "correctPhrase": string}',
    "If the learner is close, mark isCorrect true and keep hint empty. Be friendly."
  ].join("\n");
}

export function fallbackCorrection(phrase: ItalianPhrase, userAttempt: string): ItalianCorrection {
  const isCorrect =
    userAttempt.trim() !== "" && normalizeForCompare(userAttempt) === normalizeForCompare(phrase.italian);
  return {
    isCorrect,
    phraseEnglish: phrase.english,
    targetItalian: phrase.italian,
    userAttempt,
    mistakeType: isCorrect ? "correct" : "vocabulary",
    hint: isCorrect ? "Perfetto!" : `Not quite. Try again — it starts with "${phrase.italian.split(" ")[0]}".`,
    correctPhrase: phrase.italian
  };
}

export function tryParseCorrection(
  phrase: ItalianPhrase,
  userAttempt: string,
  text: string
): ItalianCorrection | null {
  const json = extractJson(text);
  if (!json) return null;
  if (typeof json.isCorrect !== "boolean") return null;
  if (typeof json.mistakeType !== "string" || !(MISTAKE_TYPES as readonly string[]).includes(json.mistakeType)) {
    return null;
  }
  if (typeof json.hint !== "string" || typeof json.correctPhrase !== "string") return null;
  return {
    isCorrect: json.isCorrect,
    phraseEnglish: phrase.english,
    targetItalian: phrase.italian,
    userAttempt,
    mistakeType: json.mistakeType as MistakeType,
    hint: json.hint,
    correctPhrase: json.correctPhrase || phrase.italian
  };
}

export async function checkAttempt(
  provider: LLMProvider,
  phrase: ItalianPhrase,
  userAttempt: string
): Promise<ItalianCorrection> {
  try {
    const raw = await provider.complete(correctionPrompt(phrase.english, phrase.italian, userAttempt));
    return tryParseCorrection(phrase, userAttempt, raw) ?? fallbackCorrection(phrase, userAttempt);
  } catch {
    return fallbackCorrection(phrase, userAttempt);
  }
}

export function translatePrompt(english: string): string {
  return [
    "You are an Italian tutor. Translate the learner's English into natural Italian.",
    `English: ${english}`,
    'Reply with ONLY JSON: {"italian": string}'
  ].join("\n");
}

export function fallbackTranslate(phrases: ItalianPhrase[], english: string): string {
  const norm = normalizeForCompare(english);
  for (const p of phrases) {
    if (normalizeForCompare(p.english) === norm) return p.italian;
  }
  return "Non ho capito, puoi ripetere?";
}

export async function translateToItalian(
  provider: LLMProvider,
  phrases: ItalianPhrase[],
  english: string
): Promise<string> {
  try {
    const raw = await provider.complete(translatePrompt(english));
    const json = extractJson(raw);
    if (json && typeof json.italian === "string" && json.italian.trim()) return json.italian.trim();
  } catch {
    /* fall through to fallbackTranslate */
  }
  return fallbackTranslate(phrases, english);
}
