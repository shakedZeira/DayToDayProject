import "dotenv/config";
import express from "express";
import cors from "cors";
import { healthRouter } from "./health";
import { authRouter } from "./auth";
import { tasksRouter } from "./tasks";
import { goalsRouter } from "./goals";
import { pushRouter } from "./push";
import { startScheduler } from "./scheduler";
import { prisma } from "./db";

export const app = express();

app.use(cors({ origin: process.env.CLIENT_URL || "*" }));
app.use(express.json());

app.use("/api", healthRouter);
app.use("/api/auth", authRouter);
app.use("/api/tasks", tasksRouter);
app.use("/api/goals", goalsRouter);
app.use("/api/push", pushRouter);

// Verify the DB is reachable on boot.
app.get("/api/ready", async (_req, res) => {
  await prisma.$queryRaw`SELECT 1`;
  res.json({ ready: true });
});

const port = Number(process.env.PORT) || 4000;

if (require.main === module) {
  startScheduler();
  app.listen(port, () => {
    console.log(`Server listening on http://localhost:${port}`);
  });
}
