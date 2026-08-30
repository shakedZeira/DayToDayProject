import { useEffect, useState } from "react";
import { fetchHealth } from "./api";

export default function App() {
  const [backend, setBackend] = useState<string>("connecting\u2026");

  useEffect(() => {
    fetchHealth()
      .then(() => setBackend("connected"))
      .catch(() => setBackend("unreachable"));
  }, []);

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col items-center justify-center gap-4 p-6">
      <h1 className="text-3xl font-bold text-slate-800">Day To Day</h1>
      <p className="text-slate-500">
        Your personal daily companion.
      </p>
      <p className="text-sm text-slate-400">
        Backend: <span className="text-indigo-600">{backend}</span>
      </p>
    </div>
  );
}
