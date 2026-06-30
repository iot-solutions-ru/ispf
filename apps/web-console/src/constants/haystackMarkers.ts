/** Common Project Haystack marker tags for the inspector multiselect (BL-57). */
export const COMMON_HAYSTACK_MARKERS = [
  "equip",
  "point",
  "sensor",
  "temp",
  "his",
  "site",
  "lab",
  "dis",
  "unit",
  "cur",
  "sp",
  "cmd",
] as const;

export type CommonHaystackMarker = (typeof COMMON_HAYSTACK_MARKERS)[number];

export function parseHaystackTagsJson(raw: string): string[] {
  if (!raw.trim()) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter((item): item is string => typeof item === "string")
      .map((tag) => tag.trim())
      .filter(Boolean);
  } catch {
    return [];
  }
}

export function serializeHaystackTags(tags: Iterable<string>): string {
  const unique = [...new Set([...tags].map((tag) => tag.trim()).filter(Boolean))].sort();
  return JSON.stringify(unique);
}
