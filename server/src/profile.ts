import { Router } from "express";
import { prisma } from "./db";
import { requireAuth, type AuthedRequest } from "./session";
import type { ProfileInput, CalorieRecommendation, ActivityLevel } from "../../shared/src/index";

export const profileRouter = Router();
profileRouter.use(requireAuth);

const ACTIVITY_LEVELS: ActivityLevel[] = [
  "sedentary", "light", "moderate", "active", "very_active",
];

const ACTIVITY_MULTIPLIERS: Record<ActivityLevel, number> = {
  sedentary: 1.2,
  light: 1.375,
  moderate: 1.55,
  active: 1.725,
  very_active: 1.9,
};

const round10 = (n: number) => Math.round(n / 10) * 10;

export function computeRecommendation(input: ProfileInput): CalorieRecommendation {
  const { weightKg, heightCm, age, sex, activity } = input;
  const bmrRaw =
    sex === "female"
      ? 10 * weightKg + 6.25 * heightCm - 5 * age - 161
      : 10 * weightKg + 6.25 * heightCm - 5 * age + 5;
  const bmr = round10(bmrRaw);
  const tdeeRaw = bmrRaw * ACTIVITY_MULTIPLIERS[activity];
  const tdee = round10(tdeeRaw);
  const weightLoss = Math.max(1200, round10(tdeeRaw - 500));
  const weightGain = round10(tdeeRaw + 300);

  return {
    bmr,
    tdee,
    activity,
    maintenance: tdee,
    weightLoss,
    weightGain,
    method:
      "Mifflin-St Jeor (1990) BMR × activity factor " +
      "(NHS/FDA ref: ~2000 kcal women / 2500 kcal men; National Academies DRI)",
    disclaimer:
      "Estimate only (±10%); adjust with real weight tracking over 1–2 weeks.",
  };
}

profileRouter.get("/", async (req: AuthedRequest, res) => {
  const row = await prisma.userProfile.findUnique({
    where: { ownerId: req.userId! },
  });
  if (!row) return res.status(404).json({ error: "no profile" });

  const recommendation = computeRecommendation({
    heightCm: row.heightCm,
    weightKg: row.weightKg,
    age: row.age ?? 30,
    sex: (row.sex as "male" | "female") ?? "male",
    activity: (row.activity as ActivityLevel) ?? "light",
  });

  res.json({ profile: row, recommendation });
});

profileRouter.put("/", async (req: AuthedRequest, res) => {
  const { heightCm, weightKg, age, sex, activity, applyTarget } = req.body ?? {};

  if (typeof heightCm !== "number" || !Number.isFinite(heightCm) || heightCm < 50 || heightCm > 250) {
    return res.status(400).json({ error: "heightCm must be a number between 50 and 250" });
  }
  if (typeof weightKg !== "number" || !Number.isFinite(weightKg) || weightKg < 25 || weightKg > 300) {
    return res.status(400).json({ error: "weightKg must be a number between 25 and 300" });
  }
  if (age !== undefined && age !== null) {
    if (!Number.isInteger(age) || age < 10 || age > 110) {
      return res.status(400).json({ error: "age must be an integer between 10 and 110" });
    }
  }
  if (sex !== undefined && sex !== null && sex !== "male" && sex !== "female") {
    return res.status(400).json({ error: "sex must be 'male' or 'female'" });
  }
  if (activity !== undefined && activity !== null && !ACTIVITY_LEVELS.includes(activity)) {
    return res.status(400).json({ error: "activity must be one of: " + ACTIVITY_LEVELS.join(", ") });
  }

  const resolvedAge = typeof age === "number" ? age : 30;
  const resolvedSex = sex === "female" ? "female" : "male";
  const resolvedActivity: ActivityLevel =
    activity && ACTIVITY_LEVELS.includes(activity) ? activity : "light";

  const row = await prisma.userProfile.upsert({
    where: { ownerId: req.userId! },
    create: {
      ownerId: req.userId!,
      heightCm,
      weightKg,
      age: typeof age === "number" ? age : null,
      sex: sex ?? null,
      activity: activity ?? null,
    },
    update: {
      heightCm,
      weightKg,
      age: typeof age === "number" ? age : null,
      sex: sex ?? null,
      activity: activity ?? null,
    },
  });

  const recommendation = computeRecommendation({
    heightCm,
    weightKg,
    age: resolvedAge,
    sex: resolvedSex,
    activity: resolvedActivity,
  });

  let targetApplied = false;
  if (applyTarget) {
    await prisma.dailyTarget.upsert({
      where: { ownerId: req.userId! },
      create: { ownerId: req.userId!, calories: recommendation.maintenance },
      update: { calories: recommendation.maintenance },
    });
    targetApplied = true;
  }

  res.json({ profile: row, recommendation, targetApplied });
});
