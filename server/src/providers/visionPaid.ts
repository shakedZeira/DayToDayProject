import type { VisionEstimate, VisionProvider } from "./vision";

export const paidProvider: VisionProvider = {
  name: "paid",
  async estimateCalories(_imageBuffer: Buffer, _filename: string): Promise<VisionEstimate> {
    return {
      provider: this.name,
      items: [{ foodName: "unrecognized", estimatedGrams: 200, estimatedCalories: 300 }],
      totalCalories: 300,
      disclaimer: "Paid provider placeholder — wire a real vision client here.",
    };
  },
};
