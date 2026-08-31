import { prisma } from "./db";

interface RoutineSeedExercise {
  exerciseName: string;
  targetSets: number;
  targetReps: number;
}

interface RoutineSeedDay {
  name: string;
  exercises: RoutineSeedExercise[];
}

interface RoutineSeed {
  name: string;
  description: string;
  days: RoutineSeedDay[];
}

const STARTER_ROUTINES: RoutineSeed[] = [
  {
    name: "Full Body A",
    description: "Full body workout hitting all major muscle groups",
    days: [
      {
        name: "Full Body",
        exercises: [
          { exerciseName: "Squat", targetSets: 3, targetReps: 8 },
          { exerciseName: "Bench Press", targetSets: 3, targetReps: 8 },
          { exerciseName: "Barbell Row", targetSets: 3, targetReps: 8 },
          { exerciseName: "Plank", targetSets: 3, targetReps: 30 },
          { exerciseName: "Overhead Press", targetSets: 3, targetReps: 8 },
          { exerciseName: "Deadlift", targetSets: 1, targetReps: 5 },
        ],
      },
    ],
  },
  {
    name: "Push/Pull/Legs",
    description: "Classic PPL split",
    days: [
      {
        name: "Push A",
        exercises: [
          { exerciseName: "Bench Press", targetSets: 3, targetReps: 8 },
          { exerciseName: "Overhead Press", targetSets: 3, targetReps: 8 },
          { exerciseName: "Incline Bench Press", targetSets: 3, targetReps: 8 },
          { exerciseName: "Tricep Pushdown", targetSets: 3, targetReps: 10 },
          { exerciseName: "Lateral Raise", targetSets: 3, targetReps: 12 },
        ],
      },
      {
        name: "Pull A",
        exercises: [
          { exerciseName: "Deadlift", targetSets: 1, targetReps: 5 },
          { exerciseName: "Pull-Up", targetSets: 3, targetReps: 8 },
          { exerciseName: "Barbell Row", targetSets: 3, targetReps: 8 },
          { exerciseName: "Face Pull", targetSets: 3, targetReps: 12 },
          { exerciseName: "Dumbbell Bicep Curl", targetSets: 3, targetReps: 10 },
        ],
      },
      {
        name: "Legs A",
        exercises: [
          { exerciseName: "Squat", targetSets: 3, targetReps: 8 },
          { exerciseName: "Romanian Deadlift", targetSets: 3, targetReps: 8 },
          { exerciseName: "Leg Press", targetSets: 3, targetReps: 10 },
          { exerciseName: "Leg Curl", targetSets: 3, targetReps: 10 },
          { exerciseName: "Calf Raise - Bodyweight", targetSets: 3, targetReps: 15 },
        ],
      },
    ],
  },
  {
    name: "Upper/Lower",
    description: "4-day upper/lower split",
    days: [
      {
        name: "Upper A",
        exercises: [
          { exerciseName: "Bench Press", targetSets: 3, targetReps: 8 },
          { exerciseName: "Barbell Row", targetSets: 3, targetReps: 8 },
          { exerciseName: "Overhead Press", targetSets: 3, targetReps: 8 },
          { exerciseName: "Pull-Up", targetSets: 3, targetReps: 8 },
          { exerciseName: "Dumbbell Bicep Curl", targetSets: 3, targetReps: 10 },
        ],
      },
      {
        name: "Lower A",
        exercises: [
          { exerciseName: "Squat", targetSets: 3, targetReps: 8 },
          { exerciseName: "Deadlift", targetSets: 1, targetReps: 5 },
          { exerciseName: "Romanian Deadlift", targetSets: 3, targetReps: 8 },
          { exerciseName: "Leg Press", targetSets: 3, targetReps: 10 },
          { exerciseName: "Calf Raise - Bodyweight", targetSets: 3, targetReps: 15 },
        ],
      },
    ],
  },
];

export async function seedDefaultRoutines(ownerId: string): Promise<number> {
  const count = await prisma.routine.count({ where: { ownerId } });
  if (count > 0) return 0;

  let created = 0;
  for (const seed of STARTER_ROUTINES) {
    const daysData: any[] = [];
    let skip = false;

    for (let dayIdx = 0; dayIdx < seed.days.length; dayIdx++) {
      const day = seed.days[dayIdx];
      const exercisesData: any[] = [];

      for (let exIdx = 0; exIdx < day.exercises.length; exIdx++) {
        const ex = day.exercises[exIdx];
        const exercise = await prisma.exercise.findFirst({
          where: { ownerId: null, name: ex.exerciseName },
        });
        if (!exercise) {
          skip = true;
          break;
        }
        exercisesData.push({
          ownerId,
          order: exIdx,
          targetSets: ex.targetSets,
          targetReps: ex.targetReps,
          exerciseId: exercise.id,
        });
      }
      if (skip) break;

      daysData.push({
        ownerId,
        name: day.name,
        order: dayIdx,
        exercises: { create: exercisesData },
      });
    }

    if (skip) continue;

    await prisma.routine.create({
      data: {
        ownerId,
        name: seed.name,
        description: seed.description,
        days: { create: daysData },
      },
    });
    created++;
  }

  return created;
}
