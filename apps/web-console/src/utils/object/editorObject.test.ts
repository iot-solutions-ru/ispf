import { describe, expect, it } from "vitest";
import { isSpecializedEditorObject, resolveEditorObjectType } from "./editorObject";

describe("editorObject APPLICATION", () => {
  it("treats application root nodes as specialized editors", () => {
    const path = "root.platform.applications.warehouse";
    expect(isSpecializedEditorObject(path, "APPLICATION")).toBe(true);
    expect(resolveEditorObjectType(path, "APPLICATION")).toBe("APPLICATION");
  });

  it("does not treat application child folders as specialized editors", () => {
    const path = "root.platform.applications.warehouse.reports";
    expect(isSpecializedEditorObject(path, "REPORTS")).toBe(false);
    expect(resolveEditorObjectType(path, "REPORTS")).toBe("REPORTS");
  });
});

describe("editorObject VISUAL_GROUP", () => {
  it("does not open specialized editors for catalog bundle groups", () => {
    const path = "root.platform.data-sources.bundle-building-hvac";
    expect(isSpecializedEditorObject(path, "VISUAL_GROUP")).toBe(false);
    expect(resolveEditorObjectType(path, "VISUAL_GROUP")).toBe("VISUAL_GROUP");
  });

  it("does not open dashboard/report editors for groups under those catalogs", () => {
    expect(isSpecializedEditorObject("root.platform.dashboards.bundle-mes", "VISUAL_GROUP")).toBe(false);
    expect(isSpecializedEditorObject("root.platform.reports.bundle-lab-training", "VISUAL_GROUP")).toBe(false);
    expect(isSpecializedEditorObject("root.platform.workflows.bundle-oil-control", "VISUAL_GROUP")).toBe(false);
  });

  it("still opens DATA_SOURCE editors for real data-source children", () => {
    const path = "root.platform.data-sources.building-hvac";
    expect(isSpecializedEditorObject(path, "DATA_SOURCE")).toBe(true);
    expect(resolveEditorObjectType(path, "DATA_SOURCE")).toBe("DATA_SOURCE");
  });

  it("keeps path heuristics only when type is unknown", () => {
    expect(isSpecializedEditorObject("root.platform.dashboards.comfort", undefined)).toBe(true);
    expect(isSpecializedEditorObject("root.platform.dashboards.comfort", "CUSTOM")).toBe(false);
  });
});

describe("editorObject application SQL leaves", () => {
  it("resolves BINDING type for app bindings path when type is unknown", () => {
    const path = "root.platform.applications.demo.bindings.row-count";
    expect(resolveEditorObjectType(path, undefined)).toBe("BINDING");
    expect(isSpecializedEditorObject(path, "BINDING")).toBe(true);
  });
});
