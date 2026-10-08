import { describe, it, expect } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import type { ItalianExercise, ItalianLessonDef } from "shared";
import ItalianLessonRunner, { buildAnswer, correctText, nextHearts } from "./ItalianLessonRunner";

function render(lesson: ItalianLessonDef): string {
  return renderToStaticMarkup(<ItalianLessonRunner token="tok" lesson={lesson} onDone={() => {}} />);
}

const choiceLesson: ItalianLessonDef = {
  id: "l1",
  title: "Greetings",
  exercises: [
    { id: "e1", kind: "choice", direction: "en_to_it", prompt: "hello", choices: ["ciao", "grazie"], answerIndex: 0 }
  ]
};

const listenLesson: ItalianLessonDef = {
  id: "l2",
  title: "Listening",
  exercises: [
    {
      id: "e2",
      kind: "choice",
      direction: "listen",
      prompt: "Tap what you hear",
      choices: ["Goodbye", "Hello"],
      answerIndex: 1,
      speak: "Ciao"
    }
  ]
};

const typeLesson: ItalianLessonDef = {
  id: "l3",
  title: "Writing",
  exercises: [
    { id: "e3", kind: "type", prompt: "Write in Italian: thank you", accepted: ["grazie"] }
  ]
};

const matchLesson: ItalianLessonDef = {
  id: "l4",
  title: "Pairs",
  exercises: [
    {
      id: "e4",
      kind: "match",
      pairs: [
        { left: "ciao", right: "hello" },
        { left: "grazie", right: "thank you" },
        { left: "prego", right: "you're welcome" },
        { left: "scusi", right: "excuse me" }
      ]
    }
  ]
};

describe("ItalianLessonRunner initial render", () => {
  it("shows title, counter, five hearts and an empty progress bar", () => {
    const html = render(choiceLesson);
    expect(html).toContain("Greetings");
    expect(html).toContain("1/1");
    expect((html.match(/❤️/g) ?? []).length).toBe(5);
    expect(html).toContain("width:0%");
  });

  it("renders a choice exercise with its prompt, choices and a disabled Check", () => {
    const html = render(choiceLesson);
    expect(html).toContain("hello");
    expect(html).toContain("ciao");
    expect(html).toContain("grazie");
    expect(html).toMatch(/<button[^>]*\sdisabled=""[^>]*>Check<\/button>/);
  });

  it("renders a Play button for listen exercises", () => {
    const html = render(listenLesson);
    expect(html).toContain("Tap what you hear");
    expect(html).toMatch(/🔊/);
    expect(html).toContain("Goodbye");
    expect(html).toContain("Hello");
  });

  it("renders a type exercise with an enabled input and a disabled Check", () => {
    const html = render(typeLesson);
    expect(html).toContain("Write in Italian: thank you");
    expect(html).toContain('placeholder="Type in Italian…"');
    expect(html).not.toMatch(/<input[^>]*disabled/);
    expect(html).toMatch(/<button[^>]*\sdisabled=""[^>]*>Check<\/button>/);
  });

  it("renders both match columns with a disabled Check", () => {
    const html = render(matchLesson);
    expect(html).toContain("Match the pairs");
    const unescaped = html.replace(/&#x27;/g, "'");
    for (const text of ["ciao", "grazie", "prego", "scusi", "hello", "thank you", "you're welcome", "excuse me"]) {
      expect(unescaped).toContain(text);
    }
    expect(html).toMatch(/<button[^>]*\sdisabled=""[^>]*>Check<\/button>/);
  });
});

describe("correctText", () => {
  const choice: ItalianExercise = {
    id: "c",
    kind: "choice",
    direction: "en_to_it",
    prompt: "hi",
    choices: ["ciao", "salve"],
    answerIndex: 1
  };
  const type: ItalianExercise = { id: "t", kind: "type", prompt: "p", accepted: ["grazie"] };
  const match: ItalianExercise = {
    id: "m",
    kind: "match",
    pairs: [
      { left: "ciao", right: "hello" },
      { left: "grazie", right: "thank you" }
    ]
  };

  it("shows the accepted text for type exercises", () => {
    expect(correctText(type)).toBe("grazie");
  });

  it("shows the correct choice for choice exercises", () => {
    expect(correctText(choice)).toBe("salve");
  });

  it("shows every pair for match exercises", () => {
    expect(correctText(match)).toBe("ciao = hello, grazie = thank you");
  });
});

describe("buildAnswer", () => {
  const choice: ItalianExercise = {
    id: "c1",
    kind: "choice",
    direction: "en_to_it",
    prompt: "hi",
    choices: ["ciao"],
    answerIndex: 0
  };
  const type: ItalianExercise = { id: "t1", kind: "type", prompt: "p", accepted: ["grazie"] };
  const match: ItalianExercise = { id: "m1", kind: "match", pairs: [{ left: "ciao", right: "hello" }] };

  it("builds a choice answer from the pick", () => {
    expect(
      buildAnswer(choice, { choicePick: 0, typeText: "", matchPicks: [] })
    ).toEqual({ exerciseId: "c1", kind: "choice", choiceIndex: 0 });
  });

  it("builds a choice answer with index -1 when nothing is picked", () => {
    expect(
      buildAnswer(choice, { choicePick: null, typeText: "", matchPicks: [] })
    ).toEqual({ exerciseId: "c1", kind: "choice", choiceIndex: -1 });
  });

  it("builds a type answer from the typed text", () => {
    expect(
      buildAnswer(type, { choicePick: null, typeText: "Grazie", matchPicks: [] })
    ).toEqual({ exerciseId: "t1", kind: "type", text: "Grazie" });
  });

  it("builds a match answer from the pairs", () => {
    expect(
      buildAnswer(match, { choicePick: null, typeText: "", matchPicks: [{ left: "ciao", right: "hello" }] })
    ).toEqual({ exerciseId: "m1", kind: "match", pairs: [{ left: "ciao", right: "hello" }] });
  });
});

describe("nextHearts", () => {
  it("keeps hearts on a correct answer", () => {
    expect(nextHearts(5, true)).toBe(5);
    expect(nextHearts(1, true)).toBe(1);
  });

  it("decrements hearts on a wrong answer", () => {
    expect(nextHearts(5, false)).toBe(4);
    expect(nextHearts(2, false)).toBe(1);
  });

  it("never lets hearts go below zero", () => {
    expect(nextHearts(1, false)).toBe(0);
    expect(nextHearts(0, false)).toBe(0);
  });
});
