# Personal Daily Companion — Design Spec

**Date:** 2026-08-30
**Status:** Approved design → implementation plan next

## 1. Vision

A personal, all-in-one daily companion application for a single user, usable on both a mobile phone and a computer, that brings together the user's daily life modules: task management with clock alerts, health tracking, daily language (Italian) learning with voice, and a study/PDF hub with learning context, all under one roof with unified progress feedback.

## 2. High-level decisions (agreed during brainstorm)

| Decision | Choice | Rationale |
|---|---|---|
| Product scope | Personal, single-user companion | Built specifically for the user |
| Platform | Web app / PWA (phone + desktop browsers) | One codebase, installable, no app store |
| Backend hosting | Cloud-hosted backend | Phone + computer hit same URL; real background alerts |
| Tech comfort | Comfortable developer | Full monorepo, self-managed |
| AI posture | Free/local first, accept rough AI accuracy | Avoid per-call costs; swappable providers |
| Voice tutor | Browser-native speech + free-tier LLM | Simplest fully-free voice approach |
| Build order | Plan all modules, phase implementation | Each phase delivers standalone value |

## 3. Modules

### 3.1 Tasks with clock alerts (Phase 1)
- One-off and recurring tasks, categories, completion tracking, streaks.
- **Clock alerts** via PWA Web Push (VAPID). Desktop (Chrome/Edge) and Android PWA: reliable. **iOS Safari limitation:** notifications only work reliably for installed home-screen PWAs on recent iOS; flag as known limitation.
- Recurring rules (daily, weekly, specific days).

### 3.2 Health (Phase 2)
- **Workouts (2×/week):** log exercises, sets, reps, weight; **progressive weight scaling** recommendations; progression over time charts.
- **Calories via image recognition:** user photographs a meal → vision model estimates calories. **Free/local first, rough accuracy; portion sizing is the hard part.** Provider-swappable to a paid API later.
- **Food management:** add foods from a nutrition database (open USDA DB), daily calorie target vs. consumed graph, meal logging.

### 3.3 Italian tutor with voice (Phase 4)
- Daily lesson generator ("lesson of the day").
- **Speak English → tutor teaches Italian** via:
  - Speech-to-text: browser Web Speech API.
  - LLM: free-tier endpoint for responses + corrections.
  - Text-to-speech: browser `speechSynthesis` for pronunciation.
- Correction flow: user speaks, gets feedback, retries.

### 3.4 Study / PDF hub with context (Phase 3)
- Upload & store PDFs; in-app viewer (PDF.js).
- **Track learning within PDFs:** highlights/notes anchored to text, per-PDF progress.
- **Learn by context:** LLM ingests highlighted notes + PDF content to generate quizzes/flashcards/digests from the user's actual material.

### 3.5 Bonus (Phase 5)
- Daily **focus digest** aggregating top task, workout reminder, Italian lesson, and study item.
- Unified **streaks + progress dashboard** across all modules (addresses the user's core "no feedback/progress view" pain).

## 4. Architecture

```
monorepo/
  client/        # React + TypeScript + Vite PWA (Tailwind)
  server/        # Express + TypeScript REST API, Prisma, storage
  shared/        # shared TS types
```

- **Frontend:** React + TypeScript + Vite, PWA via `vite-plugin-pwa`, mobile-first responsive with Tailwind CSS. Installable to home screen.
- **Backend:** Node.js + Express (TypeScript), REST API. Single language across stack.
- **Database:** SQLite via Prisma — zero-cost, file-based, ideal for single user.
- **File/PDF storage:** server filesystem (upgrade to S3-compatible later if needed).
- **Auth:** single-user account with JWT/session (room to grow).
- **Notifications:** Web Push (VAPID) via service worker; offline-friendly PWA caching.

## 5. Pluggable AI provider abstraction

Critical design for the "free/local first, swap later" posture. Defined interfaces, each with a default free implementation and a swappable alternative:

| Provider | Purpose | Default (free) | Swappable to |
|---|---|---|---|
| `LLMProvider` | Italian tutor responses/corrections, PDF quiz/flashcard generation | Free-tier LLM endpoint (e.g., Groq free tier) | OpenAI / Claude |
| `VisionProvider` | Calorie estimation from meal photos | Free/local vision model (rough) | GPT-4o / Claude vision |
| `SpeechProvider` | STT + TTS for Italian tutor | Browser-native Web Speech API + `speechSynthesis` | ElevenLabs etc. |

Config flags switch providers without changing module code.

## 6. Phased roadmap

| Phase | Scope | Delivers |
|---|---|---|
| **0** | Repo setup, PWA scaffold, auth, SQLite schema, shared layout, deploy skeleton | Running cross-device app shell |
| **1** | Tasks + recurring + completion + Web Push clock alerts + digest hook | First real value, lowest risk |
| **2** | Health: workouts w/ progressive weight, nutrition DB, food mgmt, calorie charts; then image-recognition calories (risky, behind VisionProvider) | Health module |
| **3** | Study/PDF hub: upload/store/view, highlights/notes, per-PDF progress, AI flashcards/quiz via LLMProvider | Study module |
| **4** | Italian tutor: daily lesson gen, browser STT, LLM correction, TTS pronunciation, conversation | Language module |
| **5** | Unified streaks/progress dashboard, focus digest, iOS/PWA polish, offline hardening | Polish & glue |

## 7. Known tradeoffs / honest limitations

- **iOS push notifications** are limited vs desktop/Android.
- **Calorie-from-photo** accuracy is rough with free/local vision; portion estimation is unreliable. Mitigation: manual adjust + upgrade path to paid provider.
- **Voice tutor quality** with browser APIs is decent but not Siri-grade; acceptable per user's choice.
- **Free-tier LLM limits** (rate/cost) may require a queue or fallback for large PDFs.

## 8. Non-goals (for now)

- Multi-user accounts / collaboration.
- Live calorie/nutrition data from wearable devices.
- Native mobile apps (PWA covers the need).
- Real-time anything beyond push notifications.
