// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import PlateCalc from "./PlateCalc";
import Analytics, { WeeklyMuscleChart } from "./Analytics";
import { mockApi } from "./testFetch";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("workout extras UI", () => {
  it("PlateCalc shows plates per side", () => {
    render(<PlateCalc weightKg={100} />);
    expect(screen.getByText(/Per side: 25 \+ 15 kg/)).toBeTruthy();
  });

  it("WeeklyMuscleChart renders a row per muscle", () => {
    render(<WeeklyMuscleChart data={[
      { weekStart: "2026-11-09", muscle: "Chest", sets: 4, kg: 800 },
      { weekStart: "2026-11-16", muscle: "Chest", sets: 6, kg: 1200 },
      { weekStart: "2026-11-16", muscle: "Legs", sets: 3, kg: 900 },
    ]} />);
    expect(screen.getByRole("img", { name: "Chest sets per week" })).toBeTruthy();
    expect(screen.getByText("6 sets")).toBeTruthy();
  });

  it("Analytics lists deload suggestions", async () => {
    mockApi({
      "GET /api/workouts/analytics/summary": { totalWorkouts: 4, thisWeekWorkouts: 1, volumeTrend: [], muscleVolume: [], weeklyMuscleVolume: [], prs: [] },
      "GET /api/progression": [],
      "GET /api/progression/deload": [{ exerciseName: "Squat", reason: "stalled", detail: "No estimated 1RM improvement in the last 3 sessions.", currentWeightKg: 120, suggestedWeightKg: 107.5 }],
    });
    render(<Analytics token="tok" />);
    expect(await screen.findByText(/try 107.5kg/)).toBeTruthy();
  });
});
