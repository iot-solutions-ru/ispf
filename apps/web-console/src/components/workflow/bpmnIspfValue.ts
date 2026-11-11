export type BpmnJsonIssue = "json" | "shape" | "pairs";

/** Engine `inputMap`: `key=value` pairs, or a JSON object of scalars. */
export function inputMapIssue(value: string): BpmnJsonIssue | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  if (trimmed.startsWith("{")) {
    return jsonScalarObjectIssue(trimmed);
  }
  for (const part of trimmed.split(",")) {
    const entry = part.trim();
    if (!entry) {
      continue;
    }
    if (entry.indexOf("=") <= 0) {
      return "pairs";
    }
  }
  return null;
}

/** Engine `payloadJson`: a JSON object of strings, numbers, and booleans. */
export function payloadJsonIssue(value: string): BpmnJsonIssue | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  return jsonScalarObjectIssue(trimmed);
}

function jsonScalarObjectIssue(value: string): BpmnJsonIssue | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(value);
  } catch {
    return "json";
  }
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    return "shape";
  }
  for (const entry of Object.values(parsed)) {
    if (entry !== null && typeof entry === "object") {
      return "shape";
    }
  }
  return null;
}
