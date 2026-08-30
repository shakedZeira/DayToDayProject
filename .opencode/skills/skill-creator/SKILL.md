---
name: skill-creator
description: "Use when the user describes a new skill (or an edit/improvement to an existing skill) in plain English and wants it created automatically. Turns a natural-language request into a complete, well-formed SKILL.md saved to the skills folder. Use ONLY for authoring opencode/agent skills, not for application code."
---

# Skill Creator

## Overview

Turn a plain-English request ("I want a skill that X") into a complete, valid opencode skill file — correct frontmatter, a well-structured body of step-by-step instructions, saved to the right folder, with a short self-check. You are a factory, not a blank page: the user describes the skill, you produce the file.

Core principle: **A skill is a reference someone (a future agent) follows. It must be discoverable (good name + description), valid (correct frontmatter), and actionable (concrete steps, real content).** Produce that, tell the user where it went, and stop.

## When to Use

- "Create a skill that <does X>"
- "Add a skill for <recurring task>"
- "Make a skill to help with <problem>"

## When NOT to Use

- The user is describing application code/features, not a skill → use brainstorming/writing-plans instead.
- The user only wants information, not a new skill.

## The Workflow

Follow these steps in order. Do NOT skip the location decision or the self-check.

### Step 1 — Determine where the skill lives

Ask or infer the target location. Respect a hard "everything on D:" constraint if present.

| Scope | Path |
|---|---|
| Project (default, keeps work on D: for the DayToDayProject) | `<project>\.opencode\skills\<name>\SKILL.md` |
| Global | `~\.config\opencode\skills\<name>\SKILL.md` |

Rule: if the user wants everything off C:, default to the **project** location on D:. State explicitly in your final report where the file was written.

### Step 2 — Derive the skill name

- Lowercase, hyphen-separated, letters + numbers + hyphens only (no parens/special chars).
- Verb-first / gerund where possible (`creating-x`, `express-prisma-endpoint`).
- Matches the folder name exactly, ≤ 64 chars.

### Step 3 — Write the description (most important field)

- Must start with **"Use when…"**.
- Describes **when to trigger** the skill, NOT what the skill's workflow does.
- Do NOT summarize the body's steps in the description (agents follow a summarized description and skip reading the body).
- Write in third person. Front-load concrete trigger keywords.

```
# BAD: "Use when testing - writes failing test, runs it, implements, refactors"
# GOOD: "Use when implementing any feature or bugfix, before writing implementation code"
```

### Step 4 — Write the SKILL.md body

Structure:

```markdown
---
name: <name>
description: "<Use when… trigger-only>"
---

# <Title>

## Overview          # core principle in 1-2 sentences
## When to Use       # concrete triggers; When NOT to Use
## <Core Workflow>   # numbered steps with real content
## Quick Reference   # table/bullets for scanning
## Common Mistakes   # what goes wrong + fixes
```

- Every step must carry **real, concrete content** — no "TBD", "TODO", "implement later", "add appropriate handling", "similar to Task N".
- Include actual commands/code/snippets the future agent needs, matching the project's real conventions (paths, libraries, naming).
- For discipline skills (rules to enforce), add a red-flags list + rationalization table. For technique/reference skills, focus on a clear how-to.

### Step 5 — Validate the file

Before reporting done, check:

- [ ] Exact file exists at `<target>\<name>\SKILL.md`
- [ ] Frontmatter has `name` (matches folder) and `description` (starts "Use when…")
- [ ] Body has no TBD/TODO/placeholder phrases
- [ ] Commands/paths match the actual project (verify by reading the project if unsure)

### Step 6 — Verify behavior (optional but recommended)

If the skill enforces a discipline, run a quick baseline probe: ask a fresh agent to do a small task WITHOUT the skill and observe the failure; then re-run with the skill and confirm it complies. Report the outcome honestly.

### Step 7 — Report

Tell the user:
1. Skill name and exact save path.
2. One-line summary of what it does.
3. Any location caveat (e.g., "saved to project .opencode/skills on D: — it will load only for this project; for global use, move it to ~/.config/opencode/skills").

## Common Mistakes

| Mistake | Fix |
|---|---|
| Description summarizes the workflow | Rewrite to trigger-only "Use when…" |
| Name has spaces/parens or ≠ folder | Lowercase-hyphenated, match folder |
| Vague body ("add validation", "handle edge cases") | Provide concrete steps/code |
| Saved to C: when user wants D: only | Default to project `.opencode/skills` on D: |
| No self-check before reporting done | Run the Step 5 checklist |
