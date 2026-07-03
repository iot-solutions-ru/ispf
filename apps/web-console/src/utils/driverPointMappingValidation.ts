import {
  extractHaystackTagsFromMappingValue,
  suggestHaystackTagsForVariable,
} from "./haystackMappingHints";

export type DriverMappingValidationIssue = {
  level: "error" | "warning" | "hint";
  code: string;
  message: string;
  key?: string;
  suggestedTags?: string[];
};

export type DriverMappingValidationResult = {
  mappings: Record<string, string>;
  issues: DriverMappingValidationIssue[];
};

const DRIVER_SYSTEM_VARIABLES = new Set([
  "driverId",
  "driverPollIntervalMs",
  "driverConfigJson",
  "driverPointMappingsJson",
  "driverStatus",
]);

function extractPointAddress(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "string") {
    return value.trim();
  }
  if (typeof value === "object" && !Array.isArray(value)) {
    const record = value as Record<string, unknown>;
    for (const key of ["nodeId", "point", "address", "pointId"]) {
      const candidate = record[key];
      if (typeof candidate === "string" && candidate.trim()) {
        return candidate.trim();
      }
    }
    if (typeof record.haystack === "object" && record.haystack !== null) {
      return JSON.stringify(record);
    }
  }
  return String(value).trim();
}

export function validateDriverPointMappingsJson(
  raw: string,
  deviceVariableNames: string[] = [],
): DriverMappingValidationResult {
  const issues: DriverMappingValidationIssue[] = [];
  const trimmed = raw.trim();
  if (!trimmed) {
    return { mappings: {}, issues };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    issues.push({
      level: "error",
      code: "invalid_json",
      message: "Invalid JSON",
    });
    return { mappings: {}, issues };
  }

  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    issues.push({
      level: "error",
      code: "invalid_shape",
      message: "Mappings must be a JSON object",
    });
    return { mappings: {}, issues };
  }

  const knownVariables = new Set(
    deviceVariableNames.filter((name) => !name.startsWith("@") && !DRIVER_SYSTEM_VARIABLES.has(name)),
  );
  const mappings: Record<string, string> = {};
  const addressToKeys = new Map<string, string[]>();

  for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
    if (!key.trim()) {
      issues.push({
        level: "error",
        code: "empty_key",
        message: "Mapping key cannot be empty",
      });
      continue;
    }
    if (key.startsWith("@")) {
      issues.push({
        level: "error",
        code: "reserved_key",
        message: `Reserved variable name: ${key}`,
        key,
      });
      continue;
    }
    const address = extractPointAddress(value);
    if (!address) {
      issues.push({
        level: "error",
        code: "empty_address",
        message: "Point address is required",
        key,
      });
      continue;
    }
    mappings[key] = address;
    const keysForAddress = addressToKeys.get(address) ?? [];
    keysForAddress.push(key);
    addressToKeys.set(address, keysForAddress);

    if (knownVariables.size > 0 && !knownVariables.has(key)) {
      issues.push({
        level: "warning",
        code: "unknown_variable",
        message: `Variable "${key}" is not defined on this device`,
        key,
      });
    }

    if (typeof value === "object" && value !== null && !Array.isArray(value)) {
      const record = value as Record<string, unknown>;
      if (record.haystack !== undefined && (typeof record.haystack !== "object" || record.haystack === null)) {
        issues.push({
          level: "warning",
          code: "invalid_haystack",
          message: "haystack tag must be an object",
          key,
        });
      }
      const existingTags = extractHaystackTagsFromMappingValue(value);
      const suggested = suggestHaystackTagsForVariable(key);
      if (existingTags.length === 0) {
        issues.push({
          level: "hint",
          code: "haystack_tags_suggested",
          message: `Suggested Haystack tags for "${key}": ${suggested.join(", ")}`,
          key,
          suggestedTags: suggested,
        });
      } else {
        const missing = suggested.filter((tag) => !existingTags.includes(tag));
        if (missing.length > 0) {
          issues.push({
            level: "hint",
            code: "haystack_tags_missing",
            message: `Consider adding Haystack tags for "${key}": ${missing.join(", ")}`,
            key,
            suggestedTags: missing,
          });
        }
      }
    } else if (address) {
      issues.push({
        level: "hint",
        code: "haystack_object_suggested",
        message: `Use extended mapping with haystackTags for "${key}" (semantic export)`,
        key,
        suggestedTags: suggestHaystackTagsForVariable(key),
      });
    }
  }

  for (const [address, keys] of addressToKeys.entries()) {
    if (keys.length > 1) {
      issues.push({
        level: "warning",
        code: "duplicate_address",
        message: `Duplicate point address "${address}" for: ${keys.join(", ")}`,
      });
    }
  }

  return { mappings, issues };
}

export function hasDriverMappingErrors(result: DriverMappingValidationResult): boolean {
  return result.issues.some((issue) => issue.level === "error");
}
