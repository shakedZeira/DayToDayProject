import { useEffect, useState } from "react";
import {
  getProfile,
  saveProfile,
  type ProfileRecommendation,
} from "./profileApi";

interface Props {
  token: string;
}

const ACTIVITY_OPTIONS = [
  { value: "sedentary", label: "Sedentary (little or no exercise)" },
  { value: "light", label: "Light (1–3 days/week)" },
  { value: "moderate", label: "Moderate (3–5 days/week)" },
  { value: "active", label: "Active (6–7 days/week)" },
  { value: "very_active", label: "Very Active (hard exercise daily)" },
] as const;

export default function Profile({ token }: Props) {
  const [heightCm, setHeightCm] = useState("");
  const [weightKg, setWeightKg] = useState("");
  const [age, setAge] = useState("30");
  const [sex, setSex] = useState("male");
  const [activity, setActivity] = useState("light");
  const [applyTarget, setApplyTarget] = useState(true);
  const [recommendation, setRecommendation] = useState<ProfileRecommendation | null>(null);
  const [targetApplied, setTargetApplied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getProfile(token)
      .then((resp) => {
        if (cancelled || !resp) return;
        setHeightCm(String(resp.profile.heightCm));
        setWeightKg(String(resp.profile.weightKg));
        if (resp.profile.age != null) setAge(String(resp.profile.age));
        if (resp.profile.sex) setSex(resp.profile.sex);
        if (resp.profile.activity) setActivity(resp.profile.activity);
        setRecommendation(resp.recommendation);
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [token]);

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    const h = Number(heightCm);
    const w = Number(weightKg);
    if (!h || h <= 0 || !w || w <= 0) {
      setError("Height and weight are required.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const resp = await saveProfile(token, {
        heightCm: h,
        weightKg: w,
        age: Number(age) || undefined,
        sex,
        activity,
        applyTarget,
      });
      setRecommendation(resp.recommendation);
      setTargetApplied(resp.targetApplied);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save profile");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="w-full max-w-2xl text-center text-slate-400 py-8">Loading profile…</div>
    );
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      <div className="flex flex-col gap-4 border rounded bg-white p-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-indigo-600">
          Profile
        </h2>

        <form onSubmit={onSave} className="flex flex-col gap-3">
          <div className="flex gap-2">
            <div className="flex flex-col gap-1 flex-1">
              <label className="text-xs text-slate-500">Height (cm) *</label>
              <input
                className="border rounded px-3 py-2"
                type="number"
                min={1}
                step="any"
                placeholder="170"
                value={heightCm}
                onChange={(e) => setHeightCm(e.target.value)}
                required
              />
            </div>
            <div className="flex flex-col gap-1 flex-1">
              <label className="text-xs text-slate-500">Weight (kg) *</label>
              <input
                className="border rounded px-3 py-2"
                type="number"
                min={1}
                step="any"
                placeholder="70"
                value={weightKg}
                onChange={(e) => setWeightKg(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="flex gap-2">
            <div className="flex flex-col gap-1 flex-1">
              <label className="text-xs text-slate-500">Age</label>
              <input
                className="border rounded px-3 py-2"
                type="number"
                min={1}
                placeholder="30"
                value={age}
                onChange={(e) => setAge(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1 flex-1">
              <label className="text-xs text-slate-500">Sex</label>
              <select
                className="border rounded px-3 py-2"
                value={sex}
                onChange={(e) => setSex(e.target.value)}
              >
                <option value="male">Male</option>
                <option value="female">Female</option>
              </select>
            </div>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs text-slate-500">Activity Level</label>
            <select
              className="border rounded px-3 py-2"
              value={activity}
              onChange={(e) => setActivity(e.target.value)}
            >
              {ACTIVITY_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input
              type="checkbox"
              className="rounded"
              checked={applyTarget}
              onChange={(e) => setApplyTarget(e.target.checked)}
            />
            Set as my daily calorie target
          </label>

          {error && <p className="text-red-600 text-sm">{error}</p>}

          <button
            type="submit"
            disabled={saving}
            className="bg-indigo-600 text-white rounded py-2 font-medium disabled:opacity-50"
          >
            {saving ? "Saving…" : "Save Profile"}
          </button>
        </form>

        {targetApplied && (
          <p className="text-emerald-600 text-sm font-medium">
            Daily calorie target updated to match your recommended intake.
          </p>
        )}
      </div>

      {recommendation && (
        <div className="flex flex-col gap-4 border rounded bg-white p-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-indigo-600">
            Calorie Recommendation
          </h2>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col">
              <span className="text-xs uppercase tracking-wide text-slate-400">BMR (resting)</span>
              <span className="text-lg font-semibold text-slate-800">{recommendation.bmr} kcal</span>
            </div>
            <div className="flex flex-col">
              <span className="text-xs uppercase tracking-wide text-slate-400">TDEE (maintenance)</span>
              <span className="text-lg font-semibold text-slate-800">{recommendation.tdee} kcal</span>
            </div>
            <div className="flex flex-col">
              <span className="text-xs uppercase tracking-wide text-slate-400">Weight Loss (−500)</span>
              <span className="text-lg font-semibold text-slate-800">{recommendation.weightLoss} kcal</span>
            </div>
            <div className="flex flex-col">
              <span className="text-xs uppercase tracking-wide text-slate-400">Weight Gain (+300)</span>
              <span className="text-lg font-semibold text-slate-800">{recommendation.weightGain} kcal</span>
            </div>
          </div>

          <div className="flex flex-col gap-1 text-xs text-slate-400">
            <p>Method: {recommendation.method}</p>
            <p className="italic">{recommendation.disclaimer}</p>
          </div>
        </div>
      )}
    </div>
  );
}
