import { describe, expect, it } from "vitest";
import { isSafeOperatorLaunchUrl, isSameOriginAppsUrl } from "./openHostedAppUi";

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

describe("isSafeOperatorLaunchUrl", () => {
  const origin = "https://ispf.example";

  it("allows /apps and http(s)", () => {
    expect(isSafeOperatorLaunchUrl("/apps/oil-control/", origin)).toBe(true);
    expect(isSafeOperatorLaunchUrl("https://bridge.example/", origin)).toBe(true);
    expect(isSafeOperatorLaunchUrl("http://127.0.0.1:5173/", origin)).toBe(true);
  });

  it("rejects dangerous schemes and non-apps paths", () => {
    expect(isSafeOperatorLaunchUrl("javascript:alert(1)", origin)).toBe(false);
    expect(isSafeOperatorLaunchUrl("data:text/html,hi", origin)).toBe(false);
    expect(isSafeOperatorLaunchUrl("vbscript:msgbox(1)", origin)).toBe(false);
    expect(isSafeOperatorLaunchUrl("/api/v1/info", origin)).toBe(false);
    expect(isSafeOperatorLaunchUrl("/?mode=operator", origin)).toBe(false);
  });
});
