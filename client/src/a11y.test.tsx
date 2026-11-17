// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from "vitest";
import { render, screen, cleanup, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";
import type { Recipe } from "shared";
import App from "./App";
import Nav from "./Nav";
import Login from "./Login";
import Tasks from "./Tasks";
import CookingView from "./CookingView";
import ExerciseDemoModal from "./ExerciseDemoModal";
import Toast from "./Toast";
import { mockApi } from "./testFetch";
import type { Task } from "./tasksApi";

const task = (over: Partial<Task> = {}): Task => ({
  id: "t1", ownerId: "u1", title: "Buy milk", notes: null, status: "PENDING", completedAt: null,
  dueAt: null, recurrence: "none", category: null, createdAt: "2026-11-01T08:00:00.000Z",
  updatedAt: "2026-11-01T08:00:00.000Z", ...over,
});

const recipe = {
  id: "r1", ownerId: null, title: "Shakshuka", description: "Eggs", meal: "breakfast", difficulty: "beginner",
  prepTimeMin: 10, cookTimeMin: 20, servings: 2, calories: 320, proteinG: 18, carbsG: 20, fatG: 16,
  ingredients: [], instructions: [], imageUrl: null, source: null, tags: null,
  createdAt: "2026-11-01T08:00:00.000Z", updatedAt: "2026-11-01T08:00:00.000Z", favoriteCount: 0, isFavorited: false,
} as Recipe;

afterEach(() => { cleanup(); vi.unstubAllGlobals(); sessionStorage.clear(); localStorage.clear(); });

describe("a11y: axe", () => {
  it("login form has no violations", async () => {
    const { container } = render(<Login onAuthed={() => {}} />);
    expect((await axe(container)).violations).toEqual([]);
  });

  it("signed-in app shell (header, nav, main) has no violations", async () => {
    sessionStorage.setItem("dtd.token", "tok");
    sessionStorage.setItem("dtd.user", JSON.stringify({ id: "u1", email: "a@b.c" }));
    mockApi({ "GET /api/health": { ok: true } });
    const { container } = render(<App />);
    await screen.findByRole("navigation", { name: "Main" });
    expect((await axe(container, { rules: { region: { enabled: false } } })).violations).toEqual([]);
  });

  it("tasks screen has no violations", async () => {
    mockApi({ "GET /api/tasks": [task(), task({ id: "t2", title: "Done thing", status: "DONE" })], "GET /api/tasks/today": [task({ id: "t3", title: "Due soon" })] });
    const { container } = render(<Tasks token="tok" />);
    await screen.findByText("Buy milk");
    expect((await axe(container)).violations).toEqual([]);
  });

  it("Hebrew RTL cooking screen has no violations and keeps dir=rtl", async () => {
    mockApi({ "GET /api/recipes": [recipe] });
    const { container } = render(<CookingView token="tok" />);
    await screen.findByText("Shakshuka");
    expect(container.querySelector('[dir="rtl"]')?.getAttribute("lang")).toBe("he");
    expect((await axe(container)).violations).toEqual([]);
  });

  it("exercise demo dialog has no violations", async () => {
    const { container } = render(<ExerciseDemoModal exercise={{ name: "Squat", muscleGroup: "Legs" }} onClose={() => {}} />);
    expect((await axe(container)).violations).toEqual([]);
  });
});

describe("a11y: keyboard and live regions", () => {
  it("More menu opens from the keyboard, arrows move, Escape closes and restores focus", async () => {
    const user = userEvent.setup();
    const onNavigate = vi.fn();
    render(<Nav view="today" onNavigate={onNavigate} />);
    const more = screen.getByRole("button", { name: "More" });
    expect(more.getAttribute("aria-expanded")).toBe("false");
    more.focus();
    await user.keyboard("{Enter}");
    expect(more.getAttribute("aria-expanded")).toBe("true");
    const items = screen.getAllByRole("menuitem");
    await waitFor(() => expect(document.activeElement).toBe(items[0]));
    await user.keyboard("{ArrowDown}");
    expect(document.activeElement).toBe(items[1]);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("menu")).toBeNull();
    expect(document.activeElement).toBe(more);
    await user.keyboard("{Enter}");
    await user.keyboard("{Enter}");
    expect(onNavigate).toHaveBeenCalledWith("tasks");
  });

  it("demo dialog traps focus and closes on Escape", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<ExerciseDemoModal exercise={{ name: "Squat" }} onClose={onClose} />);
    const dialog = screen.getByRole("dialog");
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));
    for (let i = 0; i < 5; i++) {
      await user.tab();
      expect(dialog.contains(document.activeElement)).toBe(true);
    }
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });

  it("toast is announced through a live region that stays mounted", () => {
    const { rerender } = render(<Toast message={null} onDismiss={() => {}} />);
    const region = screen.getByRole("status");
    expect(region.getAttribute("aria-live")).toBe("polite");
    rerender(<Toast message="Saved" onDismiss={() => {}} />);
    expect(region.textContent).toContain("Saved");
    rerender(<Toast message="Oops" type="error" onDismiss={() => {}} />);
    expect(screen.getByRole("alert").getAttribute("aria-live")).toBe("assertive");
  });

  it("icon-only task buttons have accessible names", async () => {
    mockApi({ "GET /api/tasks": [task()], "GET /api/tasks/today": [] });
    render(<Tasks token="tok" />);
    await screen.findByText("Buy milk");
    expect(screen.getByRole("button", { name: 'Mark "Buy milk" done' })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Delete Buy milk" })).toBeTruthy();
  });
});

function luminance(hex: string): number {
  const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
}
const ratio = (a: string, b: string) => {
  const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
};

describe("a11y: palette contrast (WCAG AA 4.5:1)", () => {
  // Pairs used by the web UI after the palette adjustments in tailwind.config.js.
  const pairs: [string, string, string][] = [
    ["secondary text on white (slate-500)", "#5B6472", "#FFFFFF"],
    ["secondary text on surfaceVariant", "#5B6472", "#F3F4F6"],
    ["secondary text on background", "#5B6472", "#F9FAFB"],
    ["link/primary text on white (indigo-700)", "#1E40AF", "#FFFFFF"],
    ["primary text on primaryContainer", "#1E40AF", "#DBEAFE"],
    ["white on filled button (indigo-700)", "#FFFFFF", "#1E40AF"],
    ["white on indigo-600 button", "#FFFFFF", "#1D4ED8"],
    ["error text on white (red-600)", "#DC2626", "#FFFFFF"],
    ["onSurface on white", "#111827", "#FFFFFF"],
  ];
  for (const [name, fg, bg] of pairs) {
    it(name, () => expect(ratio(fg, bg)).toBeGreaterThanOrEqual(4.5));
  }
});

describe("snooze", () => {
  it("Snooze button calls the snooze endpoint for the task", async () => {
    const user = userEvent.setup();
    const { calls } = mockApi({
      "GET /api/tasks": [task()], "GET /api/tasks/today": [],
      "POST /api/tasks/t1/snooze": task({ snoozedUntil: "2026-11-17T12:00:00.000Z" }),
    });
    render(<Tasks token="tok" />);
    await user.click(await screen.findByRole("button", { name: /Snooze Buy milk/ }));
    await waitFor(() => expect(calls.some((c) => c.method === "POST" && c.path === "/api/tasks/t1/snooze")).toBe(true));
    expect(calls.find((c) => c.path.endsWith("/snooze"))?.body).toEqual({ minutes: 10 });
    expect(await screen.findByText(/Snoozed "Buy milk"/)).toBeTruthy();
  });

  it("App performs a snooze requested by the service worker message", async () => {
    sessionStorage.setItem("dtd.token", "tok");
    sessionStorage.setItem("dtd.user", JSON.stringify({ id: "u1", email: "a@b.c" }));
    const { calls } = mockApi({ "GET /api/health": { ok: true }, "POST /api/tasks/t9/snooze": task({ id: "t9" }) });
    const listeners: ((e: MessageEvent) => void)[] = [];
    vi.stubGlobal("navigator", {
      ...navigator,
      serviceWorker: {
        addEventListener: (_: string, l: (e: MessageEvent) => void) => listeners.push(l),
        removeEventListener: () => {},
      },
    });
    render(<App />);
    await waitFor(() => expect(listeners.length).toBeGreaterThan(0));
    await act(async () => listeners.forEach((l) => l(new MessageEvent("message", { data: { type: "dtd-snooze", taskId: "t9", minutes: 15 } }))));
    await waitFor(() => expect(calls.find((c) => c.path === "/api/tasks/t9/snooze")?.body).toEqual({ minutes: 15 }));
    expect(await screen.findByText(/snoozed for 15 min/)).toBeTruthy();
  });
});
