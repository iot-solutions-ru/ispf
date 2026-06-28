import { describe, expect, it } from "vitest";
import { widgetDataBinding } from "./widgetEditorBinding";

describe("widgetDataBinding", () => {
  it("maps chart widgets to object-variable binding", () => {
    expect(widgetDataBinding("chart")).toBe("object-variable");
    expect(widgetDataBinding("sparkline")).toBe("object-variable");
  });

  it("maps catalog widgets to parent-catalog binding", () => {
    expect(widgetDataBinding("object-table")).toBe("parent-catalog");
    expect(widgetDataBinding("map")).toBe("parent-catalog");
  });

  it("maps external navigation widgets separately from session widgets", () => {
    expect(widgetDataBinding("dashboard-link")).toBe("external");
    expect(widgetDataBinding("report")).toBe("external");
    expect(widgetDataBinding("label")).toBe("session");
    expect(widgetDataBinding("breadcrumbs")).toBe("session");
  });

  it("maps composition containers without direct data binding", () => {
    expect(widgetDataBinding("panel")).toBe("composition");
    expect(widgetDataBinding("tab-panel")).toBe("composition");
  });
});
