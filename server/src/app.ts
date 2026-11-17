import express from "express";
import cors from "cors";
import { healthRouter } from "./health";
import { authRouter } from "./auth";
import { tasksRouter } from "./tasks";
import { goalsRouter } from "./goals";
import { pushRouter } from "./push";
import { workoutsRouter } from "./workouts";
import { foodsRouter } from "./foods";
import { exercisesRouter } from "./exercises";
import { nutritionRouter } from "./nutrition";
import { nutritionPhotoRouter } from "./nutritionPhoto";
import { profileRouter } from "./profile";
import { routinesRouter } from "./routines";
import { progressionRouter } from "./progression";
import { recipesRouter } from "./recipes";
import { weeklyReviewRouter } from "./weeklyReview";
import { shoppingRouter, mealPlanRouter } from "./shopping";
import { italianRouter } from "./italian";
import { italianChatRouter } from "./italianChat";
import { italianPathRouter } from "./italianPath";
import { digestRouter } from "./digest";
import { settingsRouter } from "./settings";
import { progressRouter } from "./progress";
import { exportRouter } from "./exportData";
import { prisma } from "./db";
import { requestLogger, errorHandler } from "./observability";

export const app = express();

app.use(requestLogger);
app.use(cors({ origin: process.env.CLIENT_URL || "*" }));
app.use(express.json());

app.use("/api", healthRouter);
app.use("/api/auth", authRouter);
app.use("/api/tasks", tasksRouter);
app.use("/api/goals", goalsRouter);
app.use("/api/push", pushRouter);
app.use("/api/workouts", workoutsRouter);
app.use("/api/foods", foodsRouter);
app.use("/api/exercises", exercisesRouter);
app.use("/api/routines", routinesRouter);
app.use("/api/progression", progressionRouter);
app.use("/api/nutrition", nutritionRouter);
app.use("/api/nutrition", nutritionPhotoRouter);
app.use("/api/profile", profileRouter);
app.use("/api/recipes", recipesRouter);
app.use("/api/shopping", shoppingRouter);
app.use("/api/mealplan", mealPlanRouter);
app.use("/api/italian", italianRouter);
app.use("/api/italian", italianChatRouter);
app.use("/api/italian-path", italianPathRouter);
app.use("/api/digest", digestRouter);
app.use("/api/settings", settingsRouter);
app.use("/api/progress", progressRouter);
app.use("/api/review", weeklyReviewRouter);
app.use("/api/export", exportRouter);

// Verify the DB is reachable on boot.
app.get("/api/ready", async (_req, res) => {
  await prisma.$queryRaw`SELECT 1`;
  res.json({ ready: true });
});

app.use(errorHandler); // keep last
