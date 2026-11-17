import { useEffect, useState } from "react";
import { getOutbox, startOutboxSync } from "./outboxRuntime";
import type { OutboxState } from "./outbox";

export default function OutboxStatus() {
  const [state, setState] = useState<OutboxState>(() => getOutbox().getState());

  useEffect(() => {
    const outbox = getOutbox();
    const unsub = outbox.subscribe(setState);
    const stop = startOutboxSync(outbox);
    return () => {
      unsub();
      stop();
    };
  }, []);

  const idle = state.pending === 0 && state.notes.length === 0;

  // The live region stays mounted (empty) so screen readers announce changes when content appears.
  return (
    <div
      role="status"
      aria-live="polite"
      aria-atomic="false"
      className={idle ? "sr-only" : "text-xs text-slate-600 bg-amber-50 border-b border-amber-200 px-4 py-1 flex flex-wrap items-center gap-2"}
    >
      {state.pending > 0 && (
        <span>
          {state.pending} change{state.pending === 1 ? "" : "s"} waiting to sync
          {state.paused === "auth" ? " (sign in again to sync)" : state.flushing ? " (syncing…)" : ""}
        </span>
      )}
      {state.notes.map((n, i) => (
        <span key={i} role="alert" className="text-red-700">{n}</span>
      ))}
      {state.notes.length > 0 && (
        <button type="button" className="underline" onClick={() => getOutbox().dismissNotes()}>Dismiss</button>
      )}
    </div>
  );
}
