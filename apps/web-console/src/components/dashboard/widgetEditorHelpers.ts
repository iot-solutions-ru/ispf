// Plain helpers kept out of component modules so Fast Refresh can preserve editor state.

/** Seconds before a widget-editor caption tooltip appears. */
export const WIDGET_EDITOR_HINT_DELAY_S = 0.8;

export { joinCaptionHints, splitCaptionDetail } from "../../utils/splitCaptionDetail";

export function recordFieldNames(
  variables:
    | Array<{
        name: string;
        schema?: { fields?: Array<{ name: string }> } | null;
        value?: {
          schema?: { fields?: Array<{ name: string }> };
          rows?: Array<Record<string, unknown>>;
        } | null;
      }>
    | undefined,
  variableName: string,
): string[] {
  const name = variableName.trim();
  if (!name) return [];
  const variable = variables?.find((item) => item.name === name);
  if (!variable) return [];
  const merged = new Set<string>();
  for (const source of [variable.schema?.fields, variable.value?.schema?.fields]) {
    for (const field of source ?? []) {
      const fieldName = field.name.trim();
      if (fieldName) {
        merged.add(fieldName);
      }
    }
  }
  for (const row of variable.value?.rows ?? []) {
    for (const key of Object.keys(row)) {
      const fieldName = key.trim();
      if (fieldName && fieldName !== "quality") {
        merged.add(fieldName);
      }
    }
  }
  return [...merged].sort((a, b) => a.localeCompare(b));
}

/** Free text only when the object is known solely through a selection key. */
export function variableListAllowCustom(widget: {
  objectPath?: string;
  modelHintPath?: string;
  selectionKey?: string;
}): boolean {
  const known = Boolean(widget.objectPath?.trim() || widget.modelHintPath?.trim());
  return !known && Boolean(widget.selectionKey?.trim());
}
