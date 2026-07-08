import { describe, expect, it } from "vitest";
import {
  buildAnalyticsBindingExpression,
  resolveAnalyticsAggregateBucket,
} from "./analyticsChartBinding";

describe("analyticsChartBinding", () => {
  it("builds rollingAvg expression for chart binding catalog", () => {
    expect(buildAnalyticsBindingExpression("rollingAvg", "temperature", "5m")).toBe(
      "rollingAvg('temperature', '5m')"
    );
  });

  it("uses template window bucket for rollingAvg charts", () => {
    expect(
      resolveAnalyticsAggregateBucket(
        { templateId: "rollingAvg", helper: "rollingAvg", windowBucket: "5m" },
        "1h"
      )
    ).toBe("5m");
  });

  it("falls back when no analytics template is selected", () => {
    expect(resolveAnalyticsAggregateBucket(null, "1h")).toBe("1h");
  });
});
