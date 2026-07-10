import { getAuthHeaders } from "../auth/session";

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    cache: "no-store",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
      ...init?.headers,
    },
    ...init,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }

  return response.json();
}

export type AnalyticsCatalogKind = "historian" | "reactive" | "cel";

export interface AnalyticsCatalogParameterDto {
  name: string;
  type: string;
  required: boolean;
  description: string;
  defaultValue: string | null;
}

export interface AnalyticsCatalogEntryDto {
  id: string;
  displayName: string;
  tier: string;
  kinds: string[];
  syntax: string;
  parameters: AnalyticsCatalogParameterDto[];
  description: string;
  examples: string[];
  tags: string[];
  pack: string;
  docAnchor: string;
}

export interface AnalyticsCatalogValidateRequest {
  kind: AnalyticsCatalogKind | string;
  expression: string;
  context?: Record<string, unknown>;
}

export interface AnalyticsCatalogValidateResponse {
  valid: boolean;
  expandedExpression: string | null;
  historianSources: string[];
  errors: string[];
}

export function fetchAnalyticsCatalog(): Promise<AnalyticsCatalogEntryDto[]> {
  return request("/api/v1/platform/analytics/catalog");
}

export function fetchAnalyticsCatalogById(functionId: string): Promise<AnalyticsCatalogEntryDto> {
  return request(`/api/v1/platform/analytics/catalog/${encodeURIComponent(functionId)}`);
}

export function validateAnalyticsCatalogExpression(
  payload: AnalyticsCatalogValidateRequest
): Promise<AnalyticsCatalogValidateResponse> {
  return request("/api/v1/platform/analytics/catalog/validate", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
