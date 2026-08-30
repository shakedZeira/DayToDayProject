---
name: aggregate-shared-digest
description: Use when aggregating data from multiple modules into one dashboard payload, such as building a focus digest or a progress overview that fans out to several sources and combines them into a single response.
---

# Aggregate Shared Digest

## Overview

The shared digest pattern is the standard way to combine data from several modules into one dashboard payload. Define a shared response type once in `shared/src/index.ts`, expose one aggregation route on the server, and call it from the client through a single api helper — so the consumer only ever sees one typed response, never per-module calls.

## When to Use

- You need a dashboard/overview endpoint that merges output from 2+ modules (e.g., focus digest, progress overview).
- The client must render several sections from one round-trip instead of making many requests.
- You want a single typed contract shared by server and client so the response shape cannot drift.

## When NOT to Use

- A single module's data with no fan-out — just return it directly, no aggregation step.
- Per-resource CRUD endpoints (create one route per resource; do not force a digest).
- The consumer genuinely needs to fetch sections independently and with different cadence — prefer separate endpoints then.

## Core Workflow

1. **Locate the shared types file.** Open `shared/src/index.ts` at the repo root. This is the single source of truth for types shared between the server and the web client.

2. **Define the shared response type.** Add an exported interface that describes the aggregated payload. Name it after the digest (e.g., `FocusDigestResponse`). Import types from each module you slice into it. Example:

   ```ts
   import { FocusBlock } from "../focus/types";
   import { ProgressMilestone } from "../progress/types";

   export interface FocusDigestResponse {
     blocks: FocusBlock[];
     milestones: ProgressMilestone[];
     generatedAt: string;
   }
   ```

3. **Build one aggregation route on the server.** Create a single route (e.g., `GET /api/focus/digest`) that fans out to each source module, resolves all of them in parallel, merges the results into the shared response type, and returns it. Example:

   ```ts
   router.get("/api/focus/digest", async (req, res) => {
     const [blocks, milestones] = await Promise.all([
       focusService.getBlocks(),
       progressService.getMilestones(req.user.id),
     ]);
     const response: FocusDigestResponse = {
       blocks,
       milestones,
       generatedAt: new Date().toISOString(),
     };
     res.json(response);
   });
   ```

4. **Add a client api helper.** Add one function in the client that fetches the digest route and returns the typed response. It should live next to the other api helpers and return the shared type:

   ```ts
   import { FocusDigestResponse } from "@repo/shared";

   export async function fetchFocusDigest(): Promise<FocusDigestResponse> {
     const res = await fetch("/api/focus/digest");
     if (!res.ok) throw new Error(`digest failed: ${res.status}`);
     return res.json();
   }
   ```

5. **Consume the single response.** Have the dashboard pass the one `FocusDigestResponse` down to section components. Do not call per-module endpoints from the client.

## Quick Reference

| Concern | Standard |
| --- | --- |
| Shared response type | Defined in `shared/src/index.ts` |
| Server aggregation | One route, `Promise.all` fan-out, returns the shared type |
| Client access | One api helper returning the shared type |
| Network round-trips | One request per dashboard render |
| Type safety | Single contract shared by server + client |

## Common Mistakes

| Mistake | Fix |
| --- | --- |
| Defining the response type inside a module instead of `shared/` | Move it to `shared/src/index.ts` so both sides import the same type |
| Nesting per-module calls on the client | Resolve everything on the server via one aggregation route, return once |
| Calling module endpoints sequentially on the server | Use `Promise.all` so sources fetch in parallel |
| Returning a plain object with no shared type | Annotate the route's return with the shared response type |
| Creating a separate route/helper per section in the digest | Consolidate into one route and one helper |
| Letting server/client response shapes drift | Import the shared type on both sides; never redeclare it |
