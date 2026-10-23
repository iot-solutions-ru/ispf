import type { DriverMetadata } from "../types/drivers";

export function defaultDriverConfiguration(
  driver: DriverMetadata | undefined
): Record<string, string> {
  if (!driver?.configurationSchema) {
    return {};
  }
  return { ...driver.configurationSchema };
}

export function formatDriverConfigJson(driver: DriverMetadata | undefined): string {
  return JSON.stringify(defaultDriverConfiguration(driver), null, 2);
}

/** Configuration edited in the create-device dialog. Nested values are stored as JSON text. */
export function parseDriverConfigJson(text: string): Record<string, string> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error("invalid");
  }
  if (parsed == null || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("invalid");
  }
  const configuration: Record<string, string> = {};
  for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
    if (!key.trim()) {
      continue;
    }
    if (value == null) {
      configuration[key] = "";
    } else if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      configuration[key] = String(value);
    } else {
      configuration[key] = JSON.stringify(value);
    }
  }
  return configuration;
}
