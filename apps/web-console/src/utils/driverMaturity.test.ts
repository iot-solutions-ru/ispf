import { describe, expect, it } from "vitest";
import {
  driverMaturityClass,
  driverMaturityLabel,
  normalizeDriverMaturity,
} from "./driverMaturity";

describe("driverMaturity", () => {
  it("defaults unknown values to PRODUCTION", () => {
    expect(normalizeDriverMaturity(undefined)).toBe("PRODUCTION");
    expect(normalizeDriverMaturity("UNKNOWN")).toBe("PRODUCTION");
  });

  it("maps stub and beta labels", () => {
    expect(driverMaturityLabel("STUB")).toBe("Stub");
    expect(driverMaturityClass("BETA")).toBe("driver-maturity-badge beta");
  });
});
