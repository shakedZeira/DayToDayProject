import { describe, it, expect } from "vitest";
import { withActions } from "./pushSender";

describe("withActions", () => {
  it("adds a snooze action for task reminders", () => {
    const p = withActions({ title: "Reminder", body: "Call mom", taskId: "t1", url: "/tasks" });
    expect(p.actions).toEqual([{ action: "snooze", title: "Snooze 10 min" }]);
    expect(p.snoozeMinutes).toBe(10);
    expect(p.taskId).toBe("t1");
  });
  it("leaves non-task notifications alone", () => {
    const p = withActions({ title: "Hi", body: "x" });
    expect(p.actions).toBeUndefined();
  });
});
