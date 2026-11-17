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
export interface WorkoutSetInput { exercise: string; weightKg: number; reps: number; setType?: string; restSeconds?: number; rpe?: number; }
export interface WorkoutSetView { id: string; exercise: string; weightKg: number; reps: number; setType: string; order: number; restSeconds?: number | null; rpe?: number | null; }
export interface WorkoutExerciseView { id: string; order: number; exerciseId?: string | null; exerciseName: string; supersetGroup?: number | null; sets: WorkoutSetView[]; }
export interface ProgressiveSuggestion {
  exercise: string;
  currentWeightKg: number;
  suggestedNextKg: number;
  reason: "completed_target" | "not_yet";
}
export interface FoodInput {
  name: string; nameHe?: string | null; caloriesPer100: number; servingUnit?: string;
  proteinPer100?: number; carbsPer100?: number; fatPer100?: number; barcode?: string | null;
}
export interface FoodRecord {
  id: string; name: string; nameHe?: string | null; caloriesPer100: number; servingUnit: string;
  proteinPer100: number; carbsPer100: number; fatPer100: number;
  barcode?: string | null;
  /** true when the food was created by the current user (custom food). */
  custom?: boolean;
}
export interface MacroTotals { proteinG: number; carbsG: number; fatG: number; }
export interface BarcodeFoodResult {
  barcode: string;
  source: "mine" | "openfoodfacts";
  /** Set when the product already exists as one of the user's foods. */
  foodId?: string;
  food: FoodInput & { name: string };
}
export interface MealLogInput { foodId: string; grams: number; date?: string; }
export interface DailyTargetInput { calories: number; }
export interface NutritionSummary {
  date: string;
  target: number;
  consumed: number;
  remaining: number;
  /** Macro totals consumed today, in grams. */
  macros: MacroTotals;
  /** Macro goals derived from the calorie target (30% protein / 45% carbs / 25% fat). */
  macroTargets: MacroTotals;
  meals: { id: string; foodName: string; grams: number; servings?: number; calories: number; proteinG: number; carbsG: number; fatG: number }[];
}

export interface VisionEstimate {
  provider: string;
  items: { foodName: string; estimatedGrams: number; estimatedCalories: number }[];
  totalCalories: number;
  disclaimer: string;
}

export type ActivityLevel =
  | "sedentary" | "light" | "moderate" | "active" | "very_active";

export interface UserProfile {
  heightCm: number;
  weightKg: number;
  age: number;
  sex: "male" | "female";
  activity: ActivityLevel;
}

export interface ProfileInput extends UserProfile {}

export interface CalorieRecommendation {
  bmr: number;
  tdee: number;
  activity: ActivityLevel;
  maintenance: number;
  weightLoss: number;
  weightGain: number;
  method: string;
  disclaimer: string;
}

export interface ExerciseRecord {
  id: string; name: string; muscleGroup: string; equipment: string;
  isCompound: boolean; isCustom: boolean;
}
export interface ExerciseCreateInput { name: string; muscleGroup?: string; equipment?: string; isCompound?: boolean; }

export interface RoutineExerciseInput {
  order: number; targetSets: number; targetReps: number; targetWeight?: number | null;
  notes?: string | null; exerciseId: string;
  /** Exercises of one day sharing a group id are performed as a superset. */
  supersetGroup?: number | null;
}
export interface RoutineDayInput { name: string; order?: number; exercises?: RoutineExerciseInput[]; }
export interface RoutineCreateInput { name: string; description?: string | null; days?: RoutineDayInput[]; }
export interface RoutineDayView {
  id: string; name: string; order: number;
  exercises: (RoutineExerciseInput & { id: string; exercise: ExerciseRecord })[];
}
export interface RoutineView {
  id: string; name: string; description: string | null;
  days: RoutineDayView[];
}

export interface ProgressiveOverloadView {
  exerciseName: string; targetSets: number; targetReps: number;
  targetWeight?: number | null; incrementKg: number; lastProgressed?: string | null;
}
export interface AnalyticsSummary {
  totalWorkouts: number; thisWeekWorkouts: number;
  volumeTrend: { date: string; kg: number }[];
  muscleVolume: { muscle: string; kg: number }[];
  /** Last 8 ISO weeks (Monday start), oldest first; one entry per muscle per week with working sets. */
  weeklyMuscleVolume: { weekStart: string; muscle: string; sets: number; kg: number }[];
  prs: { exerciseName: string; repRange: number; weight: number; e1rm: number; date: string }[];
}
export interface DeloadSuggestion {
  exerciseName: string;
  reason: "e1rm_drop" | "stalled";
  detail: string;
  currentWeightKg: number;
  suggestedWeightKg: number;
}
export interface ProgressionUpdateInput {
  targetSets?: number; targetReps?: number; targetWeight?: number | null; incrementKg?: number;
}

export interface ItalianVocabItem {
  english: string;
  italian: string;
}

export interface ItalianPhrase {
  english: string;
  italian: string;
}

export interface ItalianLesson {
  id: string;
  date: string; // YYYY-MM-DD
  title: string;
  tip: string;
  vocab: ItalianVocabItem[];
  phrases: ItalianPhrase[];
}

export type MistakeType =
  | "correct"
  | "minor"
  | "vocabulary"
  | "grammar"
  | "word-order"
  | "incomplete"
  | "other";

export interface ItalianCorrection {
  isCorrect: boolean;
  phraseEnglish: string;
  targetItalian: string;
  userAttempt: string;
  mistakeType: MistakeType;
  hint: string;
  correctPhrase: string;
}

export interface ItalianProgress {
  streak: number;       // consecutive active days ending at the anchor date
  totalLessons: number; // lessons with completedAt set
  totalPractice: number; // practice attempts logged
}

export interface DigestTaskItem {
  id: string;
  title: string;
  category: string | null;
  recurrence: string;
  status: "PENDING" | "DONE";
}

export interface DigestHealthItem {
  workoutsThisWeek: number;
  lastWorkoutDaysAgo: number | null;
  reminder: string | null;
}

export interface DigestItalianItem {
  lessonTitle: string | null;
  streak: number;
  totalLessons: number;
}

export interface DigestResponse {
  date: string; // local YYYY-MM-DD
  topTask: DigestTaskItem | null;
  tasksToday: DigestTaskItem[];
  health: DigestHealthItem;
  italian: DigestItalianItem;
}

export interface ProgressTaskSummary {
  total: number;
  completedThisWeek: number;
  completionRateWeek: number;
  streak: number;
  completedByDay: number[]; // 7 entries, oldest first, ending today
}

export interface ProgressHealthSummary {
  workoutsThisWeek: number;
  workoutsTarget: number;
  caloriesToday: number;
  calorieTarget: number;
}

export interface ProgressItalianSummary {
  streak: number;
  totalLessons: number;
  lessonsThisWeek: number;
}

export interface ProgressResponse {
  date: string; // local YYYY-MM-DD
  tasks: ProgressTaskSummary;
  health: ProgressHealthSummary;
  italian: ProgressItalianSummary;
}

export interface SettingsResponse {
  settings: Record<string, string>;
}

export interface ItalianChoiceExercise {
  id: string;
  kind: "choice";
  direction: "en_to_it" | "it_to_en" | "listen";
  prompt: string;
  choices: string[];
  answerIndex: number;
  speak?: string;
}

export interface ItalianMatchExercise {
  id: string;
  kind: "match";
  pairs: { left: string; right: string }[];
}

export type ItalianExercise = ItalianChoiceExercise | ItalianMatchExercise;

export interface ItalianLessonDef {
  id: string;
  title: string;
  /** "test" marks the review quiz at the end of a unit. */
  kind?: "lesson" | "test";
  exercises: ItalianExercise[];
}

export interface ItalianUnitDef {
  id: string;
  title: string;
  lessons: ItalianLessonDef[];
}

export interface ItalianCourse {
  language: "it";
  units: ItalianUnitDef[];
}

export type ItalianAnswer =
  | { exerciseId: string; kind: "choice"; choiceIndex: number }
  | { exerciseId: string; kind: "match"; pairs: { left: string; right: string }[] };

export interface ItalianReviewDue {
  lessonId: string;
  exerciseId: string;
}

export interface ItalianPathProgress {
  xp: number;
  streak: number;
  completedLessonIds: string[];
  totalLessons: number;
  reviewDue: ItalianReviewDue[];
}

export interface ItalianSubmitResult {
  results: { exerciseId: string; isCorrect: boolean }[];
  xpGained: number;
  lessonCompleted: boolean;
  progress: ItalianPathProgress;
}

export interface RecipeIngredient {
  name: string;
  amount: number;
  unit: string;
}

export interface RecipeInstructionStep {
  step: number;
  text: string;
  timerSeconds?: number | null;
}

export type RecipeMeal = "breakfast" | "lunch" | "dinner" | "snack";
export type RecipeDifficulty = "beginner" | "intermediate" | "advanced";

export interface Recipe {
  id: string;
  ownerId: string | null;
  title: string;
  description: string | null;
  meal: RecipeMeal;
  difficulty: RecipeDifficulty;
  prepTimeMin: number;
  cookTimeMin: number;
  servings: number;
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  ingredients: RecipeIngredient[];
  instructions: RecipeInstructionStep[];
  imageUrl: string | null;
  source: string | null;
  tags: string | null;
  createdAt: string;
  updatedAt: string;
  favoriteCount?: number;
  isFavorited?: boolean;
}

export interface RecipeCreateInput {
  title: string;
  description?: string | null;
  meal: RecipeMeal;
  difficulty: RecipeDifficulty;
  prepTimeMin: number;
  cookTimeMin: number;
  servings: number;
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  ingredients: RecipeIngredient[];
  instructions: RecipeInstructionStep[];
  imageUrl?: string | null;
  source?: string | null;
  tags?: string | null;
}

export interface RecipeView {
  id: string;
  title: string;
  description: string | null;
  meal: RecipeMeal;
  difficulty: RecipeDifficulty;
  prepTimeMin: number;
  cookTimeMin: number;
  servings: number;
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  ingredients: RecipeIngredient[];
  instructions: RecipeInstructionStep[];
  imageUrl: string | null;
  source: string | null;
  tags: string[] | null;
  createdAt: string;
  updatedAt: string;
  favoriteCount: number;
  isFavorited: boolean;
}

export interface ShoppingItemView {
  id: string;
  name: string;
  amount: number;
  unit: string;
  checked: boolean;
  recipeId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PantryItemView {
  id: string;
  name: string;
  amount: number | null;
  unit: string;
  createdAt: string;
  updatedAt: string;
}

export interface MealPlanEntryView {
  id: string;
  date: string;
  slot: RecipeMeal;
  recipeId: string;
  recipeTitle: string;
  servings: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface WeeklyReviewPR {
  exerciseName: string;
  repRange: number;
  weight: number;
  e1rm: number;
  date: string;
}

export interface WeeklyReviewResponse {
  from: string; // local YYYY-MM-DD, first day of the 7-day window
  to: string; // local YYYY-MM-DD, last day (today unless ?date= given)
  tasks: { completed: number; completedByDay: number[] };
  workouts: { count: number; target: number; days: string[] };
  nutrition: {
    targetCalories: number;
    daysLogged: number;
    avgCalories: number;
    diffFromTarget: number; // avgCalories - targetCalories (0 when nothing logged)
    byDay: { date: string; calories: number }[];
  };
  italian: { lessonsCompleted: number; xpEarned: number; streak: number };
  personalRecords: WeeklyReviewPR[];
}
