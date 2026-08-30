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
