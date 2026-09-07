interface NavItem {
  key: string;
  label: string;
}

const ITEMS: NavItem[] = [
  { key: "today", label: "Today" },
  { key: "tasks", label: "Tasks" },
  { key: "health", label: "Health" },
  { key: "study", label: "Study" },
  { key: "italian", label: "Italian" },
  { key: "progress", label: "Progress" },
  { key: "settings", label: "Settings" }
];

interface Props {
  view: string;
  onNavigate: (view: string) => void;
}

export default function Nav({ view, onNavigate }: Props) {
  const base = "px-3 py-2 text-sm font-medium rounded";
  const active = "bg-indigo-600 text-white";
  const idle = "text-slate-600 hover:bg-slate-200";
  return (
    <nav className="fixed bottom-0 inset-x-0 bg-white border-t md:static md:border-y">
      <ul className="flex md:flex-wrap md:gap-1 md:px-4 md:py-2">
        {ITEMS.map((item) => (
          <li key={item.key} className="flex-1 md:flex-none">
            <button
              className={`w-full md:w-auto ${base} ${view === item.key ? active : idle}`}
              onClick={() => onNavigate(item.key)}
            >
              {item.label}
            </button>
          </li>
        ))}
      </ul>
    </nav>
  );
}
