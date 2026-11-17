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
      <header className="w-full bg-md-surface h-16 px-4 flex items-center justify-between relative">
        <h1 className="absolute inset-x-0 text-center text-xl font-medium text-md-onSurface pointer-events-none">DayToDay</h1>
        <span />
        {signedIn && (
          <span className="flex items-center gap-3 text-sm">
            <span className="hidden sm:inline text-slate-500">{user.email}</span>
            <span
              className={`inline-block w-2 h-2 rounded-full ${backend === "connected" ? "bg-md-secondary" : "bg-md-error"}`}
              title={`Backend: ${backend}`}
              aria-label={`Backend: ${backend}`}
            />
            <button className="text-md-primary font-medium relative z-10" onClick={onLogout}>Log out</button>
          </span>
        )}
      </header>

      {signedIn ? (
        <>
          <OutboxStatus />
          <main className="flex-1 w-full max-w-3xl mx-auto px-4 py-4 pb-24 md:pb-6">
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
        </>
      ) : (
        <main className="flex-1 flex items-center justify-center p-6">
          <Login onAuthed={onAuthed} />
        </main>
      )}
    </div>
  );
}