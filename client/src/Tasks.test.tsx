// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from "vitest";
import { render, screen, cleanup, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Tasks from "./Tasks";
import { mockApi, type MockCall } from "./testFetch";
import type { Task } from "./tasksApi";

const task = (over: Partial<Task> = {}): Task => ({
  id: "t1", ownerId: "u1", title: "Buy milk", notes: null, status: "PENDING", completedAt: null,
  dueAt: null, recurrence: "none", category: null, createdAt: "2026-11-01T08:00:00.000Z",
  updatedAt: "2026-11-01T08:00:00.000Z", ...over,
});

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("Tasks", () => {
  it("lists tasks from the API", async () => {
    mockApi({ "GET /api/tasks": [task(), task({ id: "t2", title: "Call mum", category: "family" })], "GET /api/tasks/today": [] });
    render(<Tasks token="tok" />);
    expect(await screen.findByText("Buy milk")).toBeTruthy();
    expect(screen.getByText("Call mum")).toBeTruthy();
    expect(screen.getByText("family")).toBeTruthy();
  });

  it("shows the empty state", async () => {
    mockApi({ "GET /api/tasks": [], "GET /api/tasks/today": [] });
    render(<Tasks token="tok" />);
    expect(await screen.findByText(/No tasks yet/)).toBeTruthy();
  });

  it("creates a task and refreshes the list", async () => {
    let list: Task[] = [];
    const { calls } = mockApi({
      "GET /api/tasks": () => list,
      "GET /api/tasks/today": [],
      "POST /api/tasks": ({ body }: MockCall) => {
        const created = task({ id: "t9", title: (body as { title: string }).title });
        list = [created];
        return created;
      },
    });
    render(<Tasks token="tok" />);
    await screen.findByText(/No tasks yet/);
    await userEvent.type(screen.getByPlaceholderText("Task title"), "Write tests");
    await userEvent.click(screen.getByRole("button", { name: "Add Task" }));
    expect(await screen.findByText("Write tests")).toBeTruthy();
    const post = calls.find((c) => c.method === "POST");
    expect(post?.body).toMatchObject({ title: "Write tests", notes: null, category: null, dueAt: null });
  });

  it("toggles a task done via PATCH", async () => {
    const { calls } = mockApi({
      "GET /api/tasks": [task()],
      "GET /api/tasks/today": [],
      "PATCH /api/tasks/t1": task({ status: "DONE" }),
    });
    render(<Tasks token="tok" />);
    await screen.findByText("Buy milk");
    await userEvent.click(screen.getByTitle("Mark done"));
    await waitFor(() => expect(calls.some((c) => c.method === "PATCH")).toBe(true));
    expect(calls.find((c) => c.method === "PATCH")?.body).toEqual({ status: "DONE" });
  });

  it("deletes a task", async () => {
    const { calls } = mockApi({
      "GET /api/tasks": [task()],
      "GET /api/tasks/today": [],
      "DELETE /api/tasks/t1": new Response(null, { status: 204 }),
    });
    render(<Tasks token="tok" />);
    await screen.findByText("Buy milk");
    await userEvent.click(screen.getByTitle("Delete"));
    await waitFor(() => expect(calls.some((c) => c.method === "DELETE")).toBe(true));
  });

  it("shows the server error when loading fails", async () => {
    mockApi({
      "GET /api/tasks": new Response(JSON.stringify({ error: "boom" }), { status: 500 }),
      "GET /api/tasks/today": [],
    });
    render(<Tasks token="tok" />);
    expect(await screen.findByText("boom")).toBeTruthy();
  });
});
