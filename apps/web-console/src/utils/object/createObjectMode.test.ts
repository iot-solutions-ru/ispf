import { describe, expect, it } from "vitest";
import {
  blueprintKindForCatalog,
  canCreateChildAt,
  defaultObjectTypeForParent,
  instanceTypeFilterForParent,
  instanceTypeTargetsForParent,
  PLATFORM_CREATE_TYPES,
  platformTypesForParent,
  resolveCreateDialogMode,
} from "./createObjectMode";

describe("canCreateChildAt — platform catalogs", () => {
  it("allows create in Phase 30 catalogs", () => {
    expect(canCreateChildAt("root.platform.queries", "QUERIES")).toBe(true);
    expect(canCreateChildAt("root.platform.event-filters", "EVENT_FILTERS")).toBe(true);
    expect(canCreateChildAt("root.platform.process-programs", "PROCESS_PROGRAMS")).toBe(true);
  });

  it("allows create in MES catalog folders", () => {
    expect(canCreateChildAt("root.platform.mes.work-orders", "CUSTOM")).toBe(true);
    expect(canCreateChildAt("root.platform.mes.lots", "CUSTOM")).toBe(true);
    expect(canCreateChildAt("root.platform.mes.quality-records", "CUSTOM")).toBe(true);
    expect(canCreateChildAt("root.platform.mes.instances", "CUSTOM")).toBe(true);
  });

  it("blocks create on instance leaves", () => {
    expect(canCreateChildAt("root.platform.queries.device-scan", "CUSTOM")).toBe(false);
    expect(canCreateChildAt("root.platform.mes.work-orders.wo-1", "CUSTOM")).toBe(false);
  });
});

describe("resolveCreateDialogMode", () => {
  it("maps Phase 30 catalogs to specialized dialogs", () => {
    expect(resolveCreateDialogMode("root.platform.queries")).toBe("query");
    expect(resolveCreateDialogMode("root.platform.event-filters")).toBe("event-filter");
    expect(resolveCreateDialogMode("root.platform.process-programs")).toBe("process-program");
  });

  it("maps blueprint catalogs to the blueprint dialog", () => {
    expect(resolveCreateDialogMode("root.platform.instance-types")).toBe("blueprint");
    expect(resolveCreateDialogMode("root.platform.mixin-blueprints")).toBe("blueprint");
    expect(resolveCreateDialogMode("root.platform.singleton-blueprints")).toBe("blueprint");
    expect(blueprintKindForCatalog("root.platform.instance-types")).toBe("INSTANCE");
    expect(blueprintKindForCatalog("root.platform.mixin-blueprints")).toBe("MIXIN");
    expect(blueprintKindForCatalog("root.platform.singleton-blueprints")).toBe("SINGLETON");
    expect(blueprintKindForCatalog("root.platform.devices")).toBeNull();
  });
});

describe("defaultObjectTypeForParent", () => {
  it("maps catalog folders to child types", () => {
    expect(defaultObjectTypeForParent("root.platform.queries")).toBe("CUSTOM");
    expect(defaultObjectTypeForParent("root.platform.event-filters")).toBe("EVENT_FILTER");
    expect(defaultObjectTypeForParent("root.platform.process-programs")).toBe("PROCESS_PROGRAM");
    expect(defaultObjectTypeForParent("root.platform.mes.work-orders")).toBe("CUSTOM");
    expect(defaultObjectTypeForParent("root.platform.mes.lots")).toBe("CUSTOM");
    expect(defaultObjectTypeForParent("root.platform.devices.tank-farm")).toBe("DEVICE");
    expect(defaultObjectTypeForParent("root.platform.instances.site")).toBe("CUSTOM");
  });
});

describe("platformTypesForParent", () => {
  it("offers the catalog child type without a visual group", () => {
    expect(platformTypesForParent("root.platform.devices")).toEqual(["DEVICE", "CUSTOM"]);
    expect(platformTypesForParent("root.platform.dashboards")).toEqual(["DASHBOARD"]);
    expect(platformTypesForParent("root.platform.workflows")).toEqual(["WORKFLOW"]);
    expect(platformTypesForParent("root.platform.singleton-blueprints")).toEqual(["BLUEPRINT"]);
    expect(platformTypesForParent("root.platform.mes.work-orders")).toEqual(["CUSTOM"]);
    expect(platformTypesForParent("root.platform.instances")).toEqual(["CUSTOM"]);
    expect(platformTypesForParent("root.platform.instances.site-a")).toEqual(["CUSTOM"]);
    expect(platformTypesForParent("root.platform.devices.tank-farm")).toEqual(["DEVICE", "CUSTOM"]);
    expect(platformTypesForParent("root.platform.mes")).toEqual(["CUSTOM"]);
    expect(platformTypesForParent("root.platform.mes.instances")).toEqual(["CUSTOM"]);
    expect(platformTypesForParent("root.platform.dashboards.overview")).toEqual(["DASHBOARD"]);
    expect(platformTypesForParent("root.platform.workflows.job")).toEqual(["WORKFLOW"]);
    expect(platformTypesForParent("root")).not.toContain("VISUAL_GROUP");
    expect(platformTypesForParent("root.platform")).not.toContain("VISUAL_GROUP");
  });

  it("keeps the full list for an unconstrained parent", () => {
    expect(platformTypesForParent("root")).toEqual([...PLATFORM_CREATE_TYPES]);
    expect(platformTypesForParent("root.platform")).toEqual([...PLATFORM_CREATE_TYPES]);
  });
});

describe("instanceTypeFilterForParent", () => {
  it("filters instance blueprints for MES parents", () => {
    expect(instanceTypeFilterForParent("root.platform.mes.work-orders")).toBe("CUSTOM");
    expect(instanceTypeFilterForParent("root.platform.mes.lots")).toBe("CUSTOM");
    expect(instanceTypeFilterForParent("root.platform.mes.instances")).toBe("CUSTOM");
    expect(instanceTypeFilterForParent("root.platform.devices.tank-farm")).toBe("DEVICE");
    expect(instanceTypeFilterForParent("root.platform.queries")).toBeUndefined();
    expect(instanceTypeFilterForParent("root.platform.instances")).toBeUndefined();
    expect(instanceTypeTargetsForParent("root.platform.instances")).toEqual(["CUSTOM", "DEVICE"]);
    expect(instanceTypeTargetsForParent("root.platform.instances.meter")).toEqual(["CUSTOM", "DEVICE"]);
    expect(instanceTypeTargetsForParent("root.platform")).toBeUndefined();
  });
});
