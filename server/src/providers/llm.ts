export interface ChatMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

export interface LLMProvider {
  complete(prompt: string): Promise<string>;
  chat(system: string, messages: ChatMessage[]): Promise<string>;
  chatStream(system: string, messages: ChatMessage[], onDelta: (delta: string) => void): Promise<void>;
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

async function httpChat(system: string, messages: ChatMessage[]): Promise<string> {
  const url =
    process.env.LLM_CHAT_URL ||
    process.env.LLM_HTTP_URL ||
    "https://api.groq.com/openai/v1/chat/completions";
  const key = process.env.LLM_CHAT_KEY ?? process.env.LLM_HTTP_KEY ?? "";
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (key) headers.Authorization = `Bearer ${key}`;
  const res = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify({
      model: process.env.LLM_CHAT_MODEL || process.env.LLM_MODEL || "llama-3.1-8b-instant",
      temperature: 0.7,
      messages: [{ role: "system", content: system }, ...messages]
    })
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`LLM request failed ${res.status}: ${body.slice(0, 200)}`);
  }
  const data = await res.json();
  return data.choices?.[0]?.message?.content ?? "";
}

async function httpChatStream(
  system: string,
  messages: ChatMessage[],
  onDelta: (delta: string) => void
): Promise<void> {
  const url =
    process.env.LLM_CHAT_URL ||
    process.env.LLM_HTTP_URL ||
    "https://api.groq.com/openai/v1/chat/completions";
  const key = process.env.LLM_CHAT_KEY ?? process.env.LLM_HTTP_KEY ?? "";
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (key) headers.Authorization = `Bearer ${key}`;
  const res = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify({
      model: process.env.LLM_CHAT_MODEL || process.env.LLM_MODEL || "llama-3.1-8b-instant",
      temperature: 0.7,
      stream: true,
      messages: [{ role: "system", content: system }, ...messages]
    })
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`LLM request failed ${res.status}: ${body.slice(0, 200)}`);
  }
  if (!res.body) throw new Error("LLM response body is empty");
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() ?? "";
      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed.startsWith("data:")) continue;
        const payload = trimmed.slice(5).trim();
        if (payload === "[DONE]") return;
        let parsed: { choices?: Array<{ delta?: { content?: unknown } }> };
        try {
          parsed = JSON.parse(payload);
        } catch {
          continue;
        }
        const content = parsed.choices?.[0]?.delta?.content;
        if (typeof content === "string" && content.length > 0) onDelta(content);
      }
    }
    if (buffer.trim().startsWith("data:")) {
      const payload = buffer.trim().slice(5).trim();
      if (payload !== "[DONE]") {
        try {
          const parsed: { choices?: Array<{ delta?: { content?: unknown } }> } = JSON.parse(payload);
          const content = parsed.choices?.[0]?.delta?.content;
          if (typeof content === "string" && content.length > 0) onDelta(content);
        } catch {
          /* ignore trailing partial data */
        }
      }
    }
  } finally {
    try {
      await reader.cancel();
    } catch {
      /* reader already released */
    }
  }
}

export function llmHttpProvider(): LLMProvider {
  return {
    async complete(prompt: string): Promise<string> {
      return httpComplete(
        "You are a helpful assistant. Reply concisely using the exact format the user asks for.",
        prompt
      );
    },
    async chat(system: string, messages: ChatMessage[]): Promise<string> {
      return httpChat(system, messages);
    },
    async chatStream(system: string, messages: ChatMessage[], onDelta: (delta: string) => void): Promise<void> {
      return httpChatStream(system, messages, onDelta);
    }
  };
}

function mockProvider(): LLMProvider {
  return {
    async complete(_prompt: string): Promise<string> {
      return '{"fallback":"canned"}';
    },
    async chat(_system: string, _messages: ChatMessage[]): Promise<string> {
      return '{"reply":"Ciao! (mock reply)","correction":"","translation":""}';
    },
    async chatStream(_system: string, _messages: ChatMessage[], onDelta: (delta: string) => void): Promise<void> {
      onDelta("Ciao! ");
      onDelta("(mock reply)");
    }
  };
}

export function getLLMProvider(): LLMProvider {
  const kind = (process.env.LLM_PROVIDER || "http").toLowerCase();
  if (kind === "mock") return mockProvider();
  return llmHttpProvider();
}

export function getChatProvider(): LLMProvider {
  const hasDedicatedChat = Boolean(process.env.LLM_CHAT_URL || process.env.LLM_CHAT_KEY);
  const kind = (process.env.LLM_PROVIDER || "http").toLowerCase();
  if (kind === "mock" && !hasDedicatedChat) return mockProvider();
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
