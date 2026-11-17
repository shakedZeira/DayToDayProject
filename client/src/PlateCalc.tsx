import { useState } from "react";
import { calculatePlates, DEFAULT_BAR_KG } from "./plates";

export default function PlateCalc({ weightKg }: { weightKg: number }) {
  const [bar, setBar] = useState(DEFAULT_BAR_KG);
  const r = calculatePlates(weightKg, bar);
  return (
    <div className="rounded-md3xs bg-slate-50 border border-md-outline px-3 py-2 text-sm text-slate-700 flex flex-col gap-1" data-testid="plate-calc">
      <div className="flex items-center gap-2">
        <span className="font-medium">Plates for {weightKg}kg</span>
        <label className="text-xs text-slate-500 ml-auto">
          Bar{" "}
          <select value={bar} onChange={(e) => setBar(Number(e.target.value))} className="border border-md-outline rounded-md3xs px-1 py-0.5">
            <option value={20}>20kg</option>
            <option value={15}>15kg</option>
            <option value={10}>10kg</option>
          </select>
        </label>
      </div>
      {!r.valid ? (
        <span className="text-slate-400">Weight is lighter than the bar.</span>
      ) : r.perSide.length === 0 ? (
        <span>Just the bar.</span>
      ) : (
        <span>
          Per side: {r.perSide.map((p) => (p.count > 1 ? `${p.count}x${p.plate}` : `${p.plate}`)).join(" + ")} kg
        </span>
      )}
      {r.valid && r.remainderKg > 0 && (
        <span className="text-xs text-amber-600">Closest loadable: {r.achievableKg}kg ({r.remainderKg}kg short)</span>
      )}
    </div>
  );
}
