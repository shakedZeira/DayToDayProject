import { useCallback, useEffect, useState } from "react";
import type { ItalianCorrection, ItalianLesson as Lesson } from "shared";
import { getTodaysLesson, checkAttempt, translateText } from "./italianApi";
import {
  startListening,
  speak,
  warmVoices,
  normalizeTranscript,
  isSpeechRecognitionSupported
} from "./speech";

type PracticeState =
  | { status: "idle" }
  | { status: "listening"; interim: string }
  | { status: "submitting" }
  | { status: "done"; correction: ItalianCorrection };

interface Props {
  token: string;
  streak?: number;
  totalLessons?: number;
}

export default function ItalianLesson({ token, streak, totalLessons }: Props) {
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activeIndex, setActiveIndex] = useState(0);
  const [practice, setPractice] = useState<PracticeState>({ status: "idle" });
  const [attemptText, setAttemptText] = useState("");
  const [tutorInput, setTutorInput] = useState("");
  const [tutorItalian, setTutorItalian] = useState<string | null>(null);
  const [tutorListening, setTutorListening] = useState(false);

  const speechSupported = isSpeechRecognitionSupported();
  const phrase = lesson?.phrases[activeIndex];

  const loadLesson = useCallback(async () => {
    try {
      const data = await getTodaysLesson(token);
      setLesson(data);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed to load the lesson");
    }
  }, [token]);

  useEffect(() => {
    loadLesson();
    warmVoices();
  }, [loadLesson]);

  async function submitAttempt(attempt: string) {
    if (!lesson || !phrase || !attempt) return;
    setPractice({ status: "submitting" });
    try {
      const correction = await checkAttempt(token, {
        dateKey: lesson.date,
        phraseIndex: activeIndex,
        userAttempt: attempt
      });
      setPractice({ status: "done", correction });
    } catch {
      setPractice({ status: "idle" });
      setError("Could not get feedback — is the server running?");
    }
  }

  function startRecite() {
    if (!phrase || practice.status !== "idle") return;
    let accumulated = "";
    setPractice({ status: "listening", interim: "" });
    startListening({
      lang: "it-IT",
      onResult: (r) => {
        accumulated = r.transcript;
        setPractice({ status: "listening", interim: r.transcript });
      },
      onEnd: () => {
        const attempt = normalizeTranscript(accumulated);
        if (attempt) submitAttempt(attempt);
        else setPractice({ status: "idle" });
      },
      onError: () => setPractice({ status: "idle" })
    });
  }

  function startTutor() {
    let accumulated = "";
    setTutorListening(true);
    setTutorItalian(null);
    startListening({
      lang: "en-US",
      onResult: (r) => {
        accumulated = r.transcript;
        setTutorInput(r.transcript);
      },
      onEnd: async () => {
        setTutorListening(false);
        const text = normalizeTranscript(accumulated);
        if (!text) return;
        try {
          const italian = await translateText(token, text);
          setTutorItalian(italian);
        } catch {
          setError("Translation failed — is the server running?");
        }
      },
      onError: () => setTutorListening(false)
    });
  }

  if (error) return <p className="text-red-600 text-sm">{error}</p>;
  if (!lesson) return <p className="text-slate-400 text-sm">Loading today's lesson…</p>;

  return (
    <section className="w-full max-w-2xl flex flex-col gap-5">
      <header className="flex items-baseline justify-between">
        <h2 className="text-xl font-semibold text-slate-800">{lesson.title}</h2>
        <span className="text-xs text-slate-500">{lesson.date}</span>
      </header>

      {streak !== undefined && totalLessons !== undefined && (
        <p className="text-xs text-slate-400">
          {streak} day streak · {totalLessons} lessons completed
        </p>
      )}

      <p className="text-sm text-slate-600 italic">{lesson.tip}</p>

      <div>
        <h3 className="font-semibold text-slate-700 mb-2">Vocab</h3>
        <ul className="grid grid-cols-2 gap-2">
          {lesson.vocab.map((v) => (
            <li key={v.italian} className="border rounded p-2 bg-white text-sm">
              <span className="text-slate-500">{v.english}</span>
              <span className="block font-medium text-slate-800">{v.italian}</span>
            </li>
          ))}
        </ul>
      </div>

      <div>
        <h3 className="font-semibold text-slate-700 mb-2">Phrases</h3>
        <ul className="flex flex-col gap-2 text-sm">
          {lesson.phrases.map((p, i) => (
            <li
              key={p.italian}
              className={`border rounded p-3 flex items-center justify-between gap-3 cursor-pointer ${
                i === activeIndex ? "border-indigo-400 bg-indigo-50" : "bg-white"
              }`}
              onClick={() => {
                setActiveIndex(i);
                setPractice({ status: "idle" });
              }}
            >
              <span className="text-slate-600">{p.english}</span>
              <span className="font-medium text-slate-800">{p.italian}</span>
            </li>
          ))}
        </ul>
      </div>

      {phrase && (
        <div className="border rounded bg-white p-4 flex flex-col gap-3">
          <h3 className="font-semibold text-slate-700">Practice — speak Italian</h3>
          <p className="text-slate-600">
            Say: <span className="font-medium text-slate-800">{phrase.english}</span>
          </p>

          {practice.status === "listening" && (
            <p className="text-sm text-indigo-600">Listening… "{practice.interim}"</p>
          )}
          {practice.status === "submitting" && <p className="text-sm text-slate-400">Checking…</p>}
          {practice.status === "done" && (
            <div className="flex flex-col gap-2 text-sm">
              <p className={practice.correction.isCorrect ? "text-green-700" : "text-red-700"}>
                {practice.correction.isCorrect ? "Correct — bravo!" : "Almost — keep trying."}
              </p>
              {!practice.correction.isCorrect && (
                <p className="text-slate-600">Hint: {practice.correction.hint}</p>
              )}
              <p className="text-slate-600">
                Correct form:{" "}
                <span className="font-medium text-slate-800">{practice.correction.correctPhrase}</span>
                <button
                  className="ml-2 text-indigo-600 underline"
                  onClick={() => speak(practice.correction.correctPhrase)}
                >
                  Hear it
                </button>
              </p>
              <div className="flex gap-2">
                <button
                  className="border border-slate-300 rounded px-3 py-1"
                  onClick={() => setPractice({ status: "idle" })}
                >
                  Try again
                </button>
                <button
                  className="border border-slate-300 rounded px-3 py-1"
                  onClick={() => {
                    setActiveIndex((prev) => (prev + 1) % lesson.phrases.length);
                    setPractice({ status: "idle" });
                  }}
                >
                  Next phrase
                </button>
              </div>
            </div>
          )}

          {practice.status === "idle" && (
            <div className="flex flex-col gap-2">
              <div className="flex gap-2 items-center">
                <button
                  className="bg-indigo-600 text-white rounded px-4 py-1"
                  onClick={startRecite}
                  disabled={!speechSupported}
                >
                  {speechSupported ? "Speak Italian" : "Mic unavailable"}
                </button>
                <span className="text-xs text-slate-400">
                  {speechSupported ? "uses your microphone (it-IT)" : "microphone unsupported — type below"}
                </span>
              </div>
              <div className="flex gap-2">
                <input
                  className="border rounded px-3 py-1 flex-1"
                  placeholder="…or type the Italian here"
                  value={attemptText}
                  onChange={(e) => setAttemptText(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      submitAttempt(normalizeTranscript(attemptText));
                      setAttemptText("");
                    }
                  }}
                />
                <button
                  className="border border-slate-300 rounded px-3 py-1"
                  onClick={() => {
                    submitAttempt(normalizeTranscript(attemptText));
                    setAttemptText("");
                  }}
                >
                  Submit
                </button>
              </div>
              <button
                className="self-start text-indigo-600 underline text-sm"
                onClick={() => speak(phrase.italian)}
              >
                Hear the correct Italian
              </button>
            </div>
          )}
        </div>
      )}

      <div className="border rounded bg-white p-4 flex flex-col gap-3">
        <h3 className="font-semibold text-slate-700">Tutor — speak English, learn Italian</h3>
        <div className="flex gap-2">
          <button
            className="bg-indigo-600 text-white rounded px-4 py-1"
            onClick={startTutor}
            disabled={!speechSupported || tutorListening}
          >
            {tutorListening ? "Listening…" : "Speak English"}
          </button>
          <input
            className="border rounded px-3 py-1 flex-1 text-sm"
            placeholder="…or type an English phrase"
            value={tutorInput}
            onChange={(e) => setTutorInput(e.target.value)}
            onKeyDown={async (e) => {
              if (e.key !== "Enter") return;
              const text = normalizeTranscript(tutorInput);
              if (!text) return;
              try {
                const italian = await translateText(token, text);
                setTutorItalian(italian);
              } catch {
                setError("Translation failed — is the server running?");
              }
            }}
          />
        </div>
        {tutorItalian && (
          <p className="text-sm text-slate-700">
            <span className="font-medium text-slate-800">{tutorItalian}</span>{" "}
            <button className="ml-2 text-indigo-600 underline" onClick={() => speak(tutorItalian)}>
              Hear it
            </button>
          </p>
        )}
      </div>
    </section>
  );
}
