export interface ExerciseArchetype {
  id: string;
  label: string;
  keywords: string[];
  exclude?: string[];
}

export const GENERIC_ARCHETYPE: ExerciseArchetype = {
  id: "generic",
  label: "Perform a controlled strength rep with full range of motion and good form.",
  keywords: [],
};

export const ARCHETYPES: ExerciseArchetype[] = [
  {
    id: "leg-curl",
    label: "Curl heels toward glutes, squeeze hamstrings, lower with control.",
    keywords: ["leg curl"],
  },
  {
    id: "calf-raise",
    label: "Press through the balls of the feet, rise to the toes, lower with control.",
    keywords: ["calf raise"],
  },
  {
    id: "tricep",
    label: "Pin upper arms, extend elbows fully, control the return.",
    keywords: ["tricep", "kickback", "pushdown"],
  },
  {
    id: "snatch",
    label: "Explode hips to launch the weight overhead, catch with the arm locked.",
    keywords: ["snatch"],
  },
  {
    id: "swing",
    label: "Hike the bell back between your hips, snap the hips forward to launch it.",
    keywords: ["swing"],
  },
  {
    id: "burpee",
    label: "Squat, kick feet back, drop into a push-up, jump feet in, stand and jump.",
    keywords: ["burpee"],
  },
  {
    id: "squat",
    label: "Brace core, drive through heels, extend hips & knees to stand.",
    keywords: ["squat", "bulgarian", "leg press"],
  },
  {
    id: "lunge",
    label: "Step forward, drop the back knee to the floor, push through the front heel to stand.",
    keywords: ["lunge"],
  },
  {
    id: "hinge",
    label: "Hinge at the hips, push hips back, keep the spine neutral, drive up, squeeze glutes.",
    keywords: ["deadlift", "rdl", "glute bridge", "hip thrust", "good morning", "hinge"],
  },
  {
    id: "vertical-pull",
    label: "Pull elbows down to drive the chest to the bar, lower under control.",
    keywords: ["pull-up", "pull up", "pullup", "chin-up", "chin up", "chinup", "lat pulldown", "pulldown", "assisted pull"],
  },
  {
    id: "vertical-press",
    label: "Press overhead from the shoulders, lock out, lower with control.",
    keywords: ["overhead press", "shoulder press", "pike push", "kettlebell press"],
  },
  {
    id: "horizontal-push",
    label: "Press the weight away from your chest to full arm extension, lower with control.",
    keywords: ["bench press", "push-up", "pushup", "chest press", "pec deck", "cable fly", "dips", "fly"],
  },
  {
    id: "horizontal-pull",
    label: "Pull elbows back and squeeze the shoulder blades, return with control.",
    keywords: ["row", "face pull", "pull-apart", "pull apart"],
  },
  {
    id: "raise",
    label: "Keep arms straight, raise to shoulder height, lower slowly.",
    keywords: ["raise", "lateral"],
    exclude: ["calf raise"],
  },
  {
    id: "plank",
    label: "Hold a straight line from head to heels, brace the core, breathe.",
    keywords: ["plank", "superman"],
  },
  {
    id: "crunch",
    label: "Curl the ribs toward the hips, exhale as you rise, lower with control.",
    keywords: ["crunch", "mountain climbers", "bicycle", "sit-up", "situp"],
  },
  {
    id: "curl",
    label: "Keep elbows pinned at your sides, curl hands to shoulders, lower slowly.",
    keywords: ["curl"],
    exclude: ["leg curl"],
  },
  {
    id: "shrug",
    label: "Drive shoulders straight up toward your ears, pause, lower slowly.",
    keywords: ["shrug"],
  },
  {
    id: "leg-extension",
    label: "Extend the knees to lift the pad, squeeze the quads, lower slowly.",
    keywords: ["leg extension"],
  },
];

export function getExerciseArchetype(exerciseName: string): ExerciseArchetype {
  const name = exerciseName.toLowerCase().replace(/\s+/g, " ").trim();
  for (const archetype of ARCHETYPES) {
    if (archetype.exclude && archetype.exclude.some((k) => name.includes(k))) continue;
    if (archetype.keywords.some((k) => name.includes(k))) return archetype;
  }
  return GENERIC_ARCHETYPE;
}