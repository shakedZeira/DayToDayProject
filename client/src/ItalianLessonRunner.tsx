import { useMemo, useState } from "react";
import type { ItalianAnswer, ItalianExercise, ItalianLessonDef, ItalianSubmitResult } from "shared";
import { submitPathLesson } from "./italianApi";
import { gradeExercise } from "./italianPathLogic";

interface Props {
  token: string;
  lesson: ItalianLessonDef;
  onDone: (result: ItalianSubmitResult | null) => void;
}

interface Picks {
  choicePick: number | null;
  matchPicks: { left: string; right: string }[];
}

export function speakIt(text: string) {
  const u = new SpeechSynthesisUtterance(text);
  u.lang = "it-IT";
  window.speechSynthesis.speak(u);
}

function shuffled<T>(items: T[]): T[] {
  return [...items].sort(() => Math.random() - 0.5);
}

export function correctText(ex: ItalianExercise): string {
  if (ex.kind === "choice") return ex.choices[ex.answerIndex];
  return ex.pairs.map((p) => `${p.left} = ${p.right}`).join(", ");
}

export function buildAnswer(ex: ItalianExercise, picks: Picks): ItalianAnswer {
  if (ex.kind === "choice") return { exerciseId: ex.id, kind: "choice", choiceIndex: picks.choicePick ?? -1 };
  return { exerciseId: ex.id, kind: "match", pairs: picks.matchPicks };
}

export function nextHearts(hearts: number, correct: boolean): number {
  return correct ? hearts : Math.max(hearts - 1, 0);
}

export default function ItalianLessonRunner({ token, lesson, onDone }: Props) {
  const [idx, setIdx] = useState(0);
  const [hearts, setHearts] = useState(5);
  const [answers, setAnswers] = useState<ItalianAnswer[]>([]);
  const [feedback, setFeedback] = useState<{ correct: boolean } | null>(null);
  const [choicePick, setChoicePick] = useState<number | null>(null);
  const [matchPicks, setMatchPicks] = useState<{ left: string; right: string }[]>([]);
  const [pickLeft, setPickLeft] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const ex = lesson.exercises[idx];
  const rights = useMemo<string[]>(
    () => (ex && ex.kind === "match" ? shuffled(ex.pairs.map((p) => p.right)) : []),
    [ex]
  );

  async function finish(list: ItalianAnswer[]) {
    setSubmitting(true);
    try {
      const result = await submitPathLesson(token, lesson.id, list);
      onDone(result);
    } catch {
      onDone(null);
    }
  }

  function picks(): Picks {
    return { choicePick, matchPicks };
  }

  function onCheck() {
    const answer = buildAnswer(ex, picks());
    const correct = gradeExercise(ex, answer);
    setFeedback({ correct });
    if (!correct) setHearts((h) => nextHearts(h, false));
  }

  function onContinue() {
    const answer = buildAnswer(ex, picks());
    const next = [...answers, answer];
    setAnswers(next);
    setFeedback(null);
    setChoicePick(null);
    setMatchPicks([]);
    setPickLeft(null);
    setIdx((i) => i + 1);
    if (idx + 1 >= lesson.exercises.length || hearts <= 0) void finish(next);
  }

  if (submitting) {
    return <div className="border rounded-lg p-6 bg-white text-center">Saving your lesson…</div>;
  }

  if (!ex) return null;

  const pairedLefts = new Set(matchPicks.map((m) => m.left));
  const usedRights = new Set(matchPicks.map((m) => m.right));

  return (
    <div className="border rounded-lg bg-white p-6 flex flex-col gap-4">
      <div className="flex justify-between text-sm text-gray-500">
        <span>
          {lesson.title} · {idx + 1}/{lesson.exercises.length}
        </span>
        <span className="text-red-600">{"❤️".repeat(Math.max(hearts, 0))}</span>
      </div>

      <div className="h-2 bg-gray-100 rounded">
        <div
          className="h-2 bg-[#58CC02] rounded"
          style={{ width: `${(idx / lesson.exercises.length) * 100}%` }}
        />
      </div>

      <p className="text-lg font-medium">
        {ex.kind === "match" ? "Match the pairs" : ex.prompt}
      </p>

      {ex.kind === "choice" && ex.direction === "listen" && (
        <button
          className="self-start border rounded px-4 py-2 text-2xl"
          onClick={() => speakIt(ex.speak ?? "")}
        >
          🔊 Play
        </button>
      )}

      {ex.kind === "choice" && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {ex.choices.map((c, i) => (
            <button
              key={c}
              onClick={() => !feedback && setChoicePick(i)}
              className={`border rounded px-4 py-3 text-left ${
                choicePick === i ? "border-[#58CC02] bg-[#58CC02]/10" : "border-gray-300"
              }`}
            >
              {c}
            </button>
          ))}
        </div>
      )}

      {ex.kind === "match" && (
        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-2">
            {ex.pairs.map((p) => (
              <button
                key={p.left}
                onClick={() => {
                  if (feedback) return;
                  if (pairedLefts.has(p.left)) {
                    setMatchPicks((m) => m.filter((x) => x.left !== p.left));
                    return;
                  }
                  setPickLeft(p.left);
                }}
                className={`border rounded px-3 py-2 ${
                  pickLeft === p.left ? "border-[#58CC02] bg-[#58CC02]/10" : "border-gray-300"
                } ${pairedLefts.has(p.left) ? "opacity-40" : ""}`}
              >
                {p.left}
              </button>
            ))}
          </div>
          <div className="flex flex-col gap-2">
            {rights.map((r) => (
              <button
                key={r}
                onClick={() => {
                  if (feedback || !pickLeft || usedRights.has(r)) return;
                  setMatchPicks((m) => [...m, { left: pickLeft, right: r }]);
                  setPickLeft(null);
                }}
                className={`border rounded px-3 py-2 ${
                  pickLeft ? "border-gray-300" : "border-gray-200"
                } ${usedRights.has(r) ? "opacity-40" : ""}`}
              >
                {r}
              </button>
            ))}
          </div>
        </div>
      )}

      {feedback && (
        <div
          className={`rounded px-4 py-3 text-sm ${
            feedback.correct ? "bg-[#58CC02]/20 text-[#58A700]" : "bg-red-100 text-red-800"
          }`}
        >
          {feedback.correct ? "Correct!" : `Incorrect — correct answer: ${correctText(ex)}`}
        </div>
      )}

      <div className="flex justify-end">
        {!feedback ? (
          <button
            className="bg-[#58CC02] text-white rounded px-6 py-2 disabled:opacity-40"
            disabled={
              (ex.kind === "choice" && choicePick === null) ||
              (ex.kind === "match" && matchPicks.length !== ex.pairs.length)
            }
            onClick={onCheck}
          >
            Check
          </button>
        ) : (
          <button className="bg-[#58CC02] text-white rounded px-6 py-2" onClick={onContinue}>
            Continue
          </button>
        )}
      </div>
    </div>
  );
}
