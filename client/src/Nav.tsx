import { useState } from "react";

// Mirrors composeApp/.../ui/screen/BottomNavHost.kt: Material 3 NavigationBar with an icon, a 12sp
// label and a primaryContainer pill behind the selected icon. NBA is Android-only (no web feature),
// and the web-only screens (Tasks, Nutrition, Progress) live under "More".
interface NavItem {
  key: string;
  label: string;
  icon: string; // Material icon path (24x24)
}

const ICONS = {
  home: "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
  workout: "M20.57 14.86L22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 9.14 3.43 7.71 2 5.57 4.14 4.14 2.71 2.71 4.14l1.43 1.43L2 7.71l1.43 1.43L2 10.57 3.43 12 7 8.43 15.57 17 12 20.57 13.43 22l1.43-1.43L16.29 22l2.14-2.14 1.43 1.43 1.43-1.43-1.43-1.43L22 16.29z",
  goals: "M14.4 6L14 4H5v17h2v-7h5.6l.4 2h7V6z",
  italian: "M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5 1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 1.05.1.05.15.05.25.05.25 0 .5-.25.5-.5V6c-.6-.45-1.25-.75-2-1zm0 13.5c-1.1-.35-2.3-.5-3.5-.5-1.7 0-4.15.65-5.5 1.5V8c1.35-.85 3.8-1.5 5.5-1.5 1.2 0 2.4.15 3.5.5v11.5z",
  cooking: "M11.5 2C11.5 2 5 8.5 5 14a7 7 0 0014 0c0-2.4-1.1-4.6-2.4-6.3-.4 1.2-1.3 2.3-2.2 2.3C13 10 13 7 11.5 2z",
  settings: "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.488.488 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1115.6 12 3.6 3.6 0 0112 15.6z",
  more: "M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"
};

const ITEMS: NavItem[] = [
  { key: "today", label: "Home", icon: ICONS.home },
  { key: "workouts", label: "Workout", icon: ICONS.workout },
  { key: "goals", label: "Goals", icon: ICONS.goals },
  { key: "italian", label: "Italian", icon: ICONS.italian },
  { key: "cooking", label: "בישול", icon: ICONS.cooking },
  { key: "settings", label: "Settings", icon: ICONS.settings },
  { key: "more", label: "More", icon: ICONS.more }
];

const MORE_ITEMS = [
  { key: "tasks", label: "Tasks" },
  { key: "nutrition", label: "Nutrition" },
  { key: "progress", label: "Progress" },
  { key: "review", label: "Weekly review" }
];

interface Props {
  view: string;
  onNavigate: (view: string) => void;
}

export default function Nav({ view, onNavigate }: Props) {
  const [moreOpen, setMoreOpen] = useState(false);
  const moreActive = MORE_ITEMS.some((m) => m.key === view);

  function go(key: string) {
    if (key === "more") {
      setMoreOpen((o) => !o);
      return;
    }
    setMoreOpen(false);
    onNavigate(key);
  }

  return (
    <>
      {moreOpen && (
        <div className="fixed inset-0 z-20" onClick={() => setMoreOpen(false)}>
          <ul className="absolute bottom-20 right-3 bg-md-surface rounded-md3m shadow-lg border border-md-outlineVariant overflow-hidden min-w-[10rem]">
            {MORE_ITEMS.map((m) => (
              <li key={m.key}>
                <button
                  className={`w-full text-left px-4 py-3 text-sm font-medium hover:bg-md-surfaceVariant ${
                    view === m.key ? "text-md-primary" : "text-md-onSurface"
                  }`}
                  onClick={(e) => {
                    e.stopPropagation();
                    go(m.key);
                  }}
                >
                  {m.label}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
      <nav
        aria-label="Main"
        className="fixed bottom-0 inset-x-0 z-30 bg-md-surface border-t border-md-outlineVariant md:static md:border-y"
      >
        <ul className="flex max-w-3xl mx-auto">
          {ITEMS.map((item) => {
            const selected = item.key === "more" ? moreActive || moreOpen : view === item.key;
            return (
              <li key={item.key} className="flex-1">
                <button
                  aria-current={selected ? "page" : undefined}
                  className="w-full flex flex-col items-center gap-1 pt-3 pb-3"
                  onClick={() => go(item.key)}
                >
                  <span
                    className={`flex items-center justify-center w-16 h-8 rounded-full transition-colors ${
                      selected ? "bg-md-primaryContainer text-md-onPrimaryContainer" : "text-md-onSurfaceVariant"
                    }`}
                  >
                    <svg viewBox="0 0 24 24" className="w-6 h-6" fill="currentColor" aria-hidden="true">
                      <path d={item.icon} />
                    </svg>
                  </span>
                  <span
                    className={`text-xs leading-4 ${
                      selected ? "font-semibold text-md-onSurface" : "font-medium text-md-onSurfaceVariant"
                    }`}
                  >
                    {item.label}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      </nav>
    </>
  );
}
