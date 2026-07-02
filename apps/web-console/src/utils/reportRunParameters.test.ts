import { describe, expect, it } from "vitest";
import { enrichReportRunParameters } from "./reportRunParameters";

describe("enrichReportRunParameters", () => {
  it("adds reportTimeZone when calendarRange is set", () => {
    expect(
      enrichReportRunParameters({ calendarRange: "today" }, "Europe/Moscow")
    ).toEqual({ calendarRange: "today", reportTimeZone: "Europe/Moscow" });
  });

  it("does not override explicit reportTimeZone", () => {
    expect(
      enrichReportRunParameters(
        { calendarRange: "today", reportTimeZone: "UTC" },
        "Europe/Moscow"
      )
    ).toEqual({ calendarRange: "today", reportTimeZone: "UTC" });
  });

  it("leaves params unchanged without calendarRange", () => {
    const input = { itemCode: "A1" };
    expect(enrichReportRunParameters(input, "Europe/Moscow")).toBe(input);
  });
});
