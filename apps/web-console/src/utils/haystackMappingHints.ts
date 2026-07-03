/** Common Haystack marker tags for driver point mapping hints (BL-84). */

export const COMMON_HAYSTACK_MARKER_TAGS = [
  "point",
  "sensor",
  "temp",
  "power",
  "energy",
  "elec",
  "water",
  "air",
  "co2",
  "humidity",
  "flow",
  "pressure",
  "sp",
  "cmd",
  "equip",
  "site",
  "meter",
  "kw",
  "kwh",
] as const;

const TAGS_BY_VARIABLE_PATTERN: Array<{ pattern: RegExp; tags: string[] }> = [
  { pattern: /temp(erature)?/i, tags: ["point", "sensor", "temp"] },
  { pattern: /humid/i, tags: ["point", "sensor", "humidity"] },
  { pattern: /co2|carbon/i, tags: ["point", "sensor", "co2", "air"] },
  { pattern: /power|kw|energy|kwh/i, tags: ["point", "sensor", "power", "elec"] },
  { pattern: /flow/i, tags: ["point", "sensor", "flow", "water"] },
  { pattern: /pressure|press/i, tags: ["point", "sensor", "pressure"] },
  { pattern: /status|state|alarm/i, tags: ["point", "sensor"] },
];

export function suggestHaystackTagsForVariable(variableName: string): string[] {
  for (const entry of TAGS_BY_VARIABLE_PATTERN) {
    if (entry.pattern.test(variableName)) {
      return [...entry.tags];
    }
  }
  return ["point", "sensor"];
}

export function extractHaystackTagsFromMappingValue(value: unknown): string[] {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return [];
  }
  const record = value as Record<string, unknown>;
  const raw = record.haystackTags ?? record.tags;
  if (Array.isArray(raw)) {
    return raw.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
  }
  if (typeof record.haystack === "object" && record.haystack !== null && !Array.isArray(record.haystack)) {
    const haystack = record.haystack as Record<string, unknown>;
    const nested = haystack.tags ?? haystack.haystackTags;
    if (Array.isArray(nested)) {
      return nested.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
    }
  }
  return [];
}

export function buildHaystackMappingTemplate(
  variableName: string,
  address: string,
  tags: string[] = suggestHaystackTagsForVariable(variableName),
): Record<string, unknown> {
  return {
    point: address,
    haystackTags: tags,
  };
}
