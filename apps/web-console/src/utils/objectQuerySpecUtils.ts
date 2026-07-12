import { DEFAULT_OBJECT_QUERY_SPEC } from "./objectQueryDefaults";

export interface ObjectQuerySpecValidation {
  valid: boolean;
  error?: string;
}

export function prettyObjectQuerySpec(raw: string): string {
  if (!raw.trim()) {
    return prettyObjectQuerySpec(DEFAULT_OBJECT_QUERY_SPEC);
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

export function minifyObjectQuerySpec(raw: string): string {
  const parsed = JSON.parse(raw);
  return JSON.stringify(parsed);
}

export function validateObjectQuerySpec(raw: string): ObjectQuerySpecValidation {
  const trimmed = raw.trim();
  if (!trimmed) {
    return { valid: false, error: "empty" };
  }
  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(trimmed) as Record<string, unknown>;
  } catch {
    return { valid: false, error: "invalidJson" };
  }
  if (!parsed.from || typeof parsed.from !== "object") {
    return { valid: false, error: "missingFrom" };
  }
  const from = parsed.from as Record<string, unknown>;
  const pattern = from.sourcePathPattern;
  if (typeof pattern !== "string" || !pattern.trim()) {
    return { valid: false, error: "missingPattern" };
  }
  return { valid: true };
}

/** True when value is a PlatformRef to a variable holding the spec. */
export function isObjectQuerySpecVariableRef(value: string): boolean {
  const trimmed = value.trim();
  return trimmed.startsWith("@/");
}

export function specExampleToJson(spec: Record<string, unknown>): string {
  return JSON.stringify(spec, null, 2);
}
