import { useEffect, useRef } from "react";

interface Props {
  message: string | null;
  type?: "success" | "error";
  onDismiss: () => void;
  /** ms before auto-dismiss; 0 disables. */
  duration?: number;
}

/** Transient message. The live region stays mounted so screen readers announce text changes. */
export default function Toast({ message, type = "success", onDismiss, duration = 5000 }: Props) {
  const dismissRef = useRef(onDismiss);
  dismissRef.current = onDismiss;
  useEffect(() => {
    if (!message || !duration) return;
    const id = setTimeout(() => dismissRef.current(), duration);
    return () => clearTimeout(id);
  }, [message, duration]);

  return (
    <div
      role={type === "error" ? "alert" : "status"}
      aria-live={type === "error" ? "assertive" : "polite"}
      aria-atomic="true"
      className="fixed bottom-24 inset-x-0 z-40 flex justify-center px-4 pointer-events-none"
    >
      {message && (
        <div
          className={`pointer-events-auto flex items-center gap-2 rounded-md3s px-4 py-3 text-sm text-white shadow-lg ${
            type === "success" ? "bg-green-900" : "bg-red-900"
          }`}
        >
          <span>{message}</span>
          <button type="button" onClick={onDismiss} aria-label="Dismiss message" className="ml-2 rounded px-1 opacity-80 hover:opacity-100">
            <span aria-hidden="true">✕</span>
          </button>
        </div>
      )}
    </div>
  );
}
