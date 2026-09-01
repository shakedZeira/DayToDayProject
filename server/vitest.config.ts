import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    fileParallelism: false,
    env: {
      DATABASE_URL: "postgresql://dtd:dtd@localhost:5432/dtd_test?schema=public",
    },
    globalSetup: "./vitest.globalSetup.ts",
  },
});
