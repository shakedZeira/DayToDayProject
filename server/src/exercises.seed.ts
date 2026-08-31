import { prisma } from "./db";

export interface SeedExercise {
  name: string;
  muscleGroup: string;
  equipment: string;
  isCompound: boolean;
}

export const EXERCISES: SeedExercise[] = [
  { name: "Squat", muscleGroup: "Legs", equipment: "Barbell", isCompound: true },
  { name: "Deadlift", muscleGroup: "Back", equipment: "Barbell", isCompound: true },
  { name: "Bench Press", muscleGroup: "Chest", equipment: "Barbell", isCompound: true },
  { name: "Overhead Press", muscleGroup: "Shoulders", equipment: "Barbell", isCompound: true },
  { name: "Barbell Row", muscleGroup: "Back", equipment: "Barbell", isCompound: true },
  { name: "Romanian Deadlift", muscleGroup: "Back", equipment: "Barbell", isCompound: true },
  { name: "Front Squat", muscleGroup: "Legs", equipment: "Barbell", isCompound: true },
  { name: "Sumo Deadlift", muscleGroup: "Back", equipment: "Barbell", isCompound: true },
  { name: "Incline Bench Press", muscleGroup: "Chest", equipment: "Barbell", isCompound: true },
  { name: "Close-Grip Bench Press", muscleGroup: "Arms", equipment: "Barbell", isCompound: true },
  { name: "Dumbbell Bench Press", muscleGroup: "Chest", equipment: "Dumbbell", isCompound: true },
  { name: "Dumbbell Row", muscleGroup: "Back", equipment: "Dumbbell", isCompound: true },
  { name: "Dumbbell Shoulder Press", muscleGroup: "Shoulders", equipment: "Dumbbell", isCompound: true },
  { name: "Dumbbell Bicep Curl", muscleGroup: "Arms", equipment: "Dumbbell", isCompound: false },
  { name: "Dumbbell Lunges", muscleGroup: "Legs", equipment: "Dumbbell", isCompound: true },
  { name: "Goblet Squat", muscleGroup: "Legs", equipment: "Dumbbell", isCompound: true },
  { name: "Dumbbell RDL", muscleGroup: "Back", equipment: "Dumbbell", isCompound: true },
  { name: "Lateral Raise", muscleGroup: "Shoulders", equipment: "Dumbbell", isCompound: false },
  { name: "Front Raise", muscleGroup: "Shoulders", equipment: "Dumbbell", isCompound: false },
  { name: "Dumbbell Shrug", muscleGroup: "Back", equipment: "Dumbbell", isCompound: false },
  { name: "Hammer Curl", muscleGroup: "Arms", equipment: "Dumbbell", isCompound: false },
  { name: "Tricep Kickback", muscleGroup: "Arms", equipment: "Dumbbell", isCompound: false },
  { name: "Pull-Up", muscleGroup: "Back", equipment: "Bodyweight", isCompound: true },
  { name: "Chin-Up", muscleGroup: "Back", equipment: "Bodyweight", isCompound: true },
  { name: "Push-Up", muscleGroup: "Chest", equipment: "Bodyweight", isCompound: true },
  { name: "Dips", muscleGroup: "Arms", equipment: "Bodyweight", isCompound: true },
  { name: "Plank", muscleGroup: "Core", equipment: "Bodyweight", isCompound: false },
  { name: "Crunch", muscleGroup: "Core", equipment: "Bodyweight", isCompound: false },
  { name: "Side Plank", muscleGroup: "Core", equipment: "Bodyweight", isCompound: false },
  { name: "Mountain Climbers", muscleGroup: "Core", equipment: "Bodyweight", isCompound: true },
  { name: "Glute Bridge", muscleGroup: "Glutes", equipment: "Bodyweight", isCompound: false },
  { name: "Bulgarian Split Squat", muscleGroup: "Legs", equipment: "Bodyweight", isCompound: true },
  { name: "Bodyweight Squat", muscleGroup: "Legs", equipment: "Bodyweight", isCompound: true },
  { name: "Walking Lunge", muscleGroup: "Legs", equipment: "Bodyweight", isCompound: true },
  { name: "Burpees", muscleGroup: "Core", equipment: "Bodyweight", isCompound: true },
  { name: "Calf Raise - Bodyweight", muscleGroup: "Legs", equipment: "Bodyweight", isCompound: false },
  { name: "Superman", muscleGroup: "Core", equipment: "Bodyweight", isCompound: false },
  { name: "Bicycle Crunch", muscleGroup: "Core", equipment: "Bodyweight", isCompound: false },
  { name: "Pike Push-Up", muscleGroup: "Shoulders", equipment: "Bodyweight", isCompound: true },
  { name: "Prisoner Squat", muscleGroup: "Legs", equipment: "Bodyweight", isCompound: true },
  { name: "Lat Pulldown", muscleGroup: "Back", equipment: "Cable", isCompound: true },
  { name: "Seated Cable Row", muscleGroup: "Back", equipment: "Cable", isCompound: true },
  { name: "Cable Bicep Curl", muscleGroup: "Arms", equipment: "Cable", isCompound: false },
  { name: "Tricep Pushdown", muscleGroup: "Arms", equipment: "Cable", isCompound: false },
  { name: "Cable Fly", muscleGroup: "Chest", equipment: "Cable", isCompound: false },
  { name: "Face Pull", muscleGroup: "Shoulders", equipment: "Cable", isCompound: false },
  { name: "Leg Press", muscleGroup: "Legs", equipment: "Machine", isCompound: true },
  { name: "Leg Extension", muscleGroup: "Legs", equipment: "Machine", isCompound: false },
  { name: "Leg Curl", muscleGroup: "Legs", equipment: "Machine", isCompound: false },
  { name: "Calf Raise Machine", muscleGroup: "Legs", equipment: "Machine", isCompound: false },
  { name: "Chest Press Machine", muscleGroup: "Chest", equipment: "Machine", isCompound: true },
  { name: "Shoulder Press Machine", muscleGroup: "Shoulders", equipment: "Machine", isCompound: true },
  { name: "Pec Deck", muscleGroup: "Chest", equipment: "Machine", isCompound: false },
  { name: "Assisted Pull-Up", muscleGroup: "Back", equipment: "Machine", isCompound: true },
  { name: "Hip Thrust", muscleGroup: "Glutes", equipment: "Machine", isCompound: true },
  { name: "Kettlebell Swing", muscleGroup: "Glutes", equipment: "Kettlebell", isCompound: true },
  { name: "Kettlebell Goblet Squat", muscleGroup: "Legs", equipment: "Kettlebell", isCompound: true },
  { name: "Kettlebell Snatch", muscleGroup: "Back", equipment: "Kettlebell", isCompound: true },
  { name: "Kettlebell Press", muscleGroup: "Shoulders", equipment: "Kettlebell", isCompound: true },
  { name: "Banded Lateral Walk", muscleGroup: "Glutes", equipment: "Band", isCompound: false },
  { name: "Band Pull-Apart", muscleGroup: "Shoulders", equipment: "Band", isCompound: false },
];

export async function seedExercises(): Promise<number> {
  let created = 0;
  for (const ex of EXERCISES) {
    const existing = await prisma.exercise.findFirst({
      where: { ownerId: null, name: ex.name },
    });
    if (existing) continue;
    await prisma.exercise.create({
      data: {
        ownerId: null,
        name: ex.name,
        muscleGroup: ex.muscleGroup,
        equipment: ex.equipment,
        isCompound: ex.isCompound,
      },
    });
    created += 1;
  }
  return created;
}
