export interface SpeechResult {
  transcript: string;
  isFinal: boolean;
}

interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  start(): void;
  stop(): void;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onend: (() => void) | null;
  onerror: ((event: { error: string }) => void) | null;
}

interface SpeechRecognitionEventLike {
  resultIndex: number;
  results: {
    readonly length: number;
    [index: number]: {
      isFinal: boolean;
      readonly length: number;
      [index: number]: { transcript: string };
    };
  };
}

declare global {
  interface Window {
    SpeechRecognition?: { new (): SpeechRecognitionLike };
    webkitSpeechRecognition?: { new (): SpeechRecognitionLike };
  }
}

export function isSpeechRecognitionSupported(): boolean {
  return (
    typeof window !== "undefined" &&
    (("SpeechRecognition" in window) as boolean || ("webkitSpeechRecognition" in window) as boolean)
  );
}

export function createRecognizer(lang: string): SpeechRecognitionLike | null {
  if (!isSpeechRecognitionSupported()) return null;
  const Ctor = window.SpeechRecognition ?? window.webkitSpeechRecognition;
  if (!Ctor) return null;
  const rec = new Ctor();
  rec.lang = lang;
  rec.interimResults = true;
  rec.continuous = false;
  return rec;
}

export function startListening(opts: {
  lang?: string;
  onResult?: (result: SpeechResult) => void;
  onEnd?: () => void;
  onError?: (message: string) => void;
}): () => void {
  const rec = createRecognizer(opts.lang ?? "en-US");
  if (!rec) {
    opts.onError?.("speech recognition is not supported in this browser");
    return () => {};
  }
  let finalTranscript = "";
  rec.onresult = (event) => {
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const result = event.results[i];
      if (result.isFinal) finalTranscript += result[0].transcript;
    }
    const interim = Array.from({ length: event.results.length }, (_, i) => event.results[i])
      .filter((r) => !r.isFinal)
      .map((r) => r[0].transcript)
      .join("");
    opts.onResult?.({
      transcript: finalTranscript + interim,
      isFinal: finalTranscript.length > 0 && interim === ""
    });
  };
  rec.onerror = (event) => opts.onError?.(event.error);
  rec.onend = () => opts.onEnd?.();
  rec.start();
  return () => {
    try {
      rec.stop();
    } catch {
      /* already stopped */
    }
  };
}

function isItalian(lang: string): boolean {
  return lang.toLowerCase().replace("_", "-").startsWith("it");
}

export function preferVoice(voices: SpeechSynthesisVoice[]): SpeechSynthesisVoice | null {
  const itIT = voices.find((v) => v.lang.toLowerCase().replace("_", "-").startsWith("it-it"));
  if (itIT) return itIT;
  const anyIt = voices.find((v) => isItalian(v.lang));
  return anyIt ?? null;
}

export function speak(text: string, lang = "it-IT"): void {
  if (typeof window === "undefined" || !("speechSynthesis" in window)) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = lang;
  const voice = preferVoice(window.speechSynthesis.getVoices());
  if (voice) utterance.voice = voice;
  window.speechSynthesis.speak(utterance);
}

export function warmVoices(): Promise<void> {
  if (typeof window === "undefined" || !("speechSynthesis" in window)) return Promise.resolve();
  return new Promise((resolve) => {
    if (window.speechSynthesis.getVoices().length > 0) {
      resolve();
      return;
    }
    const onChange = () => {
      resolve();
      window.speechSynthesis.removeEventListener("voiceschanged", onChange);
    };
    window.speechSynthesis.addEventListener("voiceschanged", onChange);
  });
}

export function normalizeTranscript(transcript: string): string {
  return transcript.replace(/\s+/g, " ").trim();
}
