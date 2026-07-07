import { getAuthHeaders } from "../auth/session";

export interface BrickClassSuggestionDto {
  brickClass: string;
  compactClass: string;
  confidence: string;
  reason: string;
}

/** Server infer payload (BL-104). */
export interface BrickInferResult {
  objectPath?: string;
  displayName?: string;
  haystackKind?: string;
  tags?: string[];
  pointMappingTags?: string[];
  brickClass: string;
  brickClassCompact: string;
  confidence: string;
  reason: string;
  entityKind: string;
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function fetchBrickInfer(objectPath: string): Promise<BrickInferResult> {
  const query = new URLSearchParams();
  query.set("objectPath", objectPath.trim());
  return fetch(`/api/v1/platform/brick/infer?${query.toString()}`, {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<BrickInferResult>(response));
}

export function toSuggestionDto(result: BrickInferResult): BrickClassSuggestionDto {
  return {
    brickClass: result.brickClass,
    compactClass: result.brickClassCompact,
    confidence: result.confidence,
    reason: result.reason,
  };
}
