import type { ExerciseArchetype } from "./exerciseDemos";

const SLATE = "#334155";
const MUTED = "#94a3b8";
const ACCENT = "#4f46e5";
const STROKE = {
  stroke: SLATE,
  strokeWidth: 2.5,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  fill: "none",
} as const;

interface TProps {
  type: "translate" | "rotate";
  values: string;
  dur?: string;
  keyTimes?: string;
  keySplines?: string;
  calcMode?: "spline" | "linear";
}

function T({
  type,
  values,
  dur = "2.4s",
  keyTimes = "0;0.5;1",
  keySplines = "0.45 0 0.55 1;0.45 0 0.55 1",
  calcMode = "spline",
}: TProps) {
  return (
    <animateTransform
      attributeName="transform"
      type={type}
      values={values}
      dur={dur}
      keyTimes={keyTimes}
      keySplines={keySplines}
      calcMode={calcMode}
      repeatCount="indefinite"
    />
  );
}

function Squat() {
  return (
    <g transform="translate(52,52)">
      <g>
        <T type="translate" values="0 0; 0 5; 0 0" />
        <path d="M0 -28 L0 0" {...STROKE} />
        <circle cx="0" cy="-35" r="4" fill={SLATE} />
        <path d="M0 -24 L9 -17 L16 -10" {...STROKE} />
        <g>
          <T type="rotate" values="-4 0 0; 16 0 0; -4 0 0" />
          <line x1="0" y1="0" x2="0" y2="26" {...STROKE} />
          <g>
            <T type="rotate" values="6 0 26; -10 0 26; 6 0 26" />
            <line x1="0" y1="26" x2="2" y2="54" {...STROKE} />
            <line x1="2" y1="54" x2="14" y2="54" {...STROKE} />
          </g>
        </g>
      </g>
    </g>
  );
}

function Hinge() {
  return (
    <g>
      <g>
        <T type="rotate" values="0 52 52; 92 52 52; 0 52 52" />
        <circle cx="50" cy="16" r="4" fill={SLATE} />
        <path d="M52 22 L52 52" {...STROKE} />
      </g>
      <line x1="52" y1="52" x2="50" y2="78" {...STROKE} />
      <line x1="50" y1="78" x2="52" y2="106" {...STROKE} />
      <line x1="52" y1="106" x2="62" y2="106" {...STROKE} />
      <g>
        <T type="translate" values="0 0; 0 18; 0 0" />
        <path d="M52 26 L48 48 L50 70" {...STROKE} />
        <line x1="38" y1="70" x2="64" y2="70" {...STROKE} />
        <rect x="34" y="66" width="4" height="8" rx="1" fill={ACCENT} />
        <rect x="64" y="66" width="4" height="8" rx="1" fill={ACCENT} />
      </g>
    </g>
  );
}

function Swing() {
  return (
    <g>
      <g>
        <T type="rotate" values="-10 52 52; 16 52 52; -10 52 52" dur="1.6s" />
        <circle cx="50" cy="16" r="4" fill={SLATE} />
        <path d="M52 22 L52 52" {...STROKE} />
        <path d="M52 30 L46 50" {...STROKE} />
      </g>
      <line x1="52" y1="52" x2="48" y2="78" {...STROKE} />
      <line x1="48" y1="78" x2="50" y2="106" {...STROKE} />
      <line x1="50" y1="106" x2="60" y2="106" {...STROKE} />
      <g>
        <T type="translate" values="0 18; -2 -26; 0 18" dur="1.6s" />
        <path d="M42 80 C46 72, 56 74, 60 82" stroke={SLATE} strokeWidth="2.5" fill="none" strokeLinecap="round" />
        <circle cx="50" cy="86" r="7" fill={ACCENT} />
      </g>
    </g>
  );
}

function Burpee() {
  const kt = "0;0.3;0.45;0.6;0.8;1";
  return (
    <g>
      <g>
        <T type="translate" values="0 0; 0 8; 0 8; 0 -22; 0 -22; 0 0" keyTimes={kt} calcMode="linear" />
        <circle cx="52" cy="14" r="4" fill={SLATE} />
        <path d="M52 50 L52 24" {...STROKE} />
        <g>
          <T
            type="rotate"
            values="0 52 26; 40 52 26; 40 52 26; -170 52 26; -170 52 26; 0 52 26"
            keyTimes={kt}
            calcMode="linear"
          />
          <line x1="52" y1="26" x2="52" y2="48" {...STROKE} />
        </g>
        <line x1="52" y1="52" x2="50" y2="76" {...STROKE} />
        <line x1="50" y1="76" x2="52" y2="108" {...STROKE} />
      </g>
    </g>
  );
}

function HorizontalPush() {
  return (
    <g>
      <line x1="24" y1="76" x2="88" y2="76" stroke={MUTED} strokeWidth="6" strokeLinecap="round" />
      <line x1="28" y1="76" x2="28" y2="112" stroke={MUTED} strokeWidth="3" />
      <line x1="84" y1="76" x2="84" y2="112" stroke={MUTED} strokeWidth="3" />
      <circle cx="34" cy="70" r="4" fill={SLATE} />
      <path d="M40 78 C52 73, 62 73, 70 76" {...STROKE} />
      <path d="M70 76 L82 88 L84 108" {...STROKE} />
      <g>
        <T type="translate" values="0 0; 0 13; 0 0" />
        <path d="M46 74 L46 56" {...STROKE} />
        <line x1="30" y1="54" x2="62" y2="54" {...STROKE} />
        <rect x="26" y="50" width="4" height="8" rx="1" fill={ACCENT} />
        <rect x="62" y="50" width="4" height="8" rx="1" fill={ACCENT} />
      </g>
    </g>
  );
}

function VerticalPress() {
  return (
    <g>
      <g>
        <T type="rotate" values="3 52 52; -6 52 52; 3 52 52" />
        <circle cx="52" cy="14" r="4" fill={SLATE} />
        <path d="M52 52 L52 24" {...STROKE} />
      </g>
      <line x1="52" y1="52" x2="50" y2="78" {...STROKE} />
      <line x1="50" y1="78" x2="52" y2="106" {...STROKE} />
      <line x1="52" y1="106" x2="62" y2="106" {...STROKE} />
      <g>
        <T type="translate" values="0 0; 0 -32; 0 0" />
        <path d="M52 30 L52 44" {...STROKE} />
        <line x1="36" y1="42" x2="68" y2="42" {...STROKE} />
        <rect x="32" y="38" width="4" height="8" rx="1" fill={ACCENT} />
        <rect x="64" y="38" width="4" height="8" rx="1" fill={ACCENT} />
      </g>
    </g>
  );
}

function HorizontalPull() {
  return (
    <g>
      <circle cx="62" cy="20" r="4" fill={SLATE} />
      <path d="M46 58 L60 28" {...STROKE} />
      <line x1="46" y1="58" x2="44" y2="84" {...STROKE} />
      <line x1="44" y1="84" x2="46" y2="108" {...STROKE} />
      <g>
        <T type="translate" values="0 0; -3 -12; 0 0" />
        <path d="M60 34 L58 62" {...STROKE} />
        <line x1="44" y1="64" x2="72" y2="64" {...STROKE} />
        <rect x="40" y="61" width="4" height="6" rx="1" fill={ACCENT} />
        <rect x="72" y="61" width="4" height="6" rx="1" fill={ACCENT} />
      </g>
    </g>
  );
}

function VerticalPull() {
  return (
    <g>
      <line x1="42" y1="16" x2="42" y2="6" stroke={MUTED} strokeWidth="2.5" />
      <line x1="58" y1="16" x2="58" y2="6" stroke={MUTED} strokeWidth="2.5" />
      <line x1="30" y1="16" x2="70" y2="16" stroke={ACCENT} strokeWidth="4" strokeLinecap="round" />
      <g>
        <T type="translate" values="0 0; 0 -15; 0 0" dur="3s" />
        <circle cx="44" cy="44" r="4" fill={SLATE} />
        <path d="M44 22 L44 38" {...STROKE} />
        <path d="M44 38 L44 66" {...STROKE} />
        <path d="M44 66 L44 88" {...STROKE} />
        <line x1="44" y1="88" x2="46" y2="106" {...STROKE} />
      </g>
    </g>
  );
}

function Curl() {
  return (
    <g>
      <circle cx="52" cy="16" r="4" fill={SLATE} />
      <path d="M52 44 L52 24" {...STROKE} />
      <line x1="52" y1="26" x2="52" y2="44" {...STROKE} />
      <g>
        <T type="rotate" values="0 52 44; -95 52 44; 0 52 44" />
        <line x1="52" y1="44" x2="52" y2="58" {...STROKE} />
        <rect x="46" y="55" width="11" height="6" rx="2" fill={ACCENT} />
      </g>
      <line x1="52" y1="52" x2="50" y2="80" {...STROKE} />
      <line x1="50" y1="80" x2="52" y2="108" {...STROKE} />
      <line x1="52" y1="108" x2="62" y2="108" {...STROKE} />
    </g>
  );
}

function Tricep() {
  return (
    <g>
      <circle cx="60" cy="18" r="4" fill={SLATE} />
      <path d="M44 54 L60 24" {...STROKE} />
      <line x1="56" y1="32" x2="70" y2="36" {...STROKE} />
      <g>
        <T type="rotate" values="8 70 36; -98 70 36; 8 70 36" />
        <line x1="70" y1="36" x2="70" y2="52" {...STROKE} />
        <rect x="65" y="49" width="10" height="6" rx="2" fill={ACCENT} />
      </g>
      <line x1="44" y1="54" x2="42" y2="82" {...STROKE} />
      <line x1="42" y1="82" x2="44" y2="108" {...STROKE} />
      <line x1="44" y1="108" x2="54" y2="108" {...STROKE} />
    </g>
  );
}

function Lunge() {
  return (
    <g>
      <g>
        <T type="translate" values="0 0; 0 8; 0 0" />
        <circle cx="52" cy="16" r="4" fill={SLATE} />
        <path d="M52 54 L52 26" {...STROKE} />
        <path d="M52 30 L60 38 L68 44" {...STROKE} />
      </g>
      <path d="M52 54 L60 80 L60 108" {...STROKE} />
      <line x1="60" y1="108" x2="70" y2="108" {...STROKE} />
      <g>
        <T type="rotate" values="0 46 54; 12 46 54; 0 46 54" />
        <line x1="46" y1="54" x2="28" y2="70" {...STROKE} />
        <g>
          <T type="rotate" values="0 28 70; -10 28 70; 0 28 70" />
          <line x1="28" y1="70" x2="28" y2="108" {...STROKE} />
          <line x1="28" y1="108" x2="38" y2="108" {...STROKE} />
        </g>
      </g>
    </g>
  );
}

function Raise() {
  return (
    <g>
      <circle cx="50" cy="16" r="4" fill={SLATE} />
      <path d="M50 28 L50 56" {...STROKE} />
      <line x1="48" y1="56" x2="46" y2="86" {...STROKE} />
      <line x1="52" y1="56" x2="54" y2="86" {...STROKE} />
      <line x1="46" y1="86" x2="50" y2="108" {...STROKE} />
      <line x1="54" y1="86" x2="58" y2="108" {...STROKE} />
      <g>
        <T type="rotate" values="0 40 28; 95 40 28; 0 40 28" />
        <line x1="40" y1="28" x2="40" y2="52" {...STROKE} />
        <rect x="34" y="49" width="12" height="6" rx="2" fill={ACCENT} />
      </g>
      <g>
        <T type="rotate" values="0 60 28; -95 60 28; 0 60 28" />
        <line x1="60" y1="28" x2="60" y2="52" {...STROKE} />
        <rect x="54" y="49" width="12" height="6" rx="2" fill={ACCENT} />
      </g>
    </g>
  );
}

function CalfRaise() {
  return (
    <g>
      <g>
        <T type="rotate" values="0 58 106; 9 58 106; 0 58 106" dur="1.8s" />
        <circle cx="50" cy="32" r="4" fill={SLATE} />
        <path d="M52 42 L52 54" {...STROKE} />
        <line x1="52" y1="54" x2="48" y2="74" {...STROKE} />
        <line x1="48" y1="74" x2="58" y2="106" {...STROKE} />
        <line x1="38" y1="106" x2="58" y2="106" {...STROKE} />
      </g>
    </g>
  );
}

function Plank() {
  return (
    <g>
      <g>
        <T type="translate" values="0 0; 0 1.1; 0 0" dur="1.1s" />
        <circle cx="38" cy="50" r="4" fill={SLATE} />
        <path d="M42 53 L62 56" {...STROKE} />
        <line x1="62" y1="56" x2="70" y2="92" {...STROKE} />
        <line x1="70" y1="92" x2="78" y2="101" {...STROKE} />
      </g>
      <path d="M42 53 L40 70" {...STROKE} />
      <line x1="36" y1="70" x2="44" y2="70" {...STROKE} />
    </g>
  );
}

function Crunch() {
  return (
    <g>
      <g>
        <T type="rotate" values="0 48 60; 32 48 60; 0 48 60" />
        <circle cx="30" cy="54" r="4" fill={SLATE} />
        <path d="M48 62 C40 62, 35 60, 33 57" {...STROKE} />
      </g>
      <path d="M58 62 L48 62" {...STROKE} />
      <path d="M58 62 L70 84" {...STROKE} />
      <line x1="70" y1="84" x2="64" y2="106" {...STROKE} />
      <line x1="30" y1="74" x2="42" y2="74" {...STROKE} />
    </g>
  );
}

function Shrug() {
  return (
    <g>
      <circle cx="50" cy="16" r="4" fill={SLATE} />
      <path d="M50 28 L50 54" {...STROKE} />
      <line x1="48" y1="54" x2="46" y2="84" {...STROKE} />
      <line x1="52" y1="54" x2="54" y2="84" {...STROKE} />
      <line x1="46" y1="84" x2="49" y2="108" {...STROKE} />
      <line x1="54" y1="84" x2="51" y2="108" {...STROKE} />
      <g>
        <T type="translate" values="0 0; 0 -7; 0 0" dur="1.8s" />
        <circle cx="40" cy="28" r="3.5" fill={SLATE} />
        <circle cx="60" cy="28" r="3.5" fill={SLATE} />
        <line x1="40" y1="28" x2="40" y2="50" {...STROKE} />
        <line x1="60" y1="28" x2="60" y2="50" {...STROKE} />
        <rect x="34" y="47" width="12" height="6" rx="2" fill={ACCENT} />
        <rect x="54" y="47" width="12" height="6" rx="2" fill={ACCENT} />
      </g>
    </g>
  );
}

function Snatch() {
  return (
    <g>
      <g>
        <T type="rotate" values="2 52 52; -7 52 52; 2 52 52" dur="1.8s" />
        <circle cx="52" cy="14" r="4" fill={SLATE} />
        <path d="M52 52 L52 24" {...STROKE} />
      </g>
      <line x1="52" y1="52" x2="50" y2="78" {...STROKE} />
      <line x1="50" y1="78" x2="52" y2="106" {...STROKE} />
      <line x1="52" y1="106" x2="62" y2="106" {...STROKE} />
      <g>
        <T type="rotate" values="0 52 28; -200 52 28; 0 52 28" dur="1.8s" />
        <line x1="52" y1="28" x2="58" y2="46" {...STROKE} />
        <circle cx="60" cy="52" r="6" fill={ACCENT} />
        <path d="M56 46 C56 42, 64 42, 64 46" stroke={SLATE} strokeWidth="2.5" fill="none" />
      </g>
    </g>
  );
}

function LegExtension() {
  return (
    <g>
      <line x1="26" y1="42" x2="26" y2="82" stroke={MUTED} strokeWidth="4" />
      <line x1="26" y1="82" x2="60" y2="82" stroke={MUTED} strokeWidth="6" strokeLinecap="round" />
      <line x1="30" y1="82" x2="30" y2="112" stroke={MUTED} strokeWidth="3" />
      <circle cx="42" cy="68" r="4" fill={SLATE} />
      <path d="M46 82 L42 72" {...STROKE} />
      <line x1="46" y1="82" x2="62" y2="86" {...STROKE} />
      <g>
        <T type="rotate" values="0 62 86; -85 62 86; 0 62 86" />
        <line x1="62" y1="86" x2="62" y2="106" {...STROKE} />
        <rect x="58" y="100" width="8" height="8" rx="2" fill={ACCENT} />
      </g>
    </g>
  );
}

function LegCurl() {
  return (
    <g>
      <line x1="24" y1="42" x2="24" y2="82" stroke={MUTED} strokeWidth="4" />
      <line x1="24" y1="82" x2="60" y2="82" stroke={MUTED} strokeWidth="6" strokeLinecap="round" />
      <line x1="28" y1="82" x2="28" y2="112" stroke={MUTED} strokeWidth="3" />
      <circle cx="38" cy="66" r="4" fill={SLATE} />
      <path d="M42 82 L40 70" {...STROKE} />
      <line x1="42" y1="82" x2="58" y2="88" {...STROKE} />
      <rect x="54" y="83" width="8" height="9" rx="2" fill={ACCENT} />
      <g>
        <T type="rotate" values="0 58 88; 115 58 88; 0 58 88" />
        <line x1="58" y1="88" x2="72" y2="88" {...STROKE} />
        <rect x="70" y="84" width="8" height="8" rx="2" fill={ACCENT} />
      </g>
    </g>
  );
}

function Generic() {
  return (
    <g>
      <circle cx="52" cy="16" r="4" fill={SLATE} />
      <path d="M52 34 L52 20" {...STROKE} />
      <line x1="52" y1="52" x2="50" y2="80" {...STROKE} />
      <line x1="50" y1="80" x2="52" y2="108" {...STROKE} />
      <line x1="52" y1="108" x2="62" y2="108" {...STROKE} />
      <g>
        <T type="translate" values="0 0; 0 -26; 0 0" dur="2.6s" />
        <line x1="52" y1="30" x2="52" y2="66" {...STROKE} />
        <line x1="40" y1="66" x2="64" y2="66" {...STROKE} />
        <rect x="36" y="63" width="4" height="6" rx="1" fill={ACCENT} />
        <rect x="64" y="63" width="4" height="6" rx="1" fill={ACCENT} />
      </g>
    </g>
  );
}

function renderArchetype(id: string) {
  switch (id) {
    case "squat":
      return <Squat />;
    case "hinge":
      return <Hinge />;
    case "swing":
      return <Swing />;
    case "burpee":
      return <Burpee />;
    case "horizontal-push":
      return <HorizontalPush />;
    case "vertical-press":
      return <VerticalPress />;
    case "horizontal-pull":
      return <HorizontalPull />;
    case "vertical-pull":
      return <VerticalPull />;
    case "curl":
      return <Curl />;
    case "tricep":
      return <Tricep />;
    case "lunge":
      return <Lunge />;
    case "raise":
      return <Raise />;
    case "calf-raise":
      return <CalfRaise />;
    case "plank":
      return <Plank />;
    case "crunch":
      return <Crunch />;
    case "shrug":
      return <Shrug />;
    case "snatch":
      return <Snatch />;
    case "leg-extension":
      return <LegExtension />;
    case "leg-curl":
      return <LegCurl />;
    default:
      return <Generic />;
  }
}

export default function ExerciseSVG({ archetype }: { archetype: ExerciseArchetype }) {
  return (
    <svg
      viewBox="0 0 100 120"
      role="img"
      aria-label={`Animated demonstration: ${archetype.label}`}
      className="h-40 w-40 sm:h-44 sm:w-44"
    >
      <line x1="6" y1="112" x2="94" y2="112" stroke="#cbd5e1" strokeWidth="2" />
      {renderArchetype(archetype.id)}
    </svg>
  );
}