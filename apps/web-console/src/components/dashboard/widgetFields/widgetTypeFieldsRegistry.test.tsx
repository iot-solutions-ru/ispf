import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { renderWithDashboard } from "../../../test/renderWithDashboard";
import type { DashboardWidget, WidgetType } from "../../../types/dashboard";
import { WidgetTypeSpecificFields } from "./widgetEditorFields";
import type { WidgetFieldContext } from "./widgetFieldPrimitives";
import { WIDGET_TYPE_FIELDS } from "./widgetTypeFieldsRegistry";

/**
 * Widget types that had a `case` in the pre-split `renderWidgetTypeFields` switch. A type missing
 * here silently loses its editor fields (the dispatcher renders nothing), so the list is explicit.
 */
const TYPES_WITH_FIELDS: readonly WidgetType[] = [
  "value", "toggle", "indicator", "chart", "sparkline", "function", "function-form", "progress",
  "object-table", "event-feed", "work-queue", "gauge", "linear-gauge", "liquid-gauge", "card-grid",
  "dashboard-link", "report", "pie-chart", "history-table", "variable-editor", "spreadsheet",
  "svg-widget", "sub-dashboard", "panel", "composite-widget", "drawer-panel", "tab-panel", "map",
  "label", "image", "html-snippet", "object-tree", "breadcrumbs", "timer", "context-list",
  "input-form", "carousel", "steps-panel", "gantt-chart", "network-graph", "nav-menu",
  "status-badge", "scada-mimic",
];

function bareWidget(type: WidgetType): DashboardWidget {
  // Every renderer reads its type-specific settings defensively (`?? default`), so a base widget
  // is enough to mount it. The cast is the point of the test: the editor must not blow up on a
  // freshly created widget of any type.
  return { id: "w1", type, title: "Widget", x: 0, y: 0, w: 4, h: 3 } as DashboardWidget;
}

function contextFor(widget: DashboardWidget): WidgetFieldContext {
  return {
    widget,
    objects: [{ path: "root.devices.pump", displayName: "Pump", variableNames: ["flow", "state"] }],
    dashboards: [{ path: "root.platform.dashboards.main", displayName: "Main" }],
    reports: [{ path: "root.platform.reports.shift", displayName: "Shift" }],
    variables: ["flow", "state"],
    allVariableNames: ["flow", "state", "pressure"],
    variableSelectEnabled: true,
    update: () => {},
  };
}

describe("widget type fields registry", () => {
  afterEach(cleanup);

  it("covers exactly the widget types that have type-specific editor fields", () => {
    expect(Object.keys(WIDGET_TYPE_FIELDS).sort()).toEqual([...TYPES_WITH_FIELDS].sort());
  });

  it("shares one renderer between composite-widget and drawer-panel", () => {
    expect(WIDGET_TYPE_FIELDS["composite-widget"]).toBe(WIDGET_TYPE_FIELDS["drawer-panel"]);
  });

  it.each(TYPES_WITH_FIELDS)("mounts the editor fields for a bare %s widget", (type) => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { container } = renderWithDashboard(
      <QueryClientProvider client={queryClient}>
        <WidgetTypeSpecificFields {...contextFor(bareWidget(type))} />
      </QueryClientProvider>,
    );
    expect(container.querySelector(".widget-editor-section, .widget-editor-pairs, label, input, select")).not.toBeNull();
  });
});
