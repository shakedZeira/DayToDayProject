# DayToDay Project - Merge Plan for NBA Daily, Sydney Workout, and PDF Merger

## Overview
Merge three separate projects into the existing DayToDay monorepo as a **Compose Multiplatform (KMP)** Android app that shares business logic with the existing React web app.

## Source Projects Analysis

### 1. NBA Daily App (D:\AI Projects\NBA daily)
- **Type**: Android app (Kotlin, Jetpack Compose, Material3)
- **Architecture**: Clean Architecture (data/domain/ui layers), Hilt DI, Room DB, Retrofit
- **Features**: 
  - Scoreboard with live games
  - Game details with box scores
  - News feed
  - Injuries tracking
  - Voice summaries (TTS)
  - Offline caching with Room
- **Key packages**: `com.nbadaily.app`, `data`, `domain`, `di`, `ui`, `voice`
- **APIs**: ESPN API, BigBalls API (with auth)

### 2. Sydney Workout App (D:\AI Projects\SydneyWorkout)
- **Type**: Android app (Kotlin, Jetpack Compose, Material3)
- **Architecture**: Clean Architecture, Hilt DI, Room DB, Health Connect, Spotify API
- **Features**:
  - Workout tracking (rounds, sets, exercises)
  - Health Connect integration (steps, calories, heart rate)
  - Spotify integration for workout music
  - Alarm system for workout reminders
  - Progress tracking & personal bests
  - Daily summary generation
  - Settings with DataStore
- **Key packages**: `com.sydneyworkout.app`, `data` (alarm, health, local, spotify), `domain`, `di`, `ui`

### 3. PDF Merger (D:\AI Projects\PDF merger)
- **Type**: Python Flask web app + vanilla HTML/JS frontend
- **Backend**: Flask, pypdf (PdfWriter)
- **Frontend**: Drag-drop file upload, reorder, merge, download
- **Current DayToDay**: Already has `pdf-lib` on server and `pdfjs-dist` + `react-pdf` on client

### 4. DayToDay Project (D:\AI Projects\DayToDayProject)
- **Type**: Monorepo (npm workspaces)
- **Client**: React 18 + Vite + Tailwind + TypeScript + PWA
- **Server**: Express + TypeScript + Prisma + SQLite/PostgreSQL
- **Shared**: TypeScript types
- **Existing PDF**: `PdfHub.tsx`, `pdfApi.ts`, server `pdfs.ts` with `pdf-lib`

## Target Architecture: Compose Multiplatform (KMP)

```
DayToDayProject/
├── client/                 # React web app (existing)
├── server/                 # Express API (existing)
├── shared/                 # TypeScript types (existing)
├── composeApp/             # NEW: KMP module
│   ├── composeApp/
│   │   ├── src/
│   │   │   ├── commonMain/     # Shared business logic (Kotlin)
│   │   │   │   ├── model/      # Shared data models
│   │   │   │   ├── repository/ # Repository interfaces
│   │   │   │   ├── usecase/    # Business logic
│   │   │   │   └── network/    # API client (Ktor)
│   │   │   ├── androidMain/    # Android-specific (Room, Health Connect, etc.)
│   │   │   │   ├── data/       # Room DAOs, Health Connect impl
│   │   │   │   ├── di/         # Hilt modules
│   │   │   │   └── ui/         # Compose screens
│   │   │   ├── iosMain/        # iOS-specific (future)
│   │   │   │   └── ...
│   │   │   └── desktopMain/    # Desktop (future)
│   │   └── build.gradle.kts
│   └── settings.gradle.kts
├── settings.gradle.kts       # Root Gradle settings
└── build.gradle.kts          # Root Gradle build
```

## Integration Strategy

### PDF Merger
- **Web**: Keep existing `PdfHub.tsx` + server `/api/pdfs/merge` endpoint (uses `pdf-lib`)
- **Android**: Call same server API from KMP commonMain using Ktor client
- **Shared**: Define API types in `shared/` (TypeScript) and mirror in `commonMain` (Kotlin)

### NBA Daily
- Port entire feature set to `composeApp/androidMain`
- Share `Game`, `News`, `Injury` models in `commonMain`
- Use Ktor for API calls (replace Retrofit)
- Use Room in `androidMain` for offline caching
- Keep TTS in `androidMain` (platform-specific)

### Sydney Workout
- Port entire feature set to `composeApp/androidMain`
- Share `Session`, `Exercise`, `SetRecord`, `DailyProgress` models in `commonMain`
- Health Connect → `androidMain` only
- Spotify → `androidMain` only (OAuth)
- Alarms → `androidMain` only
- Room for local persistence in `androidMain`

### Shared Business Logic (commonMain)
- Repository interfaces
- Use cases: `GenerateDailySummary`, `ComputePersonalBests`, `CalorieCalculator`, `RoundTracker`
- API client with Ktor
- Data models (expect/actual for platform-specific)

### Navigation
- Single `NavHost` in `androidMain` with bottom nav:
  1. **Home** (DayToDay dashboard)
  2. **NBA** (Scoreboard, Games, News, Injuries)
  3. **Workout** (Active, History, Progress)
  4. **PDF** (Merger - calls server API)
  5. **Settings**

### Theme
- Unified Material3 theme in `commonMain`
- Platform-specific color schemes in `androidMain`/`iosMain`

## Implementation Phases

### Phase 1: Project Setup (Week 1)
1. Add Gradle root project with Compose Multiplatform plugin
2. Create `composeApp` module with `commonMain`/`androidMain`
3. Configure Ktor client, Room, Hilt, Compose Material3
4. Set up shared models from existing TypeScript types

### Phase 2: NBA Daily Port (Week 2)
1. Copy domain models to `commonMain`
2. Port data layer: API services → Ktor, Room DAOs → `androidMain`
3. Port repository implementations
4. Port UI screens to Compose (adapt for shared theme)
5. Port ViewModels (use KMP-compatible state management)

### Phase 3: Sydney Workout Port (Week 3)
1. Copy domain models to `commonMain`
2. Port data layer: Room, Health Connect, Spotify → `androidMain`
4. Port use cases to `commonMain`
5. Port UI screens and ViewModels
6. Integrate alarm system

### Phase 4: PDF Merger Integration (Week 4)
1. Add PDF merge API types to shared models
2. Implement Ktor client call to existing server endpoint
3. Create PDF merge screen in `androidMain`
4. Test web + Android both work

### Phase 5: Unification & Polish (Week 5)
1. Unified navigation with bottom bar
2. Shared theme and design system
3. Settings screen combining all features
4. Testing, bug fixes, build optimization

## Key Technical Decisions

| Aspect | Decision |
|--------|----------|
| **Language** | Kotlin (KMP) for shared, Kotlin for Android |
| **Networking** | Ktor Client (commonMain) |
| **Database** | Room (androidMain), expect/actual for interface |
| **DI** | Hilt (androidMain), Koin or manual for commonMain |
| **State** | Compose State / MutableStateFlow (commonMain) |
| **Serialization** | Kotlinx Serialization (commonMain) |
| **PDF** | Server-side pdf-lib (existing), client calls REST API |
| **Auth** | JWT via shared API client |
| **Images** | Coil (androidMain) |

## Shared Models Mapping

| DayToDay (TS) | NBA Daily (Kotlin) | Sydney Workout (Kotlin) | KMP Common |
|---------------|-------------------|------------------------|------------|
| User | - | - | User |
| Workout/Exercise | - | Session/Exercise/SetRecord | WorkoutSession/Exercise/Set |
| Food/Nutrition | - | - | NutritionEntry |
| Goal | - | DailyProgress/Goals | Goal |
| PDF | PdfMergeRequest | - | PdfMergeRequest |
| - | Game/News/Injury | - | NbaGame/NbaNews/Injury |
| - | - | DailySummary/PersonalBests | FitnessSummary |

## Dependencies to Add (composeApp/build.gradle.kts)

```kotlin
// Compose Multiplatform
implementation(compose.material3)
implementation(compose.runtime)
implementation(compose.ui)
implementation(compose.foundation)

// Ktor
implementation("io.ktor:ktor-client-core:2.3.8")
implementation("io.ktor:ktor-client-android:2.3.8")
implementation("io.ktor:ktor-client-json:2.3.8")
implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")

// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// Hilt
implementation("com.google.dagger:hilt-android:2.48")
kapt("com.google.dagger:hilt-compiler:2.48")

// Health Connect
implementation("androidx.health.connect:healthconnect-client:1.1.0")

// Spotify
implementation("com.spotify.android:auth:2.0.0")

// Coil
implementation("io.coil-kt:coil-compose:2.5.0")

// Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
```

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| API key management | Use existing `apikey.properties` pattern, add to `.gitignore` |
| Health Connect permissions | Handle gracefully, fallback to manual entry |
| Spotify OAuth | Implement in `androidMain` only, mock in commonMain tests |
| TTS | Platform-specific implementation (androidMain only) |
| Large APK size | Enable dynamic feature modules for NBA/Workout/PDF |
| Build complexity | Use Gradle version catalogs, shared convention plugins |

## Success Criteria

1. ✅ Android app builds and runs on device/emulator
2. ✅ NBA Daily features work (scoreboard, games, news, injuries, voice)
3. ✅ Sydney Workout features work (tracking, Health Connect, Spotify, alarms, progress)
4. ✅ PDF Merger works on Android (calls server API) AND web (existing)
5. ✅ Shared business logic compiles for commonMain
6. ✅ Unified navigation and theme
7. ✅ Existing DayToDay web app unaffected
8. ✅ Server API unchanged (backward compatible)

## Next Steps

1. Create this plan document ✓
2. Initialize Gradle root project with KMP plugin
3. Create composeApp module structure
4. Begin Phase 1 implementation