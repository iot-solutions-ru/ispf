/** Pulls a trailing "(description)" out of a field caption for title + tooltip split. */
export function splitCaptionDetail(text: string): { title: string; detail?: string } {
  const match = text.trim().match(/^(.*?)\s*\(([^()]*)\)\s*$/);
  if (!match) return { title: text };
  const title = match[1].trim();
  const detail = match[2].trim();
  if (!title || !detail) return { title: text };
  return { title, detail };
}

export function joinCaptionHints(...parts: Array<string | undefined>): string | undefined {
  const text = parts.map((part) => part?.trim()).filter((part): part is string => Boolean(part));
  return text.length > 0 ? text.join("\n") : undefined;
}
