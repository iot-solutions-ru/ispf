import { describe, expect, it } from "vitest";
import {
  operatorAppIdCandidates,
  preferCanonicalOperatorAppId,
  resolveOperatorAppId,
  resolveOperatorAppIdFromPath,
} from "./operatorAppsPath";

describe("operatorAppsPath", () => {
  const apps = [
    { appId: "it-infra-monitoring" },
    { appId: "itm-plugin-topology-m11" },
  ];

  it("maps bundle visual group leaf to registry app id", () => {
    expect(resolveOperatorAppId("bundle-it-infra-monitoring", apps)).toBe("it-infra-monitoring");
  });

  it("strips bundle prefix even when registry is empty", () => {
    expect(resolveOperatorAppId("bundle-it-infra-monitoring", [])).toBe("it-infra-monitoring");
    expect(preferCanonicalOperatorAppId("bundle-it-infra-monitoring")).toBe("it-infra-monitoring");
  });

  it("resolves from operator-apps tree path with bundle prefix", () => {
    expect(
      resolveOperatorAppIdFromPath("root.platform.operator-apps.bundle-it-infra-monitoring", apps),
    ).toBe("it-infra-monitoring");
  });

  it("keeps unknown leaf unchanged", () => {
    expect(resolveOperatorAppId("unknown-app", apps)).toBe("unknown-app");
  });

  it("lists bundle alias candidates", () => {
    expect(operatorAppIdCandidates("bundle-it-infra-monitoring")).toEqual([
      "bundle-it-infra-monitoring",
      "it-infra-monitoring",
    ]);
  });
});
