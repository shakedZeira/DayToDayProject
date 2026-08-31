import type { VisionEstimate, VisionProvider } from "./vision";

export const stubProvider: VisionProvider = {
  name: "stub",
  async estimateCalories(_imageBuffer: Buffer, _filename: string): Promise<VisionEstimate> {
    return {
      provider: this.name,
      items: [{ foodName: "Estimated meal", estimatedGrams: 300, estimatedCalories: 500 }],
      totalCalories: 500,
      disclaimer:
        "The free local estimator cannot actually recognize food from a photo — this is a rough 300g / 500 kcal placeholder. Enable a paid vision provider for real recognition.",
    };
  },
};