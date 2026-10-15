import type { VariableDto } from "../../types";

/** Match batch API keys to the object paths used in list/tree responses. */
export function resolveVariablesBatchEntry(
  batch: Record<string, VariableDto[]>,
  objectPath: string,
): VariableDto[] | undefined {
  const path = objectPath.trim();
  if (!path) {
    return undefined;
  }
  if (batch[path]) {
    return batch[path];
  }
  const keys = Object.keys(batch);
  if (keys.length === 0) {
    return undefined;
  }
  const leaf = path.split(".").pop() ?? path;
  const leafSuffix = `.${leaf}`;
  const candidates = keys.filter((key) => key === path || key.endsWith(leafSuffix));
  if (candidates.length === 1) {
    return batch[candidates[0]];
  }
  if (candidates.length > 1) {
    const exact = candidates.find((key) => key === path);
    if (exact) {
      return batch[exact];
    }
    const nested = candidates.find((key) => path.endsWith(key) || key.endsWith(path));
    if (nested) {
      return batch[nested];
    }
  }
  return undefined;
}

/** Re-key batch results so callers can index by the paths they requested. */
export function normalizeVariablesBatch(
  batch: Record<string, VariableDto[]>,
  requestedPaths: string[],
): Record<string, VariableDto[]> {
  const out: Record<string, VariableDto[]> = { ...batch };
  for (const path of requestedPaths) {
    const variables = resolveVariablesBatchEntry(batch, path);
    if (variables) {
      out[path] = variables;
    }
  }
  return out;
}
