export interface ProgressionChartPoint {
  date: string;
  maxKg: number;
}

interface Props {
  points: ProgressionChartPoint[];
}

const W = 320;
const H = 160;
const PAD = 20;

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export default function ProgressionChart({ points }: Props) {
  if (points.length < 2) {
    return (
      <p className="text-sm text-slate-400">
        Log at least 2 sets to see your progression chart.
      </p>
    );
  }

  let maxY = 0;
  for (const p of points) {
    if (p.maxKg > maxY) maxY = p.maxKg;
  }
  const minX = PAD;
  const maxX = W - PAD;
  const maxYpx = PAD;
  const minYpx = H - PAD;
  const spanX = points.length > 1 ? points.length - 1 : 1;

  const coords = points.map((p, i) => {
    const x = minX + (i / spanX) * (maxX - minX);
    const y = maxY > 0 ? minYpx - (p.maxKg / maxY) * (minYpx - maxYpx) : minYpx;
    return { x, y, ...p };
  });

  const linePath = coords.map((c, i) => `${i === 0 ? "M" : "L"} ${c.x} ${c.y}`).join(" ");

  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-auto">
      <polyline
        fill="none"
        stroke="#4f46e5"
        strokeWidth={2}
        strokeLinejoin="round"
        strokeLinecap="round"
        points={coords.map((c) => `${c.x},${c.y}`).join(" ")}
      />
      {coords.map((c) => (
        <circle key={c.date} cx={c.x} cy={c.y} r={4} fill="#4f46e5">
          <title>
            {formatDate(c.date)}: {c.maxKg} kg
          </title>
        </circle>
      ))}
    </svg>
  );
}
