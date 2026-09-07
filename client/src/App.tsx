import { useEffect, useState } from "react";
import Login from "./Login";
import Tasks from "./Tasks";
import DigestView from "./DigestView";
import ProgressView from "./ProgressView";
import SettingsView from "./SettingsView";
import HealthView from "./HealthView";
import ItalianView from "./ItalianView";
import PdfHub from "./PdfHub";
import Nav from "./Nav";
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
    clearStored(TOKEN_KEY);
    clearStored(USER_KEY);
    setToken(null);
    setUser(null);
  }

  const signedIn = token && user;

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col">
      <header className="w-full bg-white shadow-sm px-4 py-3 flex items-center justify-between">
        <h1 className="text-lg font-bold text-slate-800">Day To Day</h1>
        {signedIn && (
          <span className="flex items-center gap-3 text-sm">
            <span className="hidden sm:inline text-slate-500">{user.email}</span>
            <span className="text-xs text-slate-400">Backend: {backend}</span>
            <button className="text-red-600 underline" onClick={onLogout}>Log out</button>
          </span>
        )}
      </header>

      {signedIn ? (
        <>
          <main className="flex-1 w-full max-w-3xl mx-auto px-4 py-4 pb-24 md:pb-6">
            {view === "today" && <DigestView token={token} />}
            {view === "tasks" && <Tasks token={token} />}
            {view === "health" && <HealthView token={token} />}
            {view === "study" && <PdfHub token={token} />}
            {view === "italian" && <ItalianView token={token} />}
            {view === "progress" && <ProgressView token={token} />}
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