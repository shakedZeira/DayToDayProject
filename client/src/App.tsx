import { useEffect, useState } from "react";
import Login from "./Login";
import Tasks from "./Tasks";
import Goals from "./Goals";
import Workouts from "./Workouts";
import Plans from "./Plans";
import Analytics from "./Analytics";
import Foods from "./Foods";
import type { MealLogRecord } from "./foodApi";
import NutritionSummary from "./NutritionSummary";
import { getSummary, setTarget } from "./nutritionApi";
import type { NutritionSummary as NutritionSummaryType } from "shared";
import Notifications from "./Notifications";
import PhotoCalories from "./PhotoCalories";
import Profile from "./Profile";
import PdfHub from "./PdfHub";
import { fetchHealth } from "./api";
import type { AuthUser } from "./auth";

const TOKEN_KEY = "dtd.token";
const USER_KEY = "dtd.user";

type Tab = "today" | "goals" | "workouts" | "food" | "profile" | "notifications" | "study";

const TABS: { key: Tab; label: string }[] = [
  { key: "today", label: "Today" },
  { key: "goals", label: "Goals" },
  { key: "workouts", label: "Workouts" },
  { key: "food", label: "Food" },
  { key: "profile", label: "Profile" },
  { key: "notifications", label: "Notifications" },
  { key: "study", label: "Study" },
];

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
  const [summary, setSummary] = useState<NutritionSummaryType | null>(null);
  const [targetInput, setTargetInput] = useState("");
  const [activeTab, setActiveTab] = useState<Tab>("today");
  const [workoutSubTab, setWorkoutSubTab] = useState<"plans" | "history" | "progress">("plans");
  const [analyticsRefreshKey, setAnalyticsRefreshKey] = useState(0);

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
  }

  function refreshNutrition() {
    if (!token) return;
    getSummary(token).then(setSummary).catch(() => setSummary(null));
  }

  useEffect(() => {
    refreshNutrition();
  }, [token]);

  const summaryMeals: MealLogRecord[] = summary
    ? summary.meals.map((m) => ({
        id: m.id,
        foodId: m.id,
        grams: m.grams,
        date: summary.date,
        name: m.foodName,
      }))
    : [];

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
              onClick={() => { clearStored(TOKEN_KEY); clearStored(USER_KEY); setToken(null); setUser(null); }}
            >
              Log out
            </button>
          </div>

          {/* Tab bar */}
          <div className="flex gap-1 border-b border-slate-200 pb-px">
            {TABS.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`px-3 py-2 text-sm font-medium rounded-t transition-colors ${
                  activeTab === tab.key
                    ? "bg-indigo-600 text-white"
                    : "text-slate-600 hover:bg-slate-200"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Tab content */}
          {activeTab === "today" && (
            <div className="flex flex-col gap-4">
              <div className="flex items-center gap-2">
                <label htmlFor="target" className="text-sm text-slate-600">
                  Daily target (kcal)
                </label>
                <input
                  id="target"
                  className="border rounded px-3 py-2 w-32"
                  type="number"
                  min={1}
                  step="any"
                  placeholder="2000"
                  value={targetInput}
                  onChange={(e) => setTargetInput(e.target.value)}
                />
                <button
                  className="bg-indigo-600 text-white rounded px-4 py-2 text-sm font-medium"
                  onClick={() => {
                    const calories = Number(targetInput);
                    if (!calories || calories <= 0) return;
                    setTarget(token, calories).then(() => {
                      setTargetInput("");
                      refreshNutrition();
                    });
                  }}
                >
                  Set target
                </button>
              </div>
              {summary && <NutritionSummary summary={summary} />}
              <Tasks token={token} />
            </div>
          )}

          {activeTab === "goals" && <Goals token={token} />}

          {activeTab === "workouts" && (
            <div className="flex flex-col gap-3">
              <div className="flex gap-1 border-b border-slate-200 pb-px">
                {(["plans", "history", "progress"] as const).map((st) => (
                  <button
                    key={st}
                    onClick={() => {
                      setWorkoutSubTab(st);
                      if (st === "progress") setAnalyticsRefreshKey((k) => k + 1);
                    }}
                    className={`px-3 py-1.5 text-xs font-medium rounded transition-colors ${
                      workoutSubTab === st
                        ? "bg-indigo-600 text-white"
                        : "text-slate-500 hover:bg-slate-200"
                    }`}
                  >
                    {st === "plans" ? "Plans" : st === "history" ? "History" : "Progress"}
                  </button>
                ))}
              </div>
              {workoutSubTab === "plans" && <Plans token={token} />}
              {workoutSubTab === "history" && <Workouts token={token} />}
              {workoutSubTab === "progress" && <Analytics token={token} refreshKey={analyticsRefreshKey} />}
            </div>
          )}

          {activeTab === "food" && (
            <div className="flex flex-col gap-4">
              <Foods token={token} meals={summaryMeals} onChanged={refreshNutrition} />
              <PhotoCalories token={token} />
            </div>
          )}

          {activeTab === "profile" && <Profile token={token} />}

          {activeTab === "notifications" && <Notifications token={token} />}

          {activeTab === "study" && <PdfHub token={token} />}
        </div>
      ) : (
        <Login onAuthed={onAuthed} />
      )}
    </div>
  );
}
