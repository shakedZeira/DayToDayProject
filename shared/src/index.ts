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
export interface WorkoutExerciseView { id: string; order: number; exerciseId?: string | null; exerciseName: string; sets: WorkoutSetView[]; }
export interface ProgressiveSuggestion {
  exercise: string;
  currentWeightKg: number;
  suggestedNextKg: number;
  reason: "completed_target" | "not_yet";
}
export interface FoodInput { name: string; nameHe?: string | null; caloriesPer100: number; servingUnit?: string; }
export interface FoodRecord { id: string; name: string; nameHe?: string | null; caloriesPer100: number; servingUnit: string; }
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
  prs: { exerciseName: string; repRange: number; weight: number; e1rm: number; date: string }[];
}
export interface ProgressionUpdateInput {
  targetSets?: number; targetReps?: number; targetWeight?: number | null; incrementKg?: number;
}

export interface FlashcardOut {
  front: string;
  back: string;
}

export interface QuizOut {
  question: string;
  choices: string[];
  answerIndex: number;
  explanation?: string;
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

export interface DigestStudyItem {
  type: "pdf" | "flashcards" | "none";
  title: string | null;
  detail: string;
}

export interface DigestResponse {
  date: string; // local YYYY-MM-DD
  topTask: DigestTaskItem | null;
  tasksToday: DigestTaskItem[];
  health: DigestHealthItem;
  italian: DigestItalianItem;
  study: DigestStudyItem;
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

export interface ProgressStudySummary {
  pdfs: number;
  highlights: number;
  flashcards: number;
  pdfsInProgress: number;
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
  study: ProgressStudySummary;
  italian: ProgressItalianSummary;
}

export interface SettingsResponse {
  settings: Record<string, string>;
}
