import { describe, expect, it } from "vitest";
import { subDashboardSharesParentContext } from "./subDashboardContext";

describe("subDashboardSharesParentContext", () => {
  it("shares parent session by default", () => {
    expect(subDashboardSharesParentContext(undefined)).toBe(true);
    expect(subDashboardSharesParentContext(true)).toBe(true);
  });

  it("isolates when inheritContext is false", () => {
    expect(subDashboardSharesParentContext(false)).toBe(false);
  });
});
