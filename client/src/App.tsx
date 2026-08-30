import { useEffect, useState } from "react";
import Login from "./Login";
import Tasks from "./Tasks";
import { fetchHealth } from "./api";
import type { AuthUser } from "./auth";

const TOKEN_KEY = "dtd.token";
const USER_KEY = "dtd.user";

export default function App() {
  const [backend, setBackend] = useState<string>("connecting…");
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState<AuthUser | null>(() => {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  });

  useEffect(() => {
    fetchHealth()
      .then(() => setBackend("connected"))
      .catch(() => setBackend("unreachable"));
  }, []);

  function onAuthed(t: string, u: AuthUser) {
    localStorage.setItem(TOKEN_KEY, t);
    localStorage.setItem(USER_KEY, JSON.stringify(u));
    setToken(t);
    setUser(u);
  }

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col items-center justify-center gap-6 p-6">
      <h1 className="text-3xl font-bold text-slate-800">Day To Day</h1>
      <p className="text-sm text-slate-400">
        Backend: <span className="text-indigo-600">{backend}</span>
      </p>
      {token && user ? (
        <div className="w-full max-w-2xl flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-600">
              Logged in as <span className="font-semibold">{user.email}</span>
            </p>
            <button
              className="text-sm text-red-600 underline"
              onClick={() => { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); setToken(null); setUser(null); }}
            >
              Log out
            </button>
          </div>
          <Tasks token={token} />
        </div>
      ) : (
        <Login onAuthed={onAuthed} />
      )}
    </div>
  );
}