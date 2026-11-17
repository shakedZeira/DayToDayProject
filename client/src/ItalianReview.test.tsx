import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import ItalianReview, { promptText } from "./ItalianReview";

describe("ItalianReview", () => {
  it("renders a loading state", () => {
    expect(renderToStaticMarkup(<ItalianReview token="t" onClose={() => {}} />)).toContain("Loading");
  });
  it("builds prompts per exercise kind", () => {
    expect(promptText({ id: "a", kind: "choice", direction: "en_to_it", prompt: "hello", choices: ["ciao"], answerIndex: 0 })).toBe("hello");
    expect(promptText({ id: "b", kind: "match", pairs: [{ left: "ciao", right: "hi" }] })).toContain("ciao");
  });
});
