import { useEffect, useState } from "react";
import type { ItalianPathProgress } from "shared";
import { getPathProgress } from "./italianApi";

interface Props {
  token: string;
}

export default function ItalianGoalCard({ token }: Props) {
  const [progress, setProgress] = useState<ItalianPathProgress | null>(null);

  useEffect(() => {
    getPathProgress(token)
      .then(setProgress)
      .catch(() => setProgress(null));
  }, [token]);

  if (!progress) return null;

  const pct = progress.totalLessons > 0 ? Math.round((progress.completedLessonIds.length / progress.totalLessons) * 100) : 0;

  return (
    <div className="border rounded-lg bg-white p-4 flex flex-col gap-2">
      <div className="flex justify-between text-sm font-medium">
        <span>🇮🇹 Italian path</span>
        <span className="text-gray-500">
          {progress.xp} XP · {progress.streak} day streak
        </span>
      </div>
      <div className="h-2 bg-gray-100 rounded">
        <div className="h-2 bg-emerald-500 rounded" style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs text-gray-500">
        {progress.completedLessonIds.length}/{progress.totalLessons} lessons ({pct}%)
        {progress.reviewDue.length > 0 ? ` · ${progress.reviewDue.length} to review` : ""}
      </span>
    </div>
  );
}
