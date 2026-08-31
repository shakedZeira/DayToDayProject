import { prisma } from "./db";
import { seedFoods } from "./foods.seed";

async function main() {
  const count = await seedFoods();
  console.log(`Seeded ${count} foods (shared global catalog)`);
}

main()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(async () => {
    await prisma.$disconnect();
  });