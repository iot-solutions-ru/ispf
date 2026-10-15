import { describe, expect, it } from "vitest";
import {
  widgetDataBinding,
  widgetUsesContextPathKey,
  widgetUsesGenericVariableName,
  widgetUsesModelHintPath,
} from "./widgetEditorBinding";

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

describe("widgetUsesModelHintPath", () => {
  it("is enabled for object-variable widgets and variable-editor", () => {
    expect(widgetUsesModelHintPath("value")).toBe(true);
    expect(widgetUsesModelHintPath("chart")).toBe(true);
    expect(widgetUsesModelHintPath("variable-editor")).toBe(true);
  });

  it("is disabled for function-like object-only widgets", () => {
    expect(widgetUsesModelHintPath("function")).toBe(false);
    expect(widgetUsesModelHintPath("function-form")).toBe(false);
    expect(widgetUsesModelHintPath("input-form")).toBe(false);
  });
});

describe("widgetUsesContextPathKey", () => {
  it("is only for toggle widgets", () => {
    expect(widgetUsesContextPathKey("toggle")).toBe(true);
    expect(widgetUsesContextPathKey("value")).toBe(false);
    expect(widgetUsesContextPathKey("function")).toBe(false);
  });
});

describe("widgetUsesGenericVariableName", () => {
  it("excludes network-graph and spreadsheet", () => {
    expect(widgetUsesGenericVariableName({ type: "value" }, false)).toBe(true);
    expect(widgetUsesGenericVariableName({ type: "network-graph" }, false)).toBe(false);
    expect(widgetUsesGenericVariableName({ type: "spreadsheet" }, false)).toBe(false);
    expect(widgetUsesGenericVariableName({ type: "chart", chartType: "bubble" }, false)).toBe(false);
  });
});
