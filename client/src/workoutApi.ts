import { authedFetch } from "./auth";
import type { WorkoutCreateInput, WorkoutSetInput, ProgressiveSuggestion, WorkoutExerciseView } from "shared";

export interface WorkoutSet extends WorkoutSetInput {
  id: string;
  ownerId: string;
  workoutId: string;
  createdAt: string;
}

export interface Workout {
  id: string;
  ownerId: string;
  title: string;
  date: string;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  sets: WorkoutSet[];
  exercises: WorkoutExerciseView[];
}

export interface WorkoutSummary {
  id: string;
  title: string;
  date: string;
  notes: string | null;
  sets: Pick<WorkoutSet, "exercise" | "weightKg" | "reps">[];
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getWorkouts(token: string): Promise<WorkoutSummary[]> {
  const res = await authedFetch(token, "/api/workouts");
  return json<WorkoutSummary[]>(res);
}

export async function getWorkout(token: string, id: string): Promise<Workout> {
  const res = await authedFetch(token, `/api/workouts/${id}`);
  return json<Workout>(res);
}

export async function createWorkout(token: string, input: WorkoutCreateInput): Promise<Workout> {
  const res = await authedFetch(token, "/api/workouts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<Workout>(res);
}

export async function appendSets(token: string, id: string, sets: WorkoutSetInput[]): Promise<WorkoutSet[]> {
  const res = await authedFetch(token, `/api/workouts/${id}/sets`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sets }),
  });
  return json<WorkoutSet[]>(res);
}

export async function getProgressive(token: string, id: string): Promise<ProgressiveSuggestion[]> {
  const res = await authedFetch(token, `/api/workouts/${id}/progressive`);
  return json<ProgressiveSuggestion[]>(res);
}

export async function addWorkoutExercise(
  token: string,
  workoutId: string,
  input: { exerciseId?: string; exerciseName?: string; notes?: string },
): Promise<WorkoutExerciseView> {
  const res = await authedFetch(token, `/api/workouts/${workoutId}/exercises`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<WorkoutExerciseView>(res);
}

export async function appendSetToExercise(
  token: string,
  workoutId: string,
  xeId: string,
  input: { weightKg: number; reps: number; setType?: string; restSeconds?: number; rpe?: number },
): Promise<WorkoutSet> {
  const res = await authedFetch(token, `/api/workouts/${workoutId}/exercises/${xeId}/sets`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return json<WorkoutSet>(res);
}
