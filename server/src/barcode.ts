import type { FoodInput } from "shared";

export type BarcodeFetcher = (url: string) => Promise<{ status: number; json: () => Promise<unknown> }>;

const defaultFetcher: BarcodeFetcher = async (url) => {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 8000);
  try {
    return await fetch(url, {
      headers: { "User-Agent": "DayToDay/1.0 (personal nutrition tracker)" },
      signal: ctrl.signal,
    });
  } finally {
    clearTimeout(timer);
  }
};

let fetcher: BarcodeFetcher = defaultFetcher;
/** Inject the HTTP call (tests mock it so they never touch the network). Pass null to restore. */
export function setBarcodeFetcher(f: BarcodeFetcher | null): void {
  fetcher = f ?? defaultFetcher;
}

export function isValidBarcode(code: string): boolean {
  return /^\d{6,14}$/.test(code);
}

function num(v: unknown): number {
  const n = typeof v === "string" ? Number(v) : v;
  return typeof n === "number" && Number.isFinite(n) && n >= 0 ? Math.round(n * 10) / 10 : 0;
}

/** Maps an Open Food Facts v2 product payload to a FoodInput, or null if unusable. */
export function mapOffProduct(code: string, body: unknown): (FoodInput & { name: string }) | null {
  const b = body as { status?: number; product?: Record<string, unknown> } | null;
  const p = b?.product;
  if (!p || b?.status === 0) return null;
  const n = (p.nutriments ?? {}) as Record<string, unknown>;
  let kcal = n["energy-kcal_100g"];
  if (kcal === undefined && n["energy_100g"] !== undefined) kcal = num(n["energy_100g"]) / 4.184; // kJ
  const nameRaw = [p.product_name, p.product_name_en, p.generic_name].find((x) => typeof x === "string" && x.trim());
  const brand = typeof p.brands === "string" ? p.brands.split(",")[0].trim() : "";
  if (typeof nameRaw !== "string" || kcal === undefined) return null;
  const base = nameRaw.trim();
  const name = brand && !base.toLowerCase().includes(brand.toLowerCase()) ? `${base} (${brand})` : base;
  return {
    name,
    caloriesPer100: Math.round(num(kcal)),
    proteinPer100: num(n["proteins_100g"]),
    carbsPer100: num(n["carbohydrates_100g"]),
    fatPer100: num(n["fat_100g"]),
    servingUnit: "g",
    barcode: code,
  };
}

/** Looks a barcode up on Open Food Facts. Returns null when the product is unknown. Throws on network errors. */
export async function lookupOpenFoodFacts(code: string): Promise<(FoodInput & { name: string }) | null> {
  const res = await fetcher(`https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(code)}.json`);
  if (res.status === 404) return null;
  if (res.status < 200 || res.status >= 300) throw new Error(`Open Food Facts returned ${res.status}`);
  return mapOffProduct(code, await res.json());
}
