import { prisma } from "./db";
import { seedFoods } from "./foods.seed";

async function main() {
  const users = await prisma.user.findMany();
  for (const user of users) {
    const count = await seedFoods(user.id);
    console.log(`Seeded ${count} foods for ${user.email}`);
  }
}

main()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(async () => {
    await prisma.$disconnect();
  });