import { describe, expect, it } from "vitest";
import { visibleObjectEditorTabs } from "./tabs";

const base = {
  canManage: true,
  canManageAcl: true,
  hasHaystackMetadata: false,
  hasBrickMetadata: false,
};

describe("visibleObjectEditorTabs", () => {
  it("hides variables, computations, and events on the queries catalog folder", () => {
    const tabs = visibleObjectEditorTabs({
      ...base,
      path: "root.platform.queries",
      objectType: "QUERIES",
    });
    expect(tabs).toEqual(["general", "federation", "export", "access", "history"]);
  });

  it("shows only functions among model tabs on a query definition object", () => {
    const tabs = visibleObjectEditorTabs({
      ...base,
      path: "root.platform.queries.device-scan",
      objectType: "CUSTOM",
    });
    expect(tabs).toEqual(["general", "federation", "export", "access", "functions", "history"]);
    expect(tabs).not.toContain("variables");
    expect(tabs).not.toContain("computations");
    expect(tabs).not.toContain("events");
  });

  it("keeps full tabs on a generic CUSTOM object", () => {
    const tabs = visibleObjectEditorTabs({
      ...base,
      path: "root.platform.devices.myHub",
      objectType: "CUSTOM",
    });
    expect(tabs).toContain("variables");
    expect(tabs).toContain("computations");
    expect(tabs).toContain("events");
    expect(tabs).toContain("functions");
  });
});
