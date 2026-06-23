import { describe, expect, it } from "vitest";
import {
  canCreateChildAt,
  defaultObjectTypeForParent,
  instanceTypeFilterForParent,
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
