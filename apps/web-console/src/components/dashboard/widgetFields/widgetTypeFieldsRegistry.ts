// Merged registry of per-type editor field renderers (one entry per widget type that has
// type-specific fields). Category files own the renderers; this module only merges them.
import type { WidgetTypeFieldsRegistry } from "./widgetFieldPrimitives";
import { CHART_WIDGET_FIELDS } from "./widgetTypeFieldsChart";
import { DATA_WIDGET_FIELDS } from "./widgetTypeFieldsData";
import { DISPLAY_WIDGET_FIELDS } from "./widgetTypeFieldsDisplay";
import { LAYOUT_WIDGET_FIELDS } from "./widgetTypeFieldsLayout";

export const WIDGET_TYPE_FIELDS: WidgetTypeFieldsRegistry = {
  ...DISPLAY_WIDGET_FIELDS,
  ...CHART_WIDGET_FIELDS,
  ...DATA_WIDGET_FIELDS,
  ...LAYOUT_WIDGET_FIELDS,
};
