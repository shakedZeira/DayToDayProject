import { prisma } from "../db";
import type { VisionEstimate, VisionProvider } from "./vision";

export const stubProvider: VisionProvider = {
  name: "stub",
  async estimateCalories(_imageBuffer: Buffer, _filename: string): Promise<VisionEstimate> {
    const top = await prisma.food.findMany({
      orderBy: { caloriesPer100: "desc" },
      take: 1,
    });
    const food = top[0] ?? { name: "Assorted meal", caloriesPer100: 150 };
    const estimatedGrams = 250;
    const estimatedCalories = Math.round(food.caloriesPer100 * (estimatedGrams / 100));
    return {
      provider: this.name,
      items: [{ foodName: food.name, estimatedGrams, estimatedCalories }],
      totalCalories: estimatedCalories,
      disclaimer:
        "Rough local estimate from your most common foods. Upgrade to a paid vision provider for accurate photo recognition.",
    };
  },
};
