import { describe, expect, it } from "vitest";
import {
  isLicenseRelatedError,
  licenseErrorHintKey,
  parseManifestLicense,
} from "./bundleLicenseUi";

describe("bundleLicenseUi", () => {
  it("parses license block from manifest", () => {
    expect(
      parseManifestLicense({
        version: "1.0.0",
        license: {
          bundleId: "mini-tec",
          installationId: "abc123",
          expiresAt: "2027-01-01T00:00:00Z",
        },
      }),
    ).toEqual({
      present: true,
      bundleId: "mini-tec",
      installationId: "abc123",
      expiresAt: "2027-01-01T00:00:00Z",
    });
  });

  it("detects license-related deploy errors", () => {
    expect(isLicenseRelatedError("License installationId mismatch")).toBe(true);
    expect(isLicenseRelatedError("Not found")).toBe(false);
  });

  it("maps error text to hint keys", () => {
    expect(licenseErrorHintKey("contentSha256 mismatch")).toBe("contentSha256");
    expect(licenseErrorHintKey("License expired at 2020")).toBe("expired");
  });
});
