import { stubProvider } from "./visionStub";
import { createGeminiProvider, type GeminiFetch } from "./visionGemini";

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
export async function createVisionProvider(
  env: NodeJS.ProcessEnv = process.env,
  fetchImpl?: GeminiFetch,
): Promise<VisionProvider> {
  const name = (env.VISION_PROVIDER || "stub").toLowerCase();
  if (name === "gemini") {
    const apiKey = env.GEMINI_API_KEY?.trim();
    if (!apiKey) {
      console.warn("VISION_PROVIDER=gemini but GEMINI_API_KEY is missing; falling back to stub");
      return stubProvider;
    }
    return createGeminiProvider({ apiKey, model: env.VISION_MODEL?.trim() || undefined, fetchImpl });
  }
  if (name === "paid") return loadPaidProvider();
  return stubProvider;
}
