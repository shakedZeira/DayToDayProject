import { useEffect, useState } from "react";
import { authedFetch } from "./auth";
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
  const [exportMsg, setExportMsg] = useState<string>("");

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

  async function exportData() {
    setExportMsg("Exporting…");
    try {
      const res = await authedFetch(token, "/api/export");
      if (!res.ok) throw new Error(String(res.status));
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `daytoday-export-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      setExportMsg("Export downloaded.");
    } catch {
      setExportMsg("Export failed — try again.");
    }
  }

  if (!settings) return <p className="md-body-medium text-md-onSurfaceVariant">Loading settings…</p>;

  const workoutDays = settings.workoutDays ?? "Mon,Wed,Fri";
  const italianLevel = settings.italianLevel ?? "A1";
  const notificationsEnabled = settings.notificationsEnabled === "true";

  const field = "border border-md-outline rounded-md3xs px-3 py-2 bg-md-surface";
  return (
    <div className="flex flex-col gap-4 w-full">
      <Section title="Account">
        <Profile token={token} />
      </Section>

      <Section title="Preferences">
        <label className="flex flex-col gap-1 md-body-medium text-md-onSurface">
          Daily calorie target (kcal)
          <input
            type="number"
            className={field}
            value={calorieTarget}
            onChange={(e) => setCalorieTarget(e.target.value)}
            onBlur={(e) => saveCalories(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 md-body-medium text-md-onSurface">
          Workout days (comma-separated, e.g. Mon,Wed,Fri)
          <input className={field} value={workoutDays} onChange={(e) => save("workoutDays", e.target.value)} />
        </label>
        <label className="flex flex-col gap-1 md-body-medium text-md-onSurface">
          Italian level
          <select className={field} value={italianLevel} onChange={(e) => save("italianLevel", e.target.value)}>
            <option value="A1">A1</option>
            <option value="A2">A2</option>
            <option value="B1">B1</option>
          </select>
        </label>
      </Section>

      <Section title="Notifications">
        <label className="flex items-center justify-between md-body-medium text-md-onSurface">
          Push Notifications
          <input
            type="checkbox"
            className="w-5 h-5 accent-md-primary"
            checked={notificationsEnabled}
            onChange={(e) => save("notificationsEnabled", String(e.target.checked))}
          />
        </label>
        <p className="md-body-small text-md-onSurfaceVariant">
          iOS Safari push only works for installed home-screen PWAs (spec §7).
        </p>
        <Notifications token={token} />
      </Section>

      <Section title="Data & Privacy">
        <button type="button" className="md-btn-outlined self-start" onClick={exportData}>
          Export Data
        </button>
        <p className="md-body-small text-md-onSurfaceVariant">Downloads all your data as a JSON backup. {exportMsg}</p>
      </Section>
    </div>
  );
}

// SettingsSection (SettingsComponents.kt): labelLarge primary title above the content.
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-sm font-medium text-indigo-700">{title}</h3>
      <div className="md-card p-4 flex flex-col gap-4">{children}</div>
    </div>
  );
}
