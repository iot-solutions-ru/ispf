export function isAnalyticsTagDevice(
  object: { type?: string; variableNames?: string[] } | null | undefined,
): boolean {
  if (!object || object.type !== "DEVICE" || !object.variableNames) {
    return false;
  }
  return object.variableNames.includes("derivedValue") || object.variableNames.includes("oeePct");
}

const TAG_SEGMENT = "/tag/";

/** Canonical historian tag path (`objectPath/tag/ruleId`). */
export function encodeHistorianTagPath(objectPath: string, ruleId: string): string {
  return `${objectPath}${TAG_SEGMENT}${ruleId}`;
}

/** Object path from analytics tag path (`device/tag/ruleId`). */
export function analyticsTagObjectPath(tagPath: string): string {
  const idx = tagPath.indexOf(TAG_SEGMENT);
  return idx >= 0 ? tagPath.slice(0, idx) : tagPath;
}

/** True when tag path belongs to the given object (composite or bare device path). */
export function historianTagOwnedByObject(tagPath: string, objectPath: string): boolean {
  return analyticsTagObjectPath(tagPath) === objectPath;
}
