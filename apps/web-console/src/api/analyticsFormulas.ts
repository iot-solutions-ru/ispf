import { getAuthHeaders } from "../auth/session";
import type { AnalyticsCatalogParameterDto } from "./analyticsCatalog";

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

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export interface AnalyticsFormulaDto {
  id: string;
  displayName: string;
  kind: "historian" | "reactive" | string;
  expression: string;
  parameters: AnalyticsCatalogParameterDto[];
  createdBy?: string | null;
  version: number;
  scope: "site" | "app" | string;
  appId?: string | null;
}

export interface AnalyticsFormulaExpandRequest {
  parameters: Record<string, string>;
  scope?: string;
  appId?: string | null;
}

export interface AnalyticsFormulaExpandResponse {
  expression: string;
}

export function fetchAnalyticsFormulas(scope = "site", appId?: string): Promise<AnalyticsFormulaDto[]> {
  const params = new URLSearchParams({ scope });
  if (appId) {
    params.set("appId", appId);
  }
  return request(`/api/v1/platform/analytics/formulas?${params.toString()}`);
}

export function createAnalyticsFormula(formula: AnalyticsFormulaDto): Promise<AnalyticsFormulaDto> {
  return request("/api/v1/platform/analytics/formulas", {
    method: "POST",
    body: JSON.stringify(formula),
  });
}

export function updateAnalyticsFormula(
  formulaId: string,
  formula: AnalyticsFormulaDto,
  scope = "site",
  appId?: string
): Promise<AnalyticsFormulaDto> {
  const params = new URLSearchParams({ scope });
  if (appId) {
    params.set("appId", appId);
  }
  return request(`/api/v1/platform/analytics/formulas/${encodeURIComponent(formulaId)}?${params.toString()}`, {
    method: "PUT",
    body: JSON.stringify(formula),
  });
}

export function deleteAnalyticsFormula(formulaId: string, scope = "site", appId?: string): Promise<void> {
  const params = new URLSearchParams({ scope });
  if (appId) {
    params.set("appId", appId);
  }
  return request(`/api/v1/platform/analytics/formulas/${encodeURIComponent(formulaId)}?${params.toString()}`, {
    method: "DELETE",
  });
}

export function expandAnalyticsFormula(
  formulaId: string,
  payload: AnalyticsFormulaExpandRequest
): Promise<AnalyticsFormulaExpandResponse> {
  return request(`/api/v1/platform/analytics/formulas/${encodeURIComponent(formulaId)}/expand`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function detectFormulaParameters(expression: string): string[] {
  const names = new Set<string>();
  const pattern = /\{\{([a-zA-Z][a-zA-Z0-9_]*)\}\}/g;
  let match = pattern.exec(expression);
  while (match) {
    names.add(match[1]);
    match = pattern.exec(expression);
  }
  return [...names];
}
