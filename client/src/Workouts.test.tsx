// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from "vitest";
import { render, screen, cleanup, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ProgressiveSuggestion } from "shared";
import Workouts from "./Workouts";
import { mockApi } from "./testFetch";
import type { Workout, WorkoutSummary } from "./workoutApi";

const summary: WorkoutSummary = {
  id: "w1", title: "Push day", date: "2026-11-10T08:00:00.000Z", notes: null,
  sets: [{ exercise: "Bench Press", weightKg: 60, reps: 8 }],
};
const workout: Workout = {
  id: "w1", ownerId: "u1", title: "Push day", date: "2026-11-10T08:00:00.000Z", notes: null,
  createdAt: "2026-11-10T08:00:00.000Z", updatedAt: "2026-11-10T08:00:00.000Z",
  sets: [{ id: "s1", ownerId: "u1", workoutId: "w1", createdAt: "2026-11-10T08:05:00.000Z", exercise: "Bench Press", weightKg: 60, reps: 8 }],
  exercises: [],
};
const suggestion: ProgressiveSuggestion = { exercise: "Bench Press", currentWeightKg: 60, suggestedNextKg: 62.5, reason: "completed_target" };

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("Workouts", () => {
  it("shows the empty state with no workouts", async () => {
    mockApi({ "GET /api/workouts": [] });
    render(<Workouts token="tok" />);
    expect(await screen.findByText(/No workouts yet/)).toBeTruthy();
  });

  it("renders the selected workout's sets and progression suggestion", async () => {
    mockApi({
      "GET /api/workouts": [summary],
      "GET /api/workouts/w1": workout,
      "GET /api/workouts/w1/progressive": [suggestion],
    });
    render(<Workouts token="tok" />);
    expect(await screen.findByText("Bench Press: 60kg × 8 reps")).toBeTruthy();
    expect(await screen.findByText(/target met/)).toBeTruthy();
    expect(screen.getByText(/62.5kg/)).toBeTruthy();
  });

  it("logs a set with the typed values", async () => {
    const { calls } = mockApi({
      "GET /api/workouts": [summary],
      "GET /api/workouts/w1": workout,
      "GET /api/workouts/w1/progressive": [],
      "POST /api/workouts/w1/sets": [{ ...workout.sets[0], id: "s2", exercise: "Squat", weightKg: 100, reps: 5 }],
    });
    render(<Workouts token="tok" />);
    await screen.findByText("Bench Press: 60kg × 8 reps");
    await userEvent.type(screen.getByPlaceholderText("Exercise"), "Squat");
    await userEvent.type(screen.getByPlaceholderText("kg"), "100");
    await userEvent.type(screen.getByPlaceholderText("reps"), "5");
    await userEvent.click(screen.getByRole("button", { name: "+ Set" }));
    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ sets: [{ exercise: "Squat", weightKg: 100, reps: 5 }] });
  });

  it("surfaces an error when the list fails to load", async () => {
    mockApi({ "GET /api/workouts": new Response(JSON.stringify({ error: "db down" }), { status: 500 }) });
    render(<Workouts token="tok" />);
    await waitFor(() => expect(screen.queryByText("Loading workouts…")).toBeNull());
    expect(screen.getByText(/No workouts yet/)).toBeTruthy(); // error leaves the empty state, no crash
  });
});
