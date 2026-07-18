import { describe, expect, it } from "vitest";
import {
  canCreateChildAt,
  defaultObjectTypeForParent,
  instanceTypeFilterForParent,
  resolveCreateDialogMode,
} from "./createObjectMode";

describe("canCreateChildAt — platform catalogs", () => {
  it("allows create in Phase 30 catalogs", () => {
    expect(canCreateChildAt("root.platform.queries", "QUERIES")).toBe(true);
    expect(canCreateChildAt("root.platform.event-filters", "EVENT_FILTERS")).toBe(true);
    expect(canCreateChildAt("root.platform.process-programs", "PROCESS_PROGRAMS")).toBe(true);
  });

  it("allows create in MES catalog folders", () => {
    expect(canCreateChildAt("root.platform.mes.work-orders", "WORK_ORDERS")).toBe(true);
    expect(canCreateChildAt("root.platform.mes.lots", "LOTS")).toBe(true);
    expect(canCreateChildAt("root.platform.mes.quality-records", "QUALITY_RECORDS")).toBe(true);
    expect(canCreateChildAt("root.platform.mes.instances", "MES_INSTANCES")).toBe(true);
  });

  it("blocks create on instance leaves", () => {
    expect(canCreateChildAt("root.platform.queries.device-scan", "CUSTOM")).toBe(false);
    expect(canCreateChildAt("root.platform.mes.work-orders.wo-1", "WORK_ORDER")).toBe(false);
  });
});

describe("resolveCreateDialogMode", () => {
  it("maps Phase 30 catalogs to specialized dialogs", () => {
    expect(resolveCreateDialogMode("root.platform.queries")).toBe("query");
    expect(resolveCreateDialogMode("root.platform.event-filters")).toBe("event-filter");
    expect(resolveCreateDialogMode("root.platform.process-programs")).toBe("process-program");
  });
});

describe("defaultObjectTypeForParent", () => {
  it("maps catalog folders to child types", () => {
    expect(defaultObjectTypeForParent("root.platform.queries")).toBe("CUSTOM");
    expect(defaultObjectTypeForParent("root.platform.event-filters")).toBe("EVENT_FILTER");
    expect(defaultObjectTypeForParent("root.platform.process-programs")).toBe("PROCESS_PROGRAM");
    expect(defaultObjectTypeForParent("root.platform.mes.work-orders")).toBe("WORK_ORDER");
    expect(defaultObjectTypeForParent("root.platform.mes.lots")).toBe("LOT");
  });
});

describe("instanceTypeFilterForParent", () => {
  it("filters instance blueprints for MES parents", () => {
    expect(instanceTypeFilterForParent("root.platform.mes.work-orders")).toBe("WORK_ORDER");
    expect(instanceTypeFilterForParent("root.platform.mes.lots")).toBe("LOT");
    expect(instanceTypeFilterForParent("root.platform.queries")).toBeUndefined();
  });
});
