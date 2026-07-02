import { getAuthHeaders } from "../auth/session";

export interface SolutionCatalogVersion {
  version: string;
  deployedAt?: string;
  active?: boolean;
}

export interface SolutionCatalogInstalled {
  appId: string;
  displayName?: string;
  schemaName?: string;
  activeVersion?: string;
  deployedAt?: string;
  changelog?: string;
  screenCount?: number;
  bundleDisplayName?: string;
  versions?: SolutionCatalogVersion[];
}

export interface SolutionReferenceExample {
  exampleId: string;
  appId: string;
  title: string;
  description: string;
  installed?: boolean;
  activeVersion?: string;
}

export interface SolutionCatalogResponse {
  installed: SolutionCatalogInstalled[];
  referenceExamples: SolutionReferenceExample[];
}

export function fetchSolutionCatalog(): Promise<SolutionCatalogResponse> {
  return fetch("/api/v1/solutions/catalog", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Request failed: ${response.status}`);
    }
    return response.json();
  });
}

export function installReferenceSolution(exampleId: string): Promise<Record<string, unknown>> {
  return fetch(`/api/v1/solutions/reference/${encodeURIComponent(exampleId)}/install`, {
    method: "POST",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Install failed: ${response.status}`);
    }
    return response.json();
  });
}
