// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Foods from "./Foods";
import NutritionSummary from "./NutritionSummary";
import { mockApi, type MockCall } from "./testFetch";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("nutrition macros", () => {
  it("renders macro bars from the summary", () => {
    render(
      <NutritionSummary
        summary={{
          date: "2026-11-20", target: 2000, consumed: 500, remaining: 1500,
          macros: { proteinG: 30, carbsG: 15, fatG: 7.5 },
          macroTargets: { proteinG: 150, carbsG: 225, fatG: 56 },
          meals: [{ id: "m1", foodName: "Macro Food", grams: 150, calories: 300, proteinG: 30, carbsG: 15, fatG: 7.5 }],
        }}
      />,
    );
    expect(screen.getByText("30 / 150 g")).toBeTruthy();
    expect(screen.getByRole("progressbar", { name: "Protein intake" }).getAttribute("aria-valuenow")).toBe("30");
  });

  it("looks up a typed barcode and prefills the custom-food form", async () => {
    mockApi({
      "GET /api/foods/barcode/3017620422003": {
        barcode: "3017620422003", source: "openfoodfacts",
        food: { name: "Choco Spread", caloriesPer100: 539, proteinPer100: 6.3, carbsPer100: 57.5, fatPer100: 30.9, barcode: "3017620422003" },
      },
    });
    render(<Foods token="tok" meals={[]} onChanged={() => {}} />);
    await userEvent.type(screen.getByLabelText("Barcode"), "3017620422003");
    await userEvent.click(screen.getByRole("button", { name: "Look up" }));
    expect(((await screen.findByLabelText("Food name")) as HTMLInputElement).value).toBe("Choco Spread");
    expect((screen.getByLabelText("Protein g / 100g") as HTMLInputElement).value).toBe("6.3");
  });

  it("creates a custom food with macros", async () => {
    const { calls } = mockApi({
      "POST /api/foods": ({ body }: MockCall) => ({ id: "f1", servingUnit: "g", ...(body as object) }),
    });
    render(<Foods token="tok" meals={[]} onChanged={() => {}} />);
    await userEvent.click(screen.getByRole("button", { name: "+ Custom food" }));
    await userEvent.type(screen.getByLabelText("Food name"), "Bar");
    await userEvent.type(screen.getByLabelText("kcal / 100g"), "380");
    await userEvent.type(screen.getByLabelText("Protein g / 100g"), "30");
    await userEvent.click(screen.getByRole("button", { name: "Save food" }));
    expect(await screen.findByText(/Bar/)).toBeTruthy();
    expect(calls.find((c) => c.method === "POST")?.body).toMatchObject({ name: "Bar", caloriesPer100: 380, proteinPer100: 30, carbsPer100: 0, fatPer100: 0 });
  });
});
