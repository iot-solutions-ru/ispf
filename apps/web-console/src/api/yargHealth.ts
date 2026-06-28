import { getAuthHeaders } from "../auth/session";

export interface YargHealth {
  libreOfficeAvailable: boolean;
  configuredPath: string | null;
  resolvedPath: string | null;
  timeoutSeconds: number;
  ports: number[];
  pdfHint: string;
}

export function fetchYargHealth(): Promise<YargHealth> {
  return fetch("/api/v1/platform/reports/yarg/health", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await response.text() || `Request failed: ${response.status}`);
    }
    return response.json();
  });
}
