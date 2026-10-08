// Plain helpers kept out of component modules so Fast Refresh can preserve editor state.

/** Seconds before a widget-editor caption tooltip appears. */
export const WIDGET_EDITOR_HINT_DELAY_S = 0.8;

/** Pulls a trailing "(description)" out of a block title. */
export function splitCaptionDetail(text: string): { title: string; detail?: string } {
  const match = text.trim().match(/^(.*?)\s*\(([^()]*)\)\s*$/);
  if (!match) return { title: text };
  const title = match[1].trim();
  const detail = match[2].trim();
  if (!title || !detail) return { title: text };
  return { title, detail };
}

export function recordFieldNames(
  variables: Array<{ name: string; value?: { schema?: { fields?: Array<{ name: string }> } } | null }> | undefined,
  variableName: string,
): string[] {
  const name = variableName.trim();
  if (!name) return [];
  const variable = variables?.find((item) => item.name === name);
  const fields = variable?.value?.schema?.fields ?? [];
  return [...new Set(fields.map((field) => field.name.trim()).filter(Boolean))].sort((a, b) =>
    a.localeCompare(b),
  );
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
