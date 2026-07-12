import { describe, expect, it } from "vitest";
import {
  buildAnalyticsBindingExpression,
  resolveAnalyticsAggregateBucket,
} from "./analyticsChartBinding";

describe("analyticsChartBinding", () => {
  it("builds avg expression for chart binding catalog", () => {
    expect(buildAnalyticsBindingExpression("avg", "temperature", "5m")).toBe(
      "avg(@/temperature, 5m)"
    );
  });

  it("uses template window bucket for avg charts", () => {
    expect(
      resolveAnalyticsAggregateBucket(
        { templateId: "avg", helper: "avg", windowBucket: "5m" },
        "1h"
      )
    ).toBe("5m");
  });

  it("uses template window bucket for oee charts", () => {
    expect(
      resolveAnalyticsAggregateBucket(
        { templateId: "oee", helper: "oee", windowBucket: "8h" },
        "1h"
      )
    ).toBe("8h");
  });

  it("falls back when no analytics template is selected", () => {
    expect(resolveAnalyticsAggregateBucket(null, "1h")).toBe("1h");
  });
});
