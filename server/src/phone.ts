// Runs the Express API and serves the built React PWA from a single HTTP server.
import "dotenv/config";
import path from "path";
import fs from "fs";
import express from "express";
import { app } from "./app";
import { startScheduler } from "./scheduler";

const clientDist = process.env.CLIENT_DIST || path.resolve(__dirname, "..", "..", "client", "dist");

if (fs.existsSync(clientDist)) {
  app.use(express.static(clientDist));
  app.get(/^\/(?!api\/).*/, (_req, res) => {
    res.sendFile(path.join(clientDist, "index.html"));
  });
} else {
  console.warn(`[phone] Client build not found at ${clientDist}; serving API only.`);
}

const port = Number(process.env.PORT) || 4000;
const host = process.env.HOST || "127.0.0.1";

startScheduler();
app.listen(port, host, () => {
  console.log(`DayToDay listening on http://${host}:${port}`);
});
