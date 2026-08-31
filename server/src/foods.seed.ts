import { prisma } from "./db";
import type { FoodInput } from "shared";

export const SEED_FOODS: FoodInput[] = [
  { name: "Chicken Breast", nameHe: "חזה עוף", caloriesPer100: 165 },
  { name: "Turkey Breast", nameHe: "חזה הודו", caloriesPer100: 189 },
  { name: "Salmon", nameHe: "סלמון", caloriesPer100: 208 },
  { name: "Tuna (in water)", nameHe: "טונה במים", caloriesPer100: 116 },
  { name: "Whole Egg", nameHe: "ביצה", caloriesPer100: 143 },
  { name: "Egg White", nameHe: "חלבון ביצה", caloriesPer100: 52 },
  { name: "Cottage Cheese (5%)", nameHe: "קוטג'", caloriesPer100: 98 },
  { name: "Greek Yogurt (plain)", nameHe: "יוגורט יווני", caloriesPer100: 59 },
  { name: "White Rice (cooked)", nameHe: "אורז לבן", caloriesPer100: 130 },
  { name: "Pasta (cooked)", nameHe: "פסטה", caloriesPer100: 131 },
  { name: "Whole Wheat Bread", nameHe: "לחם מלא", caloriesPer100: 247 },
  { name: "Oats (dry)", nameHe: "שיבולת שועל", caloriesPer100: 389 },
  { name: "Couscous (cooked)", nameHe: "קוסקוס", caloriesPer100: 112 },
  { name: "Potato (boiled)", nameHe: "תפוח אדמה", caloriesPer100: 87 },
  { name: "Sweet Potato", nameHe: "בטטה", caloriesPer100: 86 },
  { name: "Broccoli", nameHe: "ברוקולי", caloriesPer100: 34 },
  { name: "Spinach", nameHe: "תרד", caloriesPer100: 23 },
  { name: "Tomato", nameHe: "עגבנייה", caloriesPer100: 18 },
  { name: "Cucumber", nameHe: "מלפפון", caloriesPer100: 15 },
  { name: "Carrot", nameHe: "גזר", caloriesPer100: 41 },
  { name: "Banana", nameHe: "בננה", caloriesPer100: 89 },
  { name: "Apple", nameHe: "תפוח", caloriesPer100: 52 },
  { name: "Avocado", nameHe: "אבוקדו", caloriesPer100: 160 },
  { name: "Orange", nameHe: "תפוז", caloriesPer100: 47 },
  { name: "Milk (whole)", nameHe: "חלב", caloriesPer100: 61, servingUnit: "ml" },
  { name: "Cheddar Cheese", nameHe: "צ'דר", caloriesPer100: 403 },
  { name: "Olive Oil", nameHe: "שמן זית", caloriesPer100: 884, servingUnit: "ml" },
  { name: "Almonds", nameHe: "שקדים", caloriesPer100: 579 },
  { name: "Peanut Butter", nameHe: "חמאת בוטנים", caloriesPer100: 588 },
];

export async function seedFoods(): Promise<number> {
  let created = 0;
  for (const food of SEED_FOODS) {
    const existing = await prisma.food.findUnique({
      where: { name: food.name },
    });
    if (existing) continue;
    await prisma.food.create({
      data: {
        name: food.name,
        nameHe: food.nameHe ?? null,
        caloriesPer100: food.caloriesPer100,
        servingUnit: food.servingUnit ?? "g",
      },
    });
    created += 1;
  }
  return created;
}