import { getAuthHeaders } from "../auth/session";

export interface PackageImportResult {
  appId?: string;
  version?: string;
  status?: string;
  dataSourcePath?: string;
  objectTree?: string;
  applied?: string[];
  skipped?: string[];
  errors?: string[];
}

async function parseError(response: Response, fallback: string): Promise<string> {
  const text = await response.text();
  return text || fallback;
}

export function importPackage(packageId: string, manifest: unknown): Promise<PackageImportResult> {
  const params = new URLSearchParams({ packageId });
  return fetch(`/api/v1/platform/packages/import?${params}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify(manifest),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Package import failed: ${response.status}`));
    }
    return response.json();
  });
}
