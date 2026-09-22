import { describe, expect, it } from "vitest";
import { normalizeIconId, resolveTreeIconKind } from "./objectTreeIconCatalog";

describe("resolveTreeIconKind", () => {
  it("uses blueprint icon for blueprint catalog folders", () => {
    expect(resolveTreeIconKind("root.platform.mixin-blueprints", "BLUEPRINT")).toBe("blueprint");
    expect(resolveTreeIconKind("root.platform.singleton-blueprints", "BLUEPRINT")).toBe("blueprint");
    expect(resolveTreeIconKind("root.platform.instance-types", "BLUEPRINT")).toBe("blueprint");
  });

  it("uses database icon for data sources folder and entries", () => {
    expect(resolveTreeIconKind("root.platform.data-sources", "DATA_SOURCES")).toBe("database");
    expect(resolveTreeIconKind("root.platform.data-sources.lab-training", "DATA_SOURCE")).toBe(
      "database",
    );
  });

  it("uses screens icon for operator apps folder and application icon for children", () => {
    expect(resolveTreeIconKind("root.platform.operator-apps", "OPERATOR_APPS")).toBe("screens");
    expect(resolveTreeIconKind("root.platform.operator-apps.lab-training", "APPLICATION")).toBe(
      "application",
    );
  });

  it("uses dedicated icons for queries and MES catalog folders", () => {
    expect(resolveTreeIconKind("root.platform.queries", "QUERIES")).toBe("queries");
    expect(resolveTreeIconKind("root.platform.queries.asset-scan", "CUSTOM")).toBe("queries");
    expect(resolveTreeIconKind("root.platform.analytics.oee-kpi", "ANALYTICS_TEMPLATE")).toBe(
      "analytics",
    );
    expect(resolveTreeIconKind("root.platform.mes", "CUSTOM")).toBe("mes");
    expect(resolveTreeIconKind("root.platform.mes.work-orders", "CUSTOM")).toBe("work-orders");
    expect(resolveTreeIconKind("root.platform.mes.work-orders.wo-1", "CUSTOM")).toBe(
      "work-orders",
    );
    expect(resolveTreeIconKind("root.platform.mes.operations", "CUSTOM")).toBe("gear");
    expect(resolveTreeIconKind("root.platform.mes.lots", "CUSTOM")).toBe("box");
    expect(resolveTreeIconKind("root.platform.mes.shifts", "CUSTOM")).toBe("schedules");
    expect(resolveTreeIconKind("root.platform.mes.quality-records", "CUSTOM")).toBe(
      "quality",
    );
    expect(resolveTreeIconKind("root.platform.mes.instances", "CUSTOM")).toBe("layers");
  });

  it("uses filter icon for event filter catalog", () => {
    expect(resolveTreeIconKind("root.platform.event-filters", "EVENT_FILTERS")).toBe("filter");
    expect(resolveTreeIconKind("root.platform.event-filters.alarm-only", "EVENT_FILTER")).toBe(
      "filter",
    );
  });
});

describe("normalizeIconId", () => {
  it("maps legacy model icon id to blueprint", () => {
    expect(normalizeIconId("model")).toBe("blueprint");
    expect(normalizeIconId("blueprint")).toBe("blueprint");
    expect(normalizeIconId("folder")).toBe("folder");
    expect(normalizeIconId("unknown")).toBeNull();
  });
});
