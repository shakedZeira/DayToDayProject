---
name: skill-creator
description: "Use during or after any task to detect whether it would be done better or faster by a skill, and create that skill automatically so it is available later without the user asking. Also use when the user describes a skill in plain English. Triggers on repeated work, error-prone steps, non-obvious procedures, and any 'this is tedious / I'll do this again' moment. Use ONLY for authoring opencode/agent skills, not for application code."
---

# Skill Creator

## Overview

You are a proactive skill factory. Your job is twofold:

1. **Detect** — while doing any meaningful task, notice when a piece of that work is a repeatable pattern that the user (or any future agent) will hit again. That is a candidate skill.
2. **Create** — turn that candidate into a complete, valid `SKILL.md` saved to the right folder, so it is available to trigger automatically next time, **without the user asking**.

Core principle: **A skill is a reference a future agent follows. It must be discoverable (good name + description), valid (correct frontmatter), and actionable (concrete steps, real content).** If you just finished work that a future run would benefit from encoding, encoding it now IS part of the job — don't wait to be asked.

## When to Use

Trigger **proactively** when you observe any of these during work:

- The same operation is being done repeatedly (CRUD endpoint, new screen, second/third instance of a pattern).
- A procedure is error-prone or took real debugging to get right (schema + generate + push; ESM config gotchas; a flaky step).
- A task is non-obvious — would a fresh agent face a wall doing it cold?
- The user says things like "this is tedious", "I'll have to do this again", "remember this for next time".
- The user *explicitly* asks for a new skill ("create a skill that X").

## When NOT to Use

- The work is clearly one-off with no realistic repeat (a specific bug fix unlikely to recur).
- The user is describing application features/code, not reusable procedure → use brainstorming/writing-plans instead.
- A skill for the pattern already exists — update it instead of duplicating.

## The Two-Phase Workflow

### Phase A — Detect (proactive, no prompt needed)

While doing any task, at a natural stopping point ask yourself:

| Signal | Example → candidate skill |
|---|---|
| Repeated shape | Adding another `*Api.ts` + component → `react-ts-component` |
| Recurring server route | Second Prisma-backed endpoint → `express-prisma-endpoint` |
| Error-prone setup | Node/Emacs config traps, Prisma generate/push ordering → encode it |
| New concept learned | A non-obvious API/pattern you had to figure out → `use-when` it |
| Obvious future repeat | "we'll do 4 more of these" → capture now |

**Decision rule:** if the pattern is likely to recur AND a future agent would benefit from written guidance, create the skill. Do not flood — one focused skill per distinct pattern, deduplicate against what exists. If you create it during a task, note it in your task summary.

### Phase B — Create (same mechanics as before)

Follow the creation workflow in order. Do not skip the location decision or the self-check.

#### Step 1 — Determine where the skill lives

| Scope | Path |
|---|---|
| Project (default, keeps work on D: for the DayToDayProject) | `<project>\.opencode\skills\<name>\SKILL.md` |
| Global | `~\.config\opencode\skills\<name>\SKILL.md` |

Rule: if the user wants everything off C:, default to the **project** location on D:. State the path in your report.

#### Step 2 — Derive the skill name

- Lowercase, hyphen-separated, letters + numbers + hyphens only.
- Verb-first / gerund where possible (`express-prisma-endpoint`).
- Matches the folder name exactly, ≤ 64 chars.

#### Step 3 — Write the description (most important field)

- Must start with **"Use when…"**.
- Describes **when to trigger**, NOT the workflow steps.
- Do NOT summarize the body's steps in the description (agents follow a summarized description and skip reading the body).
- Write in third person. Front-load concrete trigger keywords.

```
# BAD: "Use when testing - writes failing test, runs it, implements, refactors"
# GOOD: "Use when implementing any feature or bugfix, before writing implementation code"
```

#### Step 4 — Write the SKILL.md body

Structure: Overview (core principle 1-2 sentences) → When to Use / When NOT to Use → Core Workflow (numbered steps, real content) → Quick Reference → Common Mistakes.

- Every step must carry real, concrete content — no "TBD", "TODO", "implement later", "add appropriate handling", "similar to Task N".
- Include actual commands/code/snippets matching the project's real conventions (read the project to confirm paths/libraries).
- For discipline skills, add red-flags list + rationalization table. For technique/reference skills, focus on a clear how-to.
- If you created this from observed work, capture the **actual** commands/steps that worked (e.g., the precise install commands, the tsconfig tweak you had to make) — that practical specificity is the whole value.

#### Step 5 — Validate the file

- [ ] Exact file exists at `<target>\<name>\SKILL.md`
- [ ] Frontmatter has `name` (matches folder) and `description` (starts "Use when…")
- [ ] Body has no TBD/TODO/placeholder phrases
- [ ] Commands/paths match the actual project

#### Step 6 — Verify behavior (recommended)

If the skill enforces a discipline, run a quick baseline probe: a fresh agent does a small task WITHOUT the skill (observe failure), then WITH the skill (confirm compliance). For technique/reference skills, verify the commands in the body actually run. Report honestly.

#### Step 7 — Report

Tell the user:
1. Skill name + exact save path (and that it was auto-created, if you did it unprompted).
2. One-line summary of what it does.
3. Location caveat (project `.opencode/skills` loads only for this project; move to the global config for all-project use).

## Common Mistakes

| Mistake | Fix |
|---|---|
| Waiting to be asked before creating a skill | Create proactively at the moment you detect a repeatable pattern (Phase A) |
| Description summarizes the workflow | Rewrite to trigger-only "Use when…" |
| Name has spaces/parens or ≠ folder | Lowercase-hyphenated, match folder |
| Vague body ("add validation", "handle edge cases") | Provide concrete steps/code from actual observed work |
| Saved to C: when user wants D: only | Default to project `.opencode/skills` on D: |
| No self-check before reporting done | Run the Step 5 checklist |
| Duplicate of an existing skill | Check the skills folder first; prefer updating the existing one |
| Creating a skill for a genuine one-off | Skip — deduplicate against realistic future repeat |
