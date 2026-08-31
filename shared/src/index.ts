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
