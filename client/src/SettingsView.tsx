import { useEffect, useState } from "react";
import { getSettings, putSetting } from "./settingsApi";
import { getSummary, setTarget } from "./nutritionApi";
import Profile from "./Profile";
import Notifications from "./Notifications";

interface Props {
  token: string;
}

export default function SettingsView({ token }: Props) {
  const [settings, setSettings] = useState<Record<string, string> | null>(null);
  const [calorieTarget, setCalorieTarget] = useState<string>("");

  useEffect(() => {
    getSettings(token)
      .then((res) => setSettings(res.settings))
      .catch(() => setSettings({}));
    getSummary(token)
      .then((s) => setCalorieTarget(String(s.target)))
      .catch(() => setCalorieTarget(""));
  }, [token]);

  async function save(key: string, value: string) {
    await putSetting(token, key, value);
    setSettings((prev) => ({ ...(prev ?? {}), [key]: value }));
  }

  async function saveCalories(value: string) {
    const n = Number(value);
    if (!Number.isFinite(n) || n <= 0) return;
    await setTarget(token, n);
    setCalorieTarget(String(n));
  }

  if (!settings) return <p className="text-slate-400">Loading settings…</p>;

  const workoutDays = settings.workoutDays ?? "Mon,Wed,Fri";
  const italianLevel = settings.italianLevel ?? "A1";
  const notificationsEnabled = settings.notificationsEnabled === "true";

  return (
    <div className="flex flex-col gap-6 w-full max-w-lg">
      <h2 className="text-2xl font-bold text-slate-800">Settings</h2>
      <label className="flex flex-col gap-1 text-sm">
        Daily calorie target (kcal)
        <input
          type="number"
          className="border rounded px-3 py-2"
          value={calorieTarget}
          onChange={(e) => setCalorieTarget(e.target.value)}
          onBlur={(e) => saveCalories(e.target.value)}
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Workout days (comma-separated, e.g. Mon,Wed,Fri)
        <input
          className="border rounded px-3 py-2"
          value={workoutDays}
          onChange={(e) => save("workoutDays", e.target.value)}
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Italian level
        <select
          className="border rounded px-3 py-2"
          value={italianLevel}
          onChange={(e) => save("italianLevel", e.target.value)}
        >
          <option value="A1">A1</option>
          <option value="A2">A2</option>
          <option value="B1">B1</option>
        </select>
      </label>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={notificationsEnabled}
          onChange={(e) => save("notificationsEnabled", String(e.target.checked))}
        />
        Enable push notifications
      </label>
      <p className="text-xs text-slate-400">
        iOS Safari push only works for installed home-screen PWAs (spec §7).
      </p>

      <hr className="border-slate-200" />
      <Notifications token={token} />
      <hr className="border-slate-200" />
      <Profile token={token} />
    </div>
  );
}
