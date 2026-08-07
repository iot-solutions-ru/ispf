import { describe, expect, it } from "vitest";
import { isSameOriginAppsUrl } from "./openHostedAppUi";

describe("isSameOriginAppsUrl", () => {
  const origin = "https://ispf.example";

  it("accepts same-origin /apps paths", () => {
    expect(isSameOriginAppsUrl("/apps/oil-control/", origin)).toBe(true);
    expect(isSameOriginAppsUrl("https://ispf.example/apps/demo/", origin)).toBe(true);
  });

  it("rejects external and non-apps urls", () => {
    expect(isSameOriginAppsUrl("https://bridge.example/apps/x/", origin)).toBe(false);
    expect(isSameOriginAppsUrl("/api/v1/info", origin)).toBe(false);
    expect(isSameOriginAppsUrl("https://ispf.example/?mode=operator", origin)).toBe(false);
  });
});
