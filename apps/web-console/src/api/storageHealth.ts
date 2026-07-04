import { getAuthHeaders } from "../auth/session";

export interface StorageBackendInfo {
  id: string;
  role: string;
  store: string;
  engine: string;
  endpoint: string | null;
  connected: boolean;
  connectionError: string | null;
  recordCount: number | null;
  retentionDays: number | null;
  details: Record<string, unknown>;
}

export interface StorageHealth {
  timestamp: string;
  backends: StorageBackendInfo[];
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchStorageHealth(): Promise<StorageHealth> {
  return fetch("/api/v1/platform/storage/health", {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<StorageHealth>(response));
}
