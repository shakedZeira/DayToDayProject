import { useEffect, useState } from "react";
import Login from "./Login";
import Tasks from "./Tasks";
import DigestView from "./DigestView";
import ProgressView from "./ProgressView";
import SettingsView from "./SettingsView";
import HealthView from "./HealthView";
import ItalianView from "./ItalianView";
import CookingView from "./CookingView";
import Nav from "./Nav";
import WeeklyReview from "./WeeklyReview";
import { getOutbox } from "./outboxRuntime";
import OutboxStatus from "./OutboxStatus";
import { fetchHealth } from "./api";
import type { AuthUser } from "./auth";
import { snoozeTask } from "./tasksApi";
import Toast from "./Toast";

const TOKEN_KEY = "dtd.token";
const USER_KEY = "dtd.user";

function readStored(key: string): string | null {
  return sessionStorage.getItem(key) ?? localStorage.getItem(key);
}

function clearStored(key: string) {
  sessionStorage.removeItem(key);
  localStorage.removeItem(key);
}

function writeStored(key: string, value: string, remember: boolean) {
  const store = remember ? localStorage : sessionStorage;
  store.setItem(key, value);
}

export default function App() {
  const [backend, setBackend] = useState<string>("connecting…");
  const [token, setToken] = useState<string | null>(() => readStored(TOKEN_KEY));
  const [user, setUser] = useState<AuthUser | null>(() => {
    const raw = readStored(USER_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  });
  const [view, setView] = useState<string>("today");
  const [toast, setToast] = useState<{ message: string; type: "success" | "error" } | null>(null);
  const [pendingSnooze, setPendingSnooze] = useState<{ taskId: string; minutes: number } | null>(() => {
    // Deep link from a notification when no app window was open: /?snooze=<taskId>&minutes=<n>
    const q = new URLSearchParams(window.location.search);
    const id = q.get("snooze");
    if (!id) return null;
    const m = Number(q.get("minutes"));
    window.history.replaceState(null, "", window.location.pathname);
    return { taskId: id, minutes: Number.isInteger(m) && m >= 1 && m <= 1440 ? m : 10 };
  });

  // The service worker asks an open window (which holds the auth token) to snooze a task.
  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;
    const onMessage = (e: MessageEvent) => {
      const d = e.data as { type?: string; taskId?: string; minutes?: number } | undefined;
      if (d?.type === "dtd-snooze" && d.taskId) setPendingSnooze({ taskId: d.taskId, minutes: d.minutes ?? 10 });
    };
    navigator.serviceWorker.addEventListener("message", onMessage);
    return () => navigator.serviceWorker.removeEventListener("message", onMessage);
  }, []);

  useEffect(() => {
    if (!pendingSnooze || !token) return;
    const { taskId, minutes } = pendingSnooze;
    setPendingSnooze(null);
    snoozeTask(token, taskId, minutes)
      .then(() => setToast({ message: `Reminder snoozed for ${minutes} min`, type: "success" }))
      .catch(() => setToast({ message: "Could not snooze the reminder", type: "error" }));
  }, [pendingSnooze, token]);

  useEffect(() => {
    fetchHealth()
      .then(() => setBackend("connected"))
      .catch(() => setBackend("unreachable"));
  }, []);

  function onAuthed(t: string, u: AuthUser, remember: boolean) {
    writeStored(TOKEN_KEY, t, remember);
    writeStored(USER_KEY, JSON.stringify(u), remember);
    setToken(t);
    setUser(u);
    setView("today");
  }

  function onLogout() {
    void getOutbox().clear();
    clearStored(TOKEN_KEY);
    clearStored(USER_KEY);
    setToken(null);
    setUser(null);
  }

  const signedIn = token && user;

  return (
    <div className="min-h-screen bg-md-background flex flex-col">
      <a href="#main" className="sr-only-focusable z-50 bg-md-surface px-4 py-2 text-indigo-700 font-medium">Skip to content</a>
      <header className="w-full bg-md-surface h-16 px-4 flex items-center justify-between relative">
        <h1 className="absolute inset-x-0 text-center text-xl font-medium text-md-onSurface pointer-events-none">DayToDay</h1>
        <span />
        {signedIn && (
          <span className="flex items-center gap-3 text-sm">
            <span className="hidden sm:inline text-slate-600">{user.email}</span>
            <span
              role="img"
              className={`inline-block w-2 h-2 rounded-full ${backend === "connected" ? "bg-md-secondary" : "bg-md-error"}`}
              title={`Backend: ${backend}`}
              aria-label={`Backend: ${backend}`}
            />
            <button className="text-indigo-700 font-medium relative z-10" onClick={onLogout}>Log out</button>
          </span>
        )}
      </header>

      {signedIn ? (
        <>
          <OutboxStatus />
          <main id="main" tabIndex={-1} className="flex-1 w-full max-w-3xl mx-auto px-4 py-4 pb-24 md:pb-6">
            {view === "today" && <DigestView token={token} onNavigate={setView} />}
            {view === "tasks" && <Tasks token={token} />}
            {view === "workouts" && <HealthView token={token} tab="workouts" />}
            {view === "goals" && <HealthView token={token} tab="goals" />}
            {view === "nutrition" && <HealthView token={token} tab="nutrition" />}
            {view === "italian" && <ItalianView token={token} />}
            {view === "cooking" && <CookingView token={token} />}
            {view === "progress" && <ProgressView token={token} />}
            {view === "review" && <WeeklyReview token={token} />}
            {view === "settings" && <SettingsView token={token} />}
          </main>
          <Nav view={view} onNavigate={setView} />
          <Toast message={toast?.message ?? null} type={toast?.type} onDismiss={() => setToast(null)} />
        </>
      ) : (
        <main id="main" className="flex-1 flex items-center justify-center p-6">
          <Login onAuthed={onAuthed} />
        </main>
      )}
    </div>
  );
}