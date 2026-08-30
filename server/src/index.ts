import "dotenv/config";
import express from "express";
import cors from "cors";
import { healthRouter } from "./health";
import { prisma } from "./db";

export const app = express();

app.use(cors({ origin: process.env.CLIENT_URL || "*" }));
app.use(express.json());

app.use("/api", healthRouter);

// Verify the DB is reachable on boot.
app.get("/api/ready", async (_req, res) => {
  await prisma.$queryRaw`SELECT 1`;
  res.json({ ready: true });
});

const port = Number(process.env.PORT) || 4000;

if (require.main === module) {
  app.listen(port, () => {
    console.log(`Server listening on http://localhost:${port}`);
  });
}
