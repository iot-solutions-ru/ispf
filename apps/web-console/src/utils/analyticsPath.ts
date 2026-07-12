export function isAnalyticsTagDevice(
  object: { type?: string; variableNames?: string[] } | null | undefined,
): boolean {
  if (!object || object.type !== "DEVICE" || !object.variableNames) {
    return false;
  }
  return object.variableNames.includes("derivedValue") || object.variableNames.includes("oeePct");
}

const TAG_SEGMENT = "/tag/";

/** Object path from analytics tag path (`device/tag/ruleId`). */
export function analyticsTagObjectPath(tagPath: string): string {
  const idx = tagPath.indexOf(TAG_SEGMENT);
  return idx >= 0 ? tagPath.slice(0, idx) : tagPath;
}
