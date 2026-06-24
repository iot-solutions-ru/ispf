import { describe, expect, it } from "vitest";
import {
  canCreateChildAt,
  canCreateVisualGroupAt,
  defaultObjectTypeForParent,
  instanceTypeFilterForParent,
  resolveVisualGroupParentPath,
} from "./createObjectMode";

describe("canCreateChildAt", () => {
  it("allows create in new model catalog folders", () => {
    expect(canCreateChildAt("root.platform.instance-types", "MODEL")).toBe(true);
    expect(canCreateChildAt("root.platform.relative-models", "MODEL")).toBe(true);
    expect(canCreateChildAt("root.platform.absolute-models", "MODEL")).toBe(true);
    expect(canCreateChildAt("root.platform.instances", "CUSTOM")).toBe(true);
  });

  it("allows create under CUSTOM containers", () => {
    expect(canCreateChildAt("root.platform.my-folder", "CUSTOM")).toBe(true);
    expect(canCreateChildAt("root.platform.site.building", "CUSTOM")).toBe(true);
  });

  it("blocks create on model definition leaves", () => {
    expect(canCreateChildAt("root.platform.instance-types.sensor-v1", "MODEL")).toBe(false);
  });
});

describe("resolveCreateLabelKind", () => {
  it("maps parent folders to create label kinds", async () => {
    const { resolveCreateLabelKind } = await import("./createObjectMode");
    expect(resolveCreateLabelKind("root.platform.devices")).toBe("device");
    expect(resolveCreateLabelKind("root.platform.dashboards")).toBe("dashboard");
    expect(resolveCreateLabelKind("root.platform.workflows")).toBe("workflow");
    expect(resolveCreateLabelKind("root.platform.alert-rules")).toBe("alert-rule");
    expect(resolveCreateLabelKind("root.platform.instance-types")).toBe("model");
    expect(resolveCreateLabelKind("root.platform.instances")).toBe("instance");
    expect(resolveCreateLabelKind("root.platform.my-folder")).toBe("object");
  });
});

describe("canCreateVisualGroupAt", () => {
  it("allows group create from catalog folders and their children", () => {
    expect(canCreateVisualGroupAt("root.platform.reports", "REPORTS")).toBe(true);
    expect(canCreateVisualGroupAt("root.platform.reports.lab-table", "REPORT")).toBe(true);
    expect(resolveVisualGroupParentPath("root.platform.reports.lab-table", "REPORT")).toBe(
      "root.platform.reports",
    );
  });

  it("blocks group create on visual group nodes", () => {
    expect(canCreateVisualGroupAt("root.platform.reports.bundle-lab", "VISUAL_GROUP")).toBe(false);
  });
});

describe("filterVisualGroupsInCatalog", () => {
  it("keeps only visual groups in the given catalog folder", async () => {
    const { filterVisualGroupsInCatalog } = await import("./createObjectMode");
    const objects = [
      { path: "root.platform.reports.g1", type: "VISUAL_GROUP" as const },
      { path: "root.platform.devices.g2", type: "VISUAL_GROUP" as const },
      { path: "root.platform.reports.r1", type: "REPORT" as const },
    ];
    expect(filterVisualGroupsInCatalog(objects, "root.platform.reports")).toEqual([
      { path: "root.platform.reports.g1", type: "VISUAL_GROUP" },
    ]);
  });
});

describe("defaultObjectTypeForParent", () => {
  it("maps parent folders to sensible default types", () => {
    expect(defaultObjectTypeForParent("root.platform.devices")).toBe("DEVICE");
    expect(defaultObjectTypeForParent("root.platform.instance-types")).toBe("MODEL");
    expect(defaultObjectTypeForParent("root.platform.my-folder")).toBe("CUSTOM");
  });
});

describe("instanceTypeFilterForParent", () => {
  it("filters instance types by parent folder, not selected type", () => {
    expect(instanceTypeFilterForParent("root.platform.devices")).toBe("DEVICE");
    expect(instanceTypeFilterForParent("root.platform.my-folder")).toBeUndefined();
  });
});
