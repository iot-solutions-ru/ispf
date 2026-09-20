/**
 * Widget editor field modules.
 * Per-type fields live in `widgetTypeFields{Display,Chart,Data,Layout}.tsx` as registries keyed by
 * widget type (merged in `widgetTypeFieldsRegistry.ts`); `widgetEditorFields.tsx` is the dispatcher.
 */
export type { WidgetFieldContext } from "./widgetEditorFields";
export {
  FieldPairs,
  WidgetDataSourceFields,
  WidgetTypeSpecificFields,
} from "./widgetEditorFields";
