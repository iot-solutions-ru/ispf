// Widget editor fields — public surface. Building blocks live in widgetFieldPrimitives.tsx, the shared
// data-source section in widgetDataSourceFields.tsx, per-type fields in widgetTypeFields*.tsx
// (each exports a registry keyed by widget type; widgetTypeFieldsRegistry.ts merges them).

export type { WidgetFieldContext } from "./widgetFieldPrimitives";
export { FieldPairs } from "./widgetFieldPrimitives";
export { WidgetDataSourceFields } from "./widgetDataSourceFields";

import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { DashboardWidget } from "../../../types/dashboard";
import {
  FieldPairs,
  type WidgetFieldContext,
  type WidgetFieldContextFor,
  type WidgetTypeFieldsRenderer,
} from "./widgetFieldPrimitives";
import { WIDGET_TYPE_FIELDS } from "./widgetTypeFieldsRegistry";

function renderWidgetTypeFields(ctx: WidgetFieldContext, t: TFunction): ReactNode {
  // The registry is keyed by widget.type, so the renderer found for ctx.widget.type is the one whose
  // narrowed context matches ctx — the cast only re-states what the mapped type already guarantees.
  const render = WIDGET_TYPE_FIELDS[ctx.widget.type] as WidgetTypeFieldsRenderer<DashboardWidget["type"]> | undefined;
  return render ? render(ctx as WidgetFieldContextFor<DashboardWidget["type"]>, t) : null;
}

export function WidgetTypeSpecificFields(ctx: WidgetFieldContext) {
  const { t } = useTranslation(["widgets", "common"]);
  return <FieldPairs>{renderWidgetTypeFields(ctx, t)}</FieldPairs>;
}
