import { useEffect, useState } from "react";
import ItalianLesson from "./ItalianLesson";
import ItalianPath from "./ItalianPath";
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
  const [progress, setProgress] = useState<{ streak: number; totalLessons: number } | null>(null);

  useEffect(() => {
    getProgress(token)
      .then((p) => setProgress({ streak: p.streak, totalLessons: p.totalLessons }))
      .catch(() => setProgress(null));
  }, [token]);

  return (
    <div className="w-full max-w-3xl flex flex-col gap-4">
      <div className="flex gap-1 border-b">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 rounded-t font-medium ${
              tab === t.key ? "bg-emerald-600 text-white" : "text-gray-500 hover:text-gray-800"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === "path" ? (
        <ItalianPath token={token} />
      ) : (
        <ItalianLesson token={token} streak={progress?.streak} totalLessons={progress?.totalLessons} />
      )}
    </div>
  );
}
