import { stubProvider } from "./visionStub";

export interface VisionEstimate {
  provider: string;
  items: { foodName: string; estimatedGrams: number; estimatedCalories: number }[];
  totalCalories: number;
  disclaimer: string;
}

export interface VisionProvider {
  readonly name: string;
  estimateCalories(imageBuffer: Buffer, filename: string): Promise<VisionEstimate>;
}

let paidProvider: VisionProvider | null = null;
export async function loadPaidProvider(): Promise<VisionProvider> {
  if (!paidProvider) {
    const mod = await import("./visionPaid").catch(() => null) as { paidProvider?: VisionProvider } | null;
    paidProvider = mod?.paidProvider ?? stubProvider;
  }
  return paidProvider;
}
export async function createVisionProvider(env = process.env): Promise<VisionProvider> {
  const name = (env.VISION_PROVIDER || "stub").toLowerCase();
  if (name === "paid") return loadPaidProvider();
  return stubProvider;
}
