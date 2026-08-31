import { prisma } from "./db";
import { seedFoods } from "./foods.seed";
import { seedExercises } from "./exercises.seed";

async function main() {
  const foods = await seedFoods();
  console.log(`Seeded ${foods} foods (shared global catalog)`);
  const exercises = await seedExercises();
  console.log(`Seeded ${exercises} exercises (shared global library)`);
}

main()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(async () => {
    await prisma.$disconnect();
  });