import { execSync } from "child_process";
import path from "path";

export default async function globalSetup() {
  const testDbUrl = "postgresql://dtd:dtd@localhost:5432/dtd_test?schema=public";
  process.env.DATABASE_URL = testDbUrl;

  execSync("npx prisma db push --skip-generate --force-reset", {
    cwd: path.resolve(__dirname),
    env: { ...process.env, DATABASE_URL: testDbUrl },
    stdio: "inherit",
  });
}
