export interface HealthResponse {
  status: "ok";
  time: string;
}

export interface TaskCreateInput {
  title: string;
  notes?: string | null;
  dueAt?: string | null;
  recurrence?: string;
  category?: string | null;
}

export interface WeeklyGoalView {
  id: string;
  title: string;
  targetCount: number;
  unit?: string | null;
  category?: string | null;
  autoSource?: string | null;
  thisWeekCount: number;
  lastEventAt?: string | null;
}

export interface WorkoutCreateInput { title: string; date?: string; notes?: string | null; }
export interface WorkoutSetInput { exercise: string; weightKg: number; reps: number; }
export interface ProgressiveSuggestion {
  exercise: string;
  currentWeightKg: number;
  suggestedNextKg: number;
  reason: "completed_target" | "not_yet";
}
export interface FoodInput { name: string; caloriesPer100: number; servingUnit?: string; }
export interface FoodRecord { id: string; name: string; caloriesPer100: number; servingUnit: string; }
export interface MealLogInput { foodId: string; grams: number; date?: string; }
export interface DailyTargetInput { calories: number; }
export interface NutritionSummary {
  date: string;
  target: number;
  consumed: number;
  remaining: number;
  meals: { id: string; foodName: string; grams: number; calories: number }[];
}

export interface VisionEstimate {
  provider: string;
  items: { foodName: string; estimatedGrams: number; estimatedCalories: number }[];
  totalCalories: number;
  disclaimer: string;
}
