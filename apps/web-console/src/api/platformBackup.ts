import { getAuthHeaders } from "../auth/session";
import { parseApiError } from "../utils/parseApiError";

export interface PlatformBackupImportPreview {
  nodeCount: number;
  createCount: number;
  updateCount: number;
  warnings: string[];
}

export interface PlatformBackupImportResult {
  preview: PlatformBackupImportPreview;
  created: number;
  updated: number;
  skipped: number;
  dryRun: boolean;
}

export async function exportPlatformBackup(): Promise<Record<string, unknown>> {
  const response = await fetch("/api/v1/platform/backup/export", {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    throw new Error(parseApiError(await response.text(), `Export failed: ${response.status}`));
  }
  return response.json();
}

export async function importPlatformBackup(
  json: string,
  dryRun: boolean
): Promise<PlatformBackupImportResult> {
  const response = await fetch(`/api/v1/platform/backup/import?dryRun=${dryRun ? "true" : "false"}`, {
    method: "POST",
    headers: {
      ...getAuthHeaders(),
      "Content-Type": "application/json",
    },
    body: json,
  });
  if (!response.ok) {
    throw new Error(parseApiError(await response.text(), `Import failed: ${response.status}`));
  }
  return response.json();
}
