import { prisma } from "./db";
import { seedFoods } from "./foods.seed";
import { seedExercises } from "./exercises.seed";
import { seedDefaultRoutines } from "./routines.seed";

async function main() {
  const foods = await seedFoods();
  console.log(`Seeded ${foods} foods (shared global catalog)`);
  const exercises = await seedExercises();
  console.log(`Seeded ${exercises} exercises (shared global library)`);

  // Seed default routines for all existing users
  const users = await prisma.user.findMany({ select: { id: true, email: true } });
  for (const user of users) {
    const routines = await seedDefaultRoutines(user.id);
    if (routines > 0) {
      console.log(`Seeded ${routines} default routines for user ${user.email}`);
    }
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