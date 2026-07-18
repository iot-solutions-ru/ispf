import { describe, expect, it } from "vitest";
import { readAdminPathFromUrl, resolveInitialAdminPath } from "./adminRouting";

describe("adminRouting", () => {
  it("reads path from query string", () => {
    expect(readAdminPathFromUrl("?path=root.platform.devices.demo")).toBe(
      "root.platform.devices.demo"
    );
    expect(readAdminPathFromUrl("?mode=admin")).toBeNull();
  });

  it("prefers URL path over session default", () => {
    expect(resolveInitialAdminPath("?path=root.apps", "root.platform")).toBe("root.apps");
    expect(resolveInitialAdminPath("", "root.platform")).toBe("root.platform");
  });
});
