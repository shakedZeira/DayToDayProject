import { useEffect, useState } from "react";
import ItalianLesson from "./ItalianLesson";
import { getProgress } from "./italianApi";

interface Props {
  token: string;
}

export default function ItalianView({ token }: Props) {
  const [progress, setProgress] = useState<{ streak: number; totalLessons: number } | null>(null);

  useEffect(() => {
    getProgress(token)
      .then((p) => setProgress({ streak: p.streak, totalLessons: p.totalLessons }))
      .catch(() => setProgress(null));
  }, [token]);

  return <ItalianLesson token={token} streak={progress?.streak} totalLessons={progress?.totalLessons} />;
}
