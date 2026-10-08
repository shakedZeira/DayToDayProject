import { describe, it, expect } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import ItalianPath from "./ItalianPath";

describe("ItalianPath", () => {
  it("renders a loading state before course data arrives", () => {
    const html = renderToStaticMarkup(<ItalianPath token="tok" />);
    expect(html).toContain("Loading");
  });

  it("renders without a course or progress on first paint", () => {
    const html = renderToStaticMarkup(<ItalianPath token="tok" />);
    expect(html).not.toContain("XP");
    expect(html).not.toContain("Continue: Next lesson");
  });
});
