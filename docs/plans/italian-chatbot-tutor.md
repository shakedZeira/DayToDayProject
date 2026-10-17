# Italian Chatbot / Tutor Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans (or subagent-driven-development) to implement task-by-task. Steps use checkbox (`- [ ]`) syntax.
> **Skill used:** writing-plans; endpoint pattern follows this repo's `express-prisma-endpoint` skill.

**Goal:** Add an Italian tutor chatbot to the Italian tab of the Compose Multiplatform Android app: a real chat conversation that tutors in Italian, speaks its replies aloud (TTS), and optionally accepts spoken input (STT).

**Architecture:** The app already talks to a local Express server on the same phone (`http://127.0.0.1:4000/`, `composeApp/src/commonMain/kotlin/com/daytoday/network/Dtos.kt:12`). We add one stateless chat endpoint (`POST /api/italian/chat`) that reuses the existing LLM provider abstraction (`server/src/providers/llm.ts`) with a **new plain-text `chat()` method** (no `response_format: json_object`), plus a new tutor screen (`ChatScreen` + `ChatViewModel`) wired into the Italian tab, with TTS reused from `rememberItalianTts()` and STT via Android's on-device `SpeechRecognizer`.

**Tech Stack:** Express + Prisma/SQLite + vitest (server), Kotlin/Compose + Hilt + Ktor 2.3.8 + kotlinx.serialization (client), Groq free tier (`llama-3.1-8b-instant`) as the cloud LLM, Android `TextToSpeech` / `SpeechRecognizer`.

**Spec:** This document. Facts were verified against the files cited inline (routes, DTO style, nav, manifest, gradle, `phone/setup.sh`, tests).

---

## 1. Objective + Scope

**In scope**
- Server: `POST /api/italian/chat` — multi-turn, auth-required, stateless, with tutor persona prompt, structured reply (Italian reply + optional correction + optional English translation), and a canned fallback when the LLM is unavailable.
- Server: `LLMProvider.chat(system, messages)` — plain-text multi-turn completion that does **not** force `response_format: json_object`.
- Client: chat DTOs, `DayTodayApi` method + Ktor impl, dedicated `ChatViewModel`, `ChatScreen` (message list, text input, send, per-message 🔊 speaker button), navigation from the Italian tab.
- Client: TTS of assistant replies in Italian (reuse of existing helper).
- Client: STT (mic button → `SpeechRecognizer`, `it-IT`), behind the `RECORD_AUDIO` runtime permission.
- Config: document/enable LLM env vars in `phone/setup.sh`; deploy steps.
- Tests: server vitest coverage; manual on-device checklist.

**Out of scope**
- Streaming (SSE) responses; multi-user; chat history persistence across app restarts (v2 option, see §2/§10); on-device LLM; voice call / real-time conversation UI; chat from the web PWA (`client/`); any non-Italian languages; XP/goal wiring for chat.

---

## 2. Key Design Decisions (with tradeoffs)

| Decision | Choice | Tradeoffs / rejected alternatives |
|---|---|---|
| LLM location | **Cloud Groq via existing HTTP provider** (`llm.ts:18-44`) | Already wired, free tier, phone has internet (`phone/setup.sh` apt/curl/git). On-device LLM (e.g. llama.cpp) rejected: heavy install in proot, slow on phone CPU, no model download management. |
| Chat memory | **Stateless server; client holds turn list and sends it every request** | Zero schema change → `prisma db push` (`phone/setup.sh:73`) cannot break the phone DB; no ownership/index bugs; easy to test. Cost: history re-sent each turn (capped at last 40 msgs / ~1.5k chars each) and lost on app restart. Server-persisted `ChatMessage` model rejected for v1 (see §10, Q4). |
| Streaming vs single-shot | **Single-shot JSON reply** | Streaming needs Ktor `bodyAsTextChannel`/SSE handling on top of `ContentNegotiation`, a UI incremental-reveal loop, and complicates the `HttpResponseValidator` error path (`DayTodayApiImpl.kt:51-57`). Single-shot fits in the existing 20 s request timeout (`DayTodayApiImpl.kt:46-50`); Groq llama-3.1-8b-instant replies in ~1-2 s. |
| Reply format | **One LLM call returning a JSON envelope `{"reply","correction","translation"}`, parsed with `extractJson`, raw-text fallback** | Gives us a translation for beginners and a structured correction to display, in a single round trip. The envelope is requested by the *prompt* — the transport (`chat()`) sends **no** `response_format`, so a plain-text reply also parses (fallback: whole text = reply, no translation). Pure free-text (no envelope) rejected: mixed IT/EN text would be spoken by the Italian TTS voice. |
| STT | **On-device Android `SpeechRecognizer`, locale `it-IT`** | Free, offline-capable on most devices, zero server work. Requires `RECORD_AUDIO` runtime permission and a `<queries>` entry for `RecognitionService` (Android 11+ package visibility). Cloud STT rejected: extra key/latency/privacy. STT is **optional for v1** — ship chat+TTS first. |
| TTS | **Reuse `rememberItalianTts()` (`ExerciseViews.kt:40-56`)** | Already sets `Locale.ITALIAN` and shuts down on dispose. We extract it into its own file so both `ExerciseViews.kt` and `ChatScreen.kt` use one helper (avoids two TTS engines). No new dependency. |
| Level / target language | **Fixed in the system prompt: CEFR A1-A2, reply in simple Italian, English only for glosses/corrections** | No per-user level model. `level` is an optional request field defaulting to `"A1-A2"` so it can grow later. |
| Where the route lives | **New `server/src/italianChat.ts` router mounted at `/api/italian`** | `app.ts` already double-mounts one prefix (nutrition + nutritionPhoto, `app.ts:41-42`). Keeps `italian.ts` (220 lines) from bloating; gets `requireAuth` inside the new router exactly like `italian.ts:139`. |

**Endpoint contract (proposed)**

```
POST /api/italian/chat
Authorization: Bearer <token>
Content-Type: application/json

{ "level": "A1-A2",
  "messages": [ { "role": "user" | "assistant", "content": "..." }, ... ] }   // last must be "user"

200 -> { "reply": "Ciao! Oggi parliamo del caffè...", "correction": "..."?, "translation": "..."? }
400 -> { "error": "messages required" | "messages must end with a user turn" | ... }
401 -> { "error": "missing bearer token" }
```

Errors from the LLM never surface as 5xx: like `generateLessonBody()` (`italian.ts:77-84`) and `checkAttempt()` (`correction.ts:65-76`), a provider failure falls back to a friendly canned Italian reply with HTTP 200, so the chat screen never breaks.

---

## 3. Server Work

### 3.1 `server/src/providers/llm.ts` — add `chat()`

Current interface (`llm.ts:3-7`) has `generateFlashcards`, `generateQuiz`, `complete(prompt)`. `complete()` routes through `httpComplete()` which hard-codes `response_format: { type: "json_object" }` (`llm.ts:31`) and only accepts a single system+user pair (`llm.ts:32-36`) — unusable for free-form multi-turn chat (Groq/OpenAI JSON mode rejects or mangles non-JSON output).

Changes:

1. Export a message type and add the method to the interface:

```ts
export interface ChatMessageIn {
  role: "system" | "user" | "assistant";
  content: string;
}

export interface LLMProvider {
  generateFlashcards(material: string, count: number): Promise<FlashcardOut[]>;
  generateQuiz(material: string, count: number): Promise<QuizOut[]>;
  complete(prompt: string): Promise<string>;
  chat(system: string, messages: ChatMessageIn[]): Promise<string>;
}
```

2. New `httpChat()` next to `httpComplete()` (`llm.ts:18-44`) — same URL/key/model/temperature plumbing, but **no `response_format`**, `temperature: 0.6`, and messages = `[{role:"system",content: system}, ...messages]`:

```ts
async function httpChat(system: string, messages: ChatMessageIn[]): Promise<string> {
  const url = process.env.LLM_HTTP_URL || "https://api.groq.com/openai/v1/chat/completions";
  const key = process.env.LLM_HTTP_KEY || "";
  if (!key) throw new Error("LLM_HTTP_KEY not configured (free-tier key)");
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
    body: JSON.stringify({
      model: process.env.LLM_MODEL || "llama-3.1-8b-instant",
      temperature: 0.6,
      messages: [{ role: "system", content: system }, ...messages]
    })
  });
  if (!res.ok) throw new Error(`LLM chat failed ${res.status}: ${(await res.text()).slice(0, 200)}`);
  const data = await res.json();
  return data.choices?.[0]?.message?.content ?? "";
}
```

3. `llmHttpProvider()` gets `async chat(system, messages) { return httpChat(system, messages); }`.
4. `mockProvider()` (`llm.ts:77-89`) gets:

```ts
async chat(_system: string, messages: ChatMessageIn[]): Promise<string> {
  const last = messages[messages.length - 1]?.content ?? "";
  return JSON.stringify({
    reply: `Ciao! Hai scritto: "${last.slice(0, 40)}". (risposta dimostrativa)`,
    correction: "",
    translation: "Demo reply from the mock tutor."
  });
}
```

> Note: tests that build providers with `{ ...llmMockProvider(), complete: async () => out }` (`italian.test.ts:9-11`) keep working — object spread copies `chat`.

### 3.2 New `server/src/italianChat.ts`

Follows the `express-prisma-endpoint` pattern (router + validation + ownerId scope), except **no Prisma model** for v1 (stateless).

**Exports:** `buildTutorSystemPrompt(level)`, `parseChatReply(text)`, `fallbackChatReply()`, `runChatTurn(provider, messages, level)`, `createItalianChatRouter(provider)`.

Prompt builder (pure, unit-testable):

```ts
export function buildTutorSystemPrompt(level: string): string {
  return [
    "You are Sofia, a warm, patient Italian tutor chatting with a single adult beginner.",
    `Learner level: ${level} (CEFR). Always aim at that level.`,
    "Rules:",
    "1. Reply in simple, natural Italian (short sentences, present tense, everyday topics).",
    "2. If the learner wrote something wrong or unnatural, put a short, kind correction in \"correction\" (the wrong bit struck through in words, then the fix) — never only in \"reply\".",
    "3. Always fill \"translation\" with a plain English gloss of what you said in \"reply\".",
    "4. Keep \"reply\" under 60 words. Ask one short question back to keep the conversation going.",
    "5. Gently encourage speaking: if the learner only types, suggest they press the mic and answer aloud in Italian.",
    'Respond with ONLY JSON: {"reply": string, "correction": string, "translation": string}'
  ].join("\n");
}
```

Parser + fallback (mirrors `tryParseLesson`/`extractJson` style, `italian.ts:66-75`):

```ts
export interface ChatTurnReply {
  reply: string;
  correction?: string;
  translation?: string;
}

export function parseChatReply(text: string): ChatTurnReply {
  const json = extractJson(text);
  if (json && typeof json.reply === "string" && json.reply.trim()) {
    return {
      reply: json.reply.trim(),
      correction: typeof json.correction === "string" && json.correction.trim() ? json.correction.trim() : undefined,
      translation: typeof json.translation === "string" && json.translation.trim() ? json.translation.trim() : undefined
    };
  }
  const raw = text.trim();
  return { reply: raw || "Non ho capito, puoi ripetere?" };
}

export function fallbackChatReply(): ChatTurnReply {
  return {
    reply: "Ciao! Non riesco a connettermi al tutor adesso. Riprova tra un momento, oppure esercitati con la lezione di oggi.",
    translation: "I can't reach the tutor right now. Try again in a moment."
  };
}

export async function runChatTurn(
  provider: LLMProvider,
  messages: ChatMessageIn[],
  level: string
): Promise<ChatTurnReply> {
  try {
    const raw = await provider.chat(buildTutorSystemPrompt(level), messages);
    return parseChatReply(raw);
  } catch {
    return fallbackChatReply();
  }
}
```

Router (mounted like `nutritionPhotoRouter`, `app.ts:41-42`):

```ts
export function createItalianChatRouter(provider: LLMProvider = getLLMProvider()): Router {
  const router = Router();
  router.use(requireAuth);

  router.post("/chat", async (req: AuthedRequest, res) => {
    const { messages, level } = req.body ?? {};
    const err = validateChatBody(messages);           // exported pure fn, see below
    if (err) return res.status(400).json({ error: err });
    const lv = typeof level === "string" && level.trim() ? level.trim() : "A1-A2";
    const reply = await runChatTurn(provider, messages, lv);
    res.json(reply);
  });

  return router;
}
```

Validation (`validateChatBody`, exported for direct unit tests):
- `messages` must be a non-empty array, length ≤ 40 → else `"messages required"` / `"too many messages"`.
- every item `{role, content}`: role ∈ `{"user","assistant"}`, content a non-empty string ≤ 1500 chars → `"invalid message at index i"`.
- last message role must be `"user"` → `"messages must end with a user turn"`.

Mount in `server/src/app.ts` (after line 48):

```ts
import { createItalianChatRouter } from "./italianChat";
...
app.use("/api/italian", createItalianChatRouter());
```

No change to `server/prisma/schema.prisma` → `prisma db push` (`phone/setup.sh:73`) is unaffected.

### 3.3 Server tests — `server/src/italianChat.test.ts`

Pattern copied from `italian.test.ts` (register for a token, `wrapRouter()` at lines 13-18, `mockComplete`-style provider override at lines 9-11):

- **auth:** `POST /chat` with no `Authorization` → 401 (mirrors `italian.test.ts:72-77`).
- **validation:** empty `messages` → 400; `messages` ending with an assistant turn → 400; 41 messages → 400; content > 1500 chars → 400.
- **happy path:** provider override `{ ...llmMockProvider(), chat: async () => JSON.stringify({reply:"Ciao!",correction:"",translation:"Hi!"}) }` → 200 with `reply === "Ciao!"`.
- **unparseable reply:** `chat: async () => "Solo testo libero"` → 200, `reply === "Solo testo libero"`, `translation === undefined`.
- **provider throws:** `chat: async () => { throw new Error("no key"); }` → 200 with the canned `fallbackChatReply().reply`.
- **prompt:** `buildTutorSystemPrompt("A1")` contains `"A1"`, `"JSON"`, `"Italian"`; `validateChatBody` unit cases.

---

## 4. Client Work

### 4.1 DTOs — `composeApp/src/commonMain/kotlin/com/daytoday/network/ItalianDtos.kt`

Append (kotlinx.serialization, matches `networkJson` `ignoreUnknownKeys` config in `Dtos.kt:14-19`):

```kotlin
@Serializable
data class ChatMessageDto(val role: String, val content: String)   // "user" | "assistant"

@Serializable
data class ChatRequestDto(val messages: List<ChatMessageDto>, val level: String = "A1-A2")

@Serializable
data class ChatReplyDto(
    val reply: String,
    val correction: String? = null,
    val translation: String? = null,
)
```

### 4.2 API surface

`composeApp/src/commonMain/kotlin/com/daytoday/network/DayTodayApi.kt` (next to line 51):

```kotlin
suspend fun sendItalianChat(authToken: String, request: ChatRequestDto): ChatReplyDto
```

`composeApp/src/androidMain/kotlin/com/daytoday/network/DayTodayApiImpl.kt` — copy the `submitItalianLesson` shape (`:142-151`), **`contentType(ContentType.Application.Json)` is mandatory** or Ktor throws "Fail to prepare request body":

```kotlin
override suspend fun sendItalianChat(authToken: String, request: ChatRequestDto): ChatReplyDto =
    client.post(absoluteUrl("api/italian/chat")) {
        header("Authorization", "Bearer $authToken")
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()
```

Non-2xx already surfaces as `ApiException(serverErrorMessage)` via the `HttpResponseValidator` (`DayTodayApiImpl.kt:51-57`) — the ViewModel just catches `Exception`.

### 4.3 `ChatViewModel` — new `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/italian/ChatViewModel.kt`

Hilt-injected exactly like `ItalianViewModel` (`ItalianViewModel.kt:39-43`): `@Inject constructor(private val settingsManager: SettingsManager, private val api: DayTodayApi)`.

```kotlin
data class ChatUiMessage(
    val role: String,            // "user" | "assistant"
    val text: String,            // what is shown in the bubble (assistant: reply)
    val correction: String? = null,
    val translation: String? = null,
    val error: Boolean = false,  // render a failed turn in red with a Retry affordance
)

data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
)
```

Behavior:
- `onInputChanged(String)`, `send()`:
  1. guard `input.isNotBlank() && !sending`;
  2. append user message, clear input, set `sending = true`;
  3. build `ChatRequestDto(messages = state.messages.takeLast(40).map { ChatMessageDto(it.role, it.text) })` — assistant entries map from `text` (the Italian reply, which is what the model must continue from);
  4. `val token = settingsManager.authToken.first()` (`SettingsManager.kt:22` `TOKEN_KEY = "auth_token"`), `api.sendItalianChat(token, req)`;
  5. append `ChatUiMessage("assistant", reply.reply, reply.correction, reply.translation)`;
  6. `catch (e: Exception)` → append `ChatUiMessage("user", original, error = true)`? No — keep the user message, append an error assistant bubble with `error = true` and remember the failed text for a `retry()` that re-sends the same history.
- `clearChat()` for the top-bar "new chat" action.
- Auto-scroll: expose `messages.size`; `ChatScreen` `LaunchedEffect(messages.size) { listState.animateScrollToItem(last) }`.

### 4.4 `ChatScreen` — new `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/italian/ChatScreen.kt`

Layout (all components already exist in the project):
- `Scaffold(topBar = { DayTodayTopAppBar(title = "Italian Tutor", navigationIcon = back arrow, actions = { IconButton("New chat") }) })` — `DayTodayTopAppBar` supports `navigationIcon`/`actions` (`Components.kt:171-187`).
- `LazyColumn` of bubbles: user right (`primaryContainer`), assistant left (`surfaceVariant`); under an assistant bubble show `correction` (small, amber) and `translation` (small, secondary) when non-null; a `CircularProgressIndicator` row while `sending`.
- **Per-message speaker:** IconButton (material-icons-extended is already a dependency, `build.gradle.kts:36`) → `tts.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, "chat")` — same call as `ExerciseViews.kt:94`. Speak only `text` (Italian), never the English translation.
- Bottom input bar: `OutlinedTextField` + send `IconButton(Icons.AutoMirrored.Filled.Send)` + mic `IconButton(Icons.Default.Mic)`.
- TTS: `val tts = rememberItalianTts()` at screen top (helper reused — see 4.6).

### 4.5 Wiring into the Italian tab + navigation

1. `composeApp/src/androidMain/kotlin/com/daytoday/ui/navigation/Screen.kt` — add (next to line 50-53):

```kotlin
data object ItalianTutor : Screen { override val route: String = "ItalianTutor" }
```

2. `NavHost.kt` — import `ChatScreen`, add after line 67:

```kotlin
composable(Screen.ItalianTutor.route) { ChatScreen(onBack = { navController.popBackStack() }) }
```

3. `ItalianScreen.kt:40` — change signature to `fun ItalianScreen(navController: NavHostController, viewModel: ItalianViewModel = hiltViewModel())` and give the top bar a tutor entry (`ItalianScreen.kt:43`):

```kotlin
topBar = {
    DayTodayTopAppBar(
        title = "Italian",
        actions = {
            IconButton(onClick = { navController.navigate(Screen.ItalianTutor.route) }) {
                Icon(Icons.Default.Forum, contentDescription = "Tutor chat")
            }
        },
    )
},
```

4. `NavHost.kt:65-67` — pass `navController = navController` into `ItalianScreen`.

(`BottomNavHost.kt:39` needs no change — the tutor is reached from the Italian tab, not as a bottom-nav item.)

### 4.6 TTS reuse — extract the helper

Move `rememberItalianTts()` (`ExerciseViews.kt:40-56`) verbatim into a new `composeApp/src/androidMain/kotlin/com/daytoday/ui/screen/italian/ItalianTts.kt`; `ExerciseViews.kt` and `ChatScreen.kt` both import it. No behavior change (`Locale.ITALIAN`, `shutdown()` on dispose). Optional polish: report `TextToSpeech.LANG_AVAILABLE` and grey out the speaker button if the Italian voice is missing.

### 4.7 STT (speech-to-text) — mic button

**Manifest** `composeApp/src/androidMain/AndroidManifest.xml`:
- add permission next to line 21: `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
- add package-visibility query inside the existing `<queries>` block (lines 7-18), otherwise Android 11+ cannot bind the recognizer:

```xml
<intent>
    <action android:name="android.speech.RecognitionService" />
</intent>
```

**Runtime permission** — same pattern as `SettingsScreen.kt:106-107`:

```kotlin
val micLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted -> if (granted) startListening() else permissionDenied = true }
// mic click: if granted -> startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
```

**Recognizer** — helper inside `ChatScreen.kt` (or `ItalianStt.kt`):

```kotlin
val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }   // hide mic if false
val listener = object : RecognitionListener {
    override fun onResults(results: Bundle) {
        val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull().orEmpty()
        if (heard.isNotBlank()) viewModel.onInputChanged(heard)   // fills the input; user still taps send
        listening = false
    }
    override fun onError(error: Int) { listening = false }
    // implement the remaining callbacks as no-ops
}
fun startListening() {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "it-IT")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Parla italiano…")
    }
    recognizer.startListening(intent)   // main thread, as required
}
DisposableEffect(Unit) { onDispose { recognizer.cancel(); recognizer.destroy() } }
```

Notes: mic toggles (tap again to cancel); results land in the input box rather than auto-sending, so the user can correct ASR mistakes before sending — a good fit for a language learner. Recognizer errors (`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`) just stop the spinner; never surface a dialog.

---

## 5. Data Flow (one chat turn, STT + TTS optional)

```
 ┌────────────────── ANDROID APP (single user) ───────────────────┐
 │                                                                 │
 │  (opt) mic tap ── RECORD_AUDIO granted? ── no ──► runtime prompt│
 │        │ yes                                                    │
 │        ▼                                                        │
 │  SpeechRecognizer (on-device, it-IT) ── onResults ──┐           │
 │                                                     ▼           │
 │                              ChatViewModel.input = "Io voglio.."│
 │        user taps send                                   │      │
 │        ▼                                                │      │
 │  ChatUiState.messages + new user msg ◄──────────────────┘      │
 │  ChatRequestDto{messages:[{role,content}xN]}                    │
 │        │                                                        │
 │        ▼  DayTodayApiImpl.sendItalianChat()                     │
 │  POST http://127.0.0.1:4000/api/italian/chat                    │
 │  Content-Type: application/json + Authorization: Bearer token   │
 └────────┼────────────────────────────────────────────────────────┘
          ▼  (loopback on the same phone; Termux/proot Ubuntu)
 ┌──────────────────── LOCAL SERVER (port 4000) ───────────────────┐
 │ express.json -> app.ts:48 app.use("/api/italian", chatRouter)   │
 │   -> requireAuth (session.ts:10, JWT from auth_token)           │
 │   -> validateChatBody (400 on bad shape)                        │
 │   -> runChatTurn(provider, messages, level)                     │
 │        -> provider.chat(buildTutorSystemPrompt(level), msgs)    │
 │             llm.ts httpChat():  LLM_PROVIDER=http               │
 │               POST api.groq.com/openai/v1/chat/completions      │
 │               model llama-3.1-8b-instant, NO response_format    │
 │        -> extractJson -> {reply, correction, translation}       │
 │           (any error -> fallbackChatReply(), still HTTP 200)    │
 └────────┼────────────────────────────────────────────────────────┘
          ▼
  ChatReplyDto ──► ChatViewModel appends assistant bubble
                        │                     │
                        ▼ (UI)                ▼ user taps 🔊 on bubble
                 LazyColumn renders      TextToSpeech (Locale.ITALIAN)
                 reply + correction       .speak(reply, QUEUE_FLUSH)
                 + translation            ──► Italian audio aloud
```

---

## 6. Config / Env

Vars (already documented in `.env.example`, already read by `llm.ts:19-29`):

| Var | Meaning | Default on phone |
|---|---|---|
| `LLM_PROVIDER` | `http` \| `mock` (`llm.ts:117-121`) | **`"mock"` — written by `phone/setup.sh:56`** |
| `LLM_HTTP_URL` | OpenAI-compatible endpoint | `https://api.groq.com/openai/v1/chat/completions` (`setup.sh:57`) |
| `LLM_HTTP_KEY` | **The one manual secret** — user must paste a free Groq key | `""` (`setup.sh:59`) |
| `LLM_MODEL` | chat model | `llama-3.1-8b-instant` (`setup.sh:58`) |

Facts that drive the plan:
- `phone/setup.sh:33-41` `set_env()` only adds a var when it is absent — an existing `.env` on the phone is never clobbered, so **users who already ran setup must edit `~/dtd/.env` by hand** to set `LLM_PROVIDER="http"` and `LLM_HTTP_KEY="gsk_..."`.
- `phone/README.md` §5 already documents this flip; extend that section (and add a comment line in `setup.sh` above line 56) noting that without a key the tutor answers with the canned fallback.
- No new env vars are needed for chat/TTS/STT.
- The phone has internet (setup.sh does apt/curl/git over the network), so Groq egress works; traffic stays loopback until it leaves for `api.groq.com`.

---

## 7. Dependencies

**Client — no new Gradle dependencies required.** Verified in `composeApp/build.gradle.kts`:
- `androidx.activity:activity-compose:1.10.0` (line 71) → `rememberLauncherForActivityResult` / `ActivityResultContracts.RequestPermission()` ✔ (already used in `SettingsScreen.kt:106`)
- `material-icons-extended` (line 36) → `Icons.Default.Mic`, `Icons.Default.Forum`, `Icons.AutoMirrored.Filled.Send` ✔
- `hilt-navigation-compose` (line 70), `lifecycle-runtime-compose` (line 52) → `@HiltViewModel` + `collectAsStateWithLifecycle` ✔
- `SpeechRecognizer` / `TextToSpeech` are framework classes — no dependency.

**Manifest only:** `RECORD_AUDIO` permission + `<queries>` intent for `android.speech.RecognitionService` (§4.7).

**Server — no new npm dependencies** (express, supertest, vitest all present).

---

## 8. Testing Plan

**Server (automated)** — run from repo root:

```
npm test --workspace server                              # full suite (vitest run)
npx vitest run src/italianChat.test.ts --root server     # just the new file
npm run typecheck && npm run lint
```

Cases (§3.3): 401 unauthenticated, 400 validation ×4, 200 happy path via mocked `chat()`, plain-text fallback, provider-throw → canned reply, prompt content. Existing suites must stay green — in particular `llm.test.ts` (interface change) and `italian.test.ts` (spread-built providers).

**Client (build gate)** — from repo root, per `HANDOFF-2026-10-06.md:27`:

```
gradlew.bat :composeApp:compileDebugKotlinAndroid        # ~2 min compile gate
gradlew.bat :composeApp:installDebug                     # HANDOFF-2026-10-06.md:30
```

adb is not on PATH: `C:\Users\LotusSeven\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

**Manual on-device checklist (the phone is both client and server — no `adb forward` needed; `BASE_URL` is already `http://127.0.0.1:4000/`):**

1. On the phone (proot Ubuntu): `cd ~/dtd && git pull && bash phone/setup.sh`, then edit `~/dtd/.env` → `LLM_PROVIDER="http"`, `LLM_HTTP_KEY="<groq key>"`, and restart (`bash phone/stop.sh; bash phone/run.sh`).
2. Server smoke test in the proot shell (curl is installed by `setup.sh:13`):

```
curl -s -X POST http://127.0.0.1:4000/api/italian/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"messages":[{"role":"user","content":"Ciao, come stai?"}]}'
```

   Get a token with `POST /api/auth/login {"email":"local@daytoday.app","password":"daytoday-local"}` (credentials from `SessionProvisioner.kt:28-30`). Unauthenticated call must return 401.
3. Install: `gradlew.bat :composeApp:installDebug`; launch, open **Italian** tab → top-bar chat icon.
4. Chat: send "Ciao!" → expect an Italian reply bubble with English translation under it; verify 🔊 speaks Italian (TTS engine set to Italian); verify errors (stop the server) show a bubble, not a crash.
5. STT: tap mic → accept RECORD_AUDIO → speak "Io voglio un caffè" → text lands in the input; deny permission → mic shows a rationale instead of crashing.
6. Logs: `adb.exe logcat -s LocalServer AndroidRuntime | findstr /i "chat ApiException Ktor"` — Ktor logs the full request/response (`LogLevel.ALL`, `DayTodayApiImpl.kt:43-45`), so a wrong `Content-Type` or 400 body is visible immediately.
7. Regression: run an Italian lesson with TTS (make sure the extracted helper still works) and `npm test --workspace server` once more on the phone build.

---

## 9. Sequencing (milestones — each independently testable)

| # | Milestone | Deliverable / verification | Est. effort |
|---|---|---|---|
| 1 | `LLMProvider.chat()` + `httpChat()` + mock `chat` in `server/src/providers/llm.ts` | `npm test --workspace server` green (existing suites prove no regression; add 2 cases to `llm.test.ts`) | 0.5 h |
| 2 | `server/src/italianChat.ts` (prompt, parser, fallback, validation, router) + mount in `app.ts` + `italianChat.test.ts` | new vitest file green; `curl /api/italian/chat` 401/400/200 locally with `LLM_PROVIDER=mock` | 2-3 h |
| 3 | Client DTOs + `DayTodayApi.sendItalianChat` + `DayTodayApiImpl` impl | `gradlew.bat :composeApp:compileDebugKotlinAndroid` passes | 0.5 h |
| 4 | `ChatViewModel` + `ChatScreen` (bubbles, input, send, error/retry) + nav (`Screen.ItalianTutor`, `NavHost.kt`, tutor icon in `ItalianScreen` top bar) | compile gate + on-device: end-to-end typed chat against the local server | 3-4 h |
| 5 | TTS polish: extract `rememberItalianTts()` to `ItalianTts.kt`, per-message 🔊 button, translation/correction rendering | on-device: reply speaks in Italian; Italian lesson TTS unaffected | 1-1.5 h |
| 6 | STT: manifest (`RECORD_AUDIO` + `<queries>`), runtime permission, `SpeechRecognizer` → input box, mic toggle | on-device: permission grant/deny paths, `it-IT` recognition | 2-3 h |
| 7 | Config/docs: comment in `phone/setup.sh`, `phone/README.md` §5 note about chat needing the key | fresh `setup.sh` run on a clean `.env` shows the expected values | 0.5 h |
| 8 | Deploy to phone + full manual checklist (§8) + regression run | checklist passes; `npm run typecheck`/`lint`/`gradlew compile` all green | 1 h |

Total ≈ 2 days of focused work; milestones 1-2 and 3-5 are parallelizable (server vs. client).

---

## 10. Risks / Open Questions

1. **LLM key & provider (blocking).** The phone ships `LLM_PROVIDER="mock"` (`phone/setup.sh:56`), so without the user pasting a Groq key the tutor returns the canned fallback every turn. *Q: Do you have a Groq key, and should `setup.sh` default to `http` once a key exists?* Consider `setup.sh` printing a warning when `LLM_HTTP_KEY` is empty.
2. **Cost / rate limits.** Groq free tier is generous but chat burns a token per turn with the full history resent each time (cap 40×1500 chars ≈ 6k tokens/turn). *Q: Is a shorter window (last 12 messages) acceptable to stay comfortably under limits?*
3. **Privacy.** Every chat message leaves the phone for `api.groq.com`. This is a personal app, but messages may mention health/food context. *Q: Acceptable, or should v1 stay mock/local until reviewed?*
4. **Persistence.** Chat history lives only in `ChatViewModel` memory → lost on process death/screen rotation is avoided (ViewModel survives rotation) but not on app restart. *Q: Is v1 stateless okay? If not, add `model ChatMessage { id, ownerId, role, content, createdAt, @@index([ownerId, createdAt]) }` in `server/prisma/schema.prisma` — safe with `prisma db push` since it only adds a table.*
5. **Latency on the phone.** Loopback + Groq ≈ 1-3 s per turn; under the 20 s Ktor timeout. If the phone is offline, the fallback replies instantly — acceptable UX, but the user should know why replies are canned.
6. **STT required for v1?** Recommended as milestone 6 (after chat+TTS are proven). Some Samsung/Google builds ship no Italian recognizer or offline-only models; we hide the mic when `isRecognitionAvailable` is false. *Q: ship v1 without STT if it slips?*
7. **Mixed-language TTS.** If the model puts English into `reply`, the Italian voice reads it oddly. Mitigated by the JSON envelope + prompt rule 3 and by never speaking `translation`.
8. **Server rebuild on phone.** `phone/start.sh` runs `server/dist/phone.js`, so milestone 2 requires a rebuild on the device (`setup.sh` step 9, `npm run build --workspace server`) — remember to re-run `bash phone/setup.sh` after any `git pull` (README §6, SQLite patches).
