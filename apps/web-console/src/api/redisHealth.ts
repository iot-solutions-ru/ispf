import { getAuthHeaders } from "../auth/session";

export interface RedisHealth {
  enabled: boolean;
  connected: boolean;
  host: string | null;
  port: number | null;
  correlatorWindowsEnabled: boolean;
  correlatorWindowStore: "redis" | "jdbc";
  aclCacheBackend: "redis" | "local";
  objectAclTtlSeconds: number;
  contextPackTtlSeconds: number;
  platformBriefingTtlSeconds: number;
  correlatorWindowKeys: number | null;
  connectionError: string | null;
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchRedisHealth(): Promise<RedisHealth> {
  return fetch("/api/v1/platform/redis/health", {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<RedisHealth>(response));
}
