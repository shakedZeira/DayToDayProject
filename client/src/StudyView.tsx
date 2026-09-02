import { useCallback, useEffect, useState } from "react";
import { getStudy, generateStudy, type FlashcardRow, type QuizRow } from "./studyApi";

interface Props { token: string; pdfId: string; }

export default function StudyView({ token, pdfId }: Props) {
  const [cards, setCards] = useState<FlashcardRow[]>([]);
  const [quizzes, setQuizzes] = useState<QuizRow[]>([]);
  const [flipped, setFlipped] = useState<Record<string, boolean>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [answers, setAnswers] = useState<Record<string, number>>({});

  const refresh = useCallback(async () => {
    const data = await getStudy(token, pdfId);
    setCards(data.cards);
    setQuizzes(data.quizzes);
  }, [token, pdfId]);

  useEffect(() => { refresh().catch(() => {}); }, [refresh]);

  async function onGenerate(kind: "flashcards" | "quiz") {
    setBusy(true); setError(null);
    try {
      await generateStudy(token, pdfId, kind, 5);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "generation failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      <div className="flex gap-3">
        <button onClick={() => onGenerate("flashcards")} disabled={busy}
          className="bg-indigo-600 text-white rounded px-4 py-2 disabled:opacity-50">
          {busy ? "Generating\u2026" : "Generate flashcards"}
        </button>
        <button onClick={() => onGenerate("quiz")} disabled={busy}
          className="bg-emerald-600 text-white rounded px-4 py-2 disabled:opacity-50">
          Generate quiz
        </button>
      </div>
      {error && <p className="text-red-600 text-sm">{error}</p>}

      <div>
        <h3 className="font-semibold text-slate-700 mb-2">Flashcards</h3>
        {cards.length === 0 && <p className="text-sm text-slate-400">No cards yet.</p>}
        <ul className="flex flex-col gap-2">
          {cards.map((c) => {
            const f = flipped[c.id] ?? false;
            return (
              <li key={c.id} onClick={() => setFlipped((p) => ({ ...p, [c.id]: !f }))}
                className="cursor-pointer border rounded p-4 bg-white min-h-20">
                {f ? <p className="text-slate-700">{c.back}</p> : <p className="font-medium">{c.front}</p>}
                <p className="text-xs text-slate-400 mt-2">{f ? "click to flip" : "click to reveal"}</p>
              </li>
            );
          })}
        </ul>
      </div>

      <div>
        <h3 className="font-semibold text-slate-700 mb-2">Quiz</h3>
        {quizzes.length === 0 && <p className="text-sm text-slate-400">No questions yet.</p>}
        <ul className="flex flex-col gap-4">
          {quizzes.map((q) => (
            <li key={q.id} className="border rounded p-4 bg-white">
              <p className="font-medium mb-2">{q.question}</p>
              {q.options.map((opt, i) => {
                const chosen = answers[q.id] === i;
                const isCorrect = chosen && i === q.answerIndex;
                return (
                  <button key={opt.id}
                    onClick={() => setAnswers((p) => ({ ...p, [q.id]: i }))}
                    className={`block w-full text-left border rounded px-3 py-1 mb-1 ${
                      chosen ? (isCorrect ? "bg-green-100 border-green-400" : "bg-red-100 border-red-400")
                      : "bg-slate-50"}`}>
                    {opt.text}
                  </button>
                );
              })}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
