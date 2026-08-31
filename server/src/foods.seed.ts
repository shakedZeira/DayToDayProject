import { prisma } from "./db";
import type { FoodInput } from "shared";

export const SEED_FOODS: FoodInput[] = [
  { name: "Chicken Breast", caloriesPer100: 165 },
  { name: "White Rice (cooked)", caloriesPer100: 130 },
  { name: "Brown Rice (cooked)", caloriesPer100: 112 },
  { name: "Broccoli", caloriesPer100: 34 },
  { name: "Spinach", caloriesPer100: 23 },
  { name: "Olive Oil", caloriesPer100: 884, servingUnit: "ml" },
  { name: "Whole Egg", caloriesPer100: 143 },
  { name: "Banana", caloriesPer100: 89 },
  { name: "Apple", caloriesPer100: 52 },
  { name: "Bread (whole wheat)", caloriesPer100: 247 },
  { name: "Oats (dry)", caloriesPer100: 389 },
  { name: "Greek Yogurt (plain)", caloriesPer100: 59 },
  { name: "Almonds", caloriesPer100: 579 },
  { name: "Peanut Butter", caloriesPer100: 588 },
  { name: "Salmon", caloriesPer100: 208 },
  { name: "Potato (boiled)", caloriesPer100: 87 },
  { name: "Sweet Potato", caloriesPer100: 86 },
  { name: "Tomato", caloriesPer100: 18 },
  { name: "Cheddar Cheese", caloriesPer100: 403 },
  { name: "Milk (whole)", caloriesPer100: 61, servingUnit: "ml" },
];

export async function seedFoods(ownerId: string): Promise<number> {
  let created = 0;
  for (const food of SEED_FOODS) {
    const existing = await prisma.food.findUnique({
      where: { ownerId_name: { ownerId, name: food.name } },
    });
    if (existing) continue;
    await prisma.food.create({
      data: {
        ownerId,
        name: food.name,
        caloriesPer100: food.caloriesPer100,
        servingUnit: food.servingUnit ?? "g",
      },
    });
    created += 1;
  }
  return created;
}