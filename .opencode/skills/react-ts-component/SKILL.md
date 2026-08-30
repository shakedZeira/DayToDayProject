---
name: react-ts-component
description: "Use when adding or modifying a React + TypeScript UI component or screen in this project (client/). Covers the convention: a trimmed API-helper module using authedFetch, a presentational component receiving props, and wiring into App with Tailwind styling. Front-load when writing a .tsx component, a useState/useEffect screen, or a fetch call from the client."
---

# React + TypeScript Component

## Overview

The standard pattern for building a UI screen in this project's `client/` (React 18 + TypeScript + Vite + Tailwind). Data access is separated from presentation: an `*Api.ts` helper talks to the backend, and a component renders state. Follow this so screens stay consistent, typed, and easy to wire up.

Core principle: **component = props + local state; data = a small typed `*Api.ts` helper. Never call `fetch` directly inside a component.**

## When to Use

- Adding a new screen (TaskList, WorkoutForm, LessonView, Dashboard…).
- Adding a fetch call or new state to an existing component.
- Writing any `.tsx` file.

## The Pattern

### 1. API helper — `client/src/<something>Api.ts`

Every helper uses `authedFetch` from `./auth` and returns typed data:

```ts
import { authedFetch } from "./auth";

export interface Widget {
  id: string;
  name: string;
  createdAt: string;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    throw new Error(err.error ?? "request failed");
  }
  return res.json();
}

export async function getWidgets<T extends { token: string }>({ token }: T): Promise<Widget[]> {
  const res = await authedFetch(token, "/api/widgets");
  return json<Widget[]>(res);
}

export async function createWidget(token: string, input: { name: string }): Promise<Widget> {
  const res = await authedFetch(token, "/api/widgets", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input)
  });
  return json<Widget>(res);
}
```

### 2. Component — `client/src/WidgetList.tsx`

```tsx
import { useCallback, useEffect, useState } from "react";
import { getWidgets, createWidget, type Widget } from "./widgetApi";

interface Props {
  token: string; // or whatever the screen needs
}

export default function WidgetList({ token }: Props) {
  const [items, setItems] = useState<Widget[]>([]);
  const [name, setName] = useState("");

  const refresh = useCallback(async () => {
    setItems(await getWidgets({ token }));
  }, [token]);

  useEffect(() => { refresh(); }, [refresh]);

  async function onAdd(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    await createWidget(token, { name });
    setName("");
    await refresh();
  }

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      <form onSubmit={onAdd} className="flex gap-2">
        <input
          className="border rounded px-3 py-2 flex-1"
          placeholder="New widget…"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button className="bg-indigo-600 text-white rounded px-4">Add</button>
      </form>
      <ul className="flex flex-col gap-2">
        {items.map((w) => (
          <li key={w.id} className="border rounded p-3 bg-white flex justify-between">
            <span>{w.name}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
```

### 3. Wire into `client/src/App.tsx`

Pass the token (and any needed props) once it's available in the authed branch:

```tsx
{token && <WidgetList token={token} />}
```

## Quick Reference

| Concern | Convention |
|---|---|
| Fetching | Always via `*Api.ts` → `authedFetch(token, path, init)` |
| Errors | `json()` helper maps non-ok to a thrown Error with server message |
| State refresh | `useCallback` refresh fn + `useEffect` on mount + explicit calls after mutations |
| Styling | Tailwind utility classes, mobile-first, `max-w-2xl` container |
| Shared types | Import from `shared` or define locally in the `*Api.ts` file |
| Typecheck | `npm run typecheck` |
| Dev server | `npm run dev --workspace client` (port 5173, proxies `/api` to 4000) |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Calling `fetch` inline in a component | Move to an `*Api.ts` helper using `authedFetch` |
| Forgetting `authedFetch` auth header | Always pass the token into helpers |
| State that goes stale after a mutation | Call `refresh()` after create/update/delete |
| Uncontrolled→controlled input churn | Controlled inputs with `value` + `onChange`, per example |
| Hard-coded full URL | Use relative `/api/...` (Vite proxies to the backend) |
