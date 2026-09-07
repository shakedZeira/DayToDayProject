import type { FlashcardOut, QuizOut } from "shared";

export interface LLMProvider {
  generateFlashcards(material: string, count: number): Promise<FlashcardOut[]>;
  generateQuiz(material: string, count: number): Promise<QuizOut[]>;
  complete(prompt: string): Promise<string>;
}

function clampCount(count: number, max: number): number {
  return Math.max(1, Math.min(count, max));
}

function stripCodeFence(text: string): string {
  const match = text.match(/```(?:json)?\s*([\s\S]*?)```/);
  return match ? match[1] : text;
}

async function httpComplete(system: string, user: string): Promise<string> {
  const url = process.env.LLM_HTTP_URL || "https://api.groq.com/openai/v1/chat/completions";
  const key = process.env.LLM_HTTP_KEY || "";
  if (!key) throw new Error("LLM_HTTP_KEY not configured (free-tier key)");
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${key}`
    },
    body: JSON.stringify({
      model: process.env.LLM_MODEL || "llama-3.1-8b-instant",
      temperature: 0.7,
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: system },
        { role: "user", content: user }
      ]
    })
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`LLM request failed ${res.status}: ${body.slice(0, 200)}`);
  }
  const data = await res.json();
  return data.choices?.[0]?.message?.content ?? "{}";
}

export function llmHttpProvider(): LLMProvider {
  return {
    async generateFlashcards(material, count): Promise<FlashcardOut[]> {
      const n = clampCount(count, 20);
      const raw = await httpComplete(
        "You generate study flashcards as JSON. Respond ONLY with JSON of the form {\"cards\":[{\"front\":string,\"back\":string}]}.",
        `Create exactly ${n} flashcards from this material:\n\n${material.slice(0, 12000)}`
      );
      const parsed = JSON.parse(stripCodeFence(raw));
      const cards: FlashcardOut[] = Array.isArray(parsed?.cards) ? parsed.cards : [];
      return cards.slice(0, n).filter((c) => c && typeof c.front === "string" && typeof c.back === "string");
    },
    async generateQuiz(material, count): Promise<QuizOut[]> {
      const n = clampCount(count, 10);
      const raw = await httpComplete(
        "You generate multiple-choice quiz questions as JSON. Respond ONLY with JSON of the form {\"questions\":[{\"question\":string,\"choices\":string[4],\"answerIndex\":number,\"explanation\":string}]}.",
        `Create exactly ${n} questions from this material:\n\n${material.slice(0, 12000)}`
      );
      const parsed = JSON.parse(stripCodeFence(raw));
      const qs: QuizOut[] = Array.isArray(parsed?.questions) ? parsed.questions : [];
      return qs.slice(0, n).filter((q) => q && typeof q.question === "string" && Array.isArray(q.choices) && q.choices.length >= 2 && typeof q.answerIndex === "number");
    },
    async complete(prompt: string): Promise<string> {
      return httpComplete(
        "You are a helpful assistant. Reply concisely using the exact format the user asks for.",
        prompt
      );
    }
  };
}

function mockProvider(): LLMProvider {
  return {
    async generateFlashcards(material, count) {
      return useStatefulCards(material, count);
    },
    async generateQuiz(material, count) {
      return useStatefulQuiz(material, count);
    },
    async complete(_prompt: string): Promise<string> {
      return '{"fallback":"canned"}';
    }
  };
}

function cardText(material: string): string {
  const a = material.split(/\s+/).filter(Boolean);
  if (a.length >= 6) return a.slice(0, 6).join(" ");
  return material.slice(0, 40) || "concept";
}

function useStatefulCards(material: string, count: number): FlashcardOut[] {
  const n = Math.max(1, Math.min(count, 20));
  const base = cardText(material);
  return Array.from({ length: n }, (_, i) => ({
    front: `Q${i + 1}: ${base}…`,
    back: `A${i + 1}: Restate the key idea of "${base.slice(0, 24)}…".`
  }));
}

function useStatefulQuiz(material: string, count: number): QuizOut[] {
  const n = Math.max(1, Math.min(count, 10));
  const base = cardText(material);
  return Array.from({ length: n }, (_, i) => ({
    question: `What is a key takeaway from "${base.slice(0, 40)}"?`,
    choices: ["Option A", "Option B", "Option C", "Option D"],
    answerIndex: 1,
    explanation: `Derived from the material: ${base.slice(0, 60)}`
  }));
}

export function getLLMProvider(): LLMProvider {
  const kind = (process.env.LLM_PROVIDER || "http").toLowerCase();
  if (kind === "mock") return mockProvider();
  return llmHttpProvider();
}

export { mockProvider as llmMockProvider };

export function extractJson(text: string): Record<string, unknown> | null {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start === -1 || end === -1 || end <= start) return null;
  try {
    const parsed: unknown = JSON.parse(text.slice(start, end + 1));
    return typeof parsed === "object" && parsed !== null ? (parsed as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}
