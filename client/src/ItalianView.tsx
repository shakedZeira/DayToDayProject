import { useEffect, useState } from "react";
import ItalianLesson from "./ItalianLesson";
import ItalianPath from "./ItalianPath";
import ItalianReview from "./ItalianReview";
import { getProgress } from "./italianApi";

interface Props {
  token: string;
}

const TABS = [
  { key: "path", label: "Path" },
  { key: "daily", label: "Daily Lesson" }
] as const;

type TabKey = (typeof TABS)[number]["key"];

export default function ItalianView({ token }: Props) {
  const [tab, setTab] = useState<TabKey>("path");
  const [reviewing, setReviewing] = useState(false);
  const [progress, setProgress] = useState<{ streak: number; totalLessons: number } | null>(null);

  useEffect(() => {
    getProgress(token)
      .then((p) => setProgress({ streak: p.streak, totalLessons: p.totalLessons }))
      .catch(() => setProgress(null));
  }, [token]);

  return (
    <div className="w-full max-w-3xl flex flex-col gap-4">
      <div className="flex border-b border-md-outlineVariant">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`flex-1 px-4 h-12 text-sm font-medium border-b-2 ${
              tab === t.key ? "border-md-primary text-indigo-700" : "border-transparent text-md-onSurfaceVariant hover:bg-md-surfaceVariant"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {reviewing ? (
        <ItalianReview token={token} onClose={() => setReviewing(false)} />
      ) : (
        <button
          className="self-start border border-md-outline rounded-full px-4 h-10 text-sm font-medium text-md-primary"
          onClick={() => setReviewing(true)}
        >
          Review
        </button>
      )}
      {reviewing ? null : tab === "path" ? (
        <ItalianPath token={token} />
      ) : (
        <ItalianLesson token={token} streak={progress?.streak} totalLessons={progress?.totalLessons} />
      )}
    </div>
  );
}
