import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    fileParallelism: false,
    env: {
      DATABASE_URL: process.env.TEST_DATABASE_URL || "postgresql://dtd:dtd@localhost:5432/dtd_test?schema=public",
      LOG_SILENT: "true",
    },
    globalSetup: "./vitest.globalSetup.ts",
  },
});
