import { getAuthHeaders } from "../auth/session";
import type { ConfigureDriverPayload, DriverMetadata, DriverRuntimeStatus } from "../types/drivers";

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
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

export function fetchDrivers(): Promise<DriverMetadata[]> {
  return request("/api/v1/drivers");
}

export function fetchDriverStatus(devicePath: string): Promise<DriverRuntimeStatus> {
  const params = new URLSearchParams({ devicePath });
  return request(`/api/v1/drivers/runtime/status?${params}`);
}

export function startDriver(devicePath: string): Promise<DriverRuntimeStatus> {
  const params = new URLSearchParams({ devicePath });
  return request(`/api/v1/drivers/runtime/start?${params}`, { method: "POST" });
}

export function stopDriver(devicePath: string): Promise<DriverRuntimeStatus> {
  const params = new URLSearchParams({ devicePath });
  return request(`/api/v1/drivers/runtime/stop?${params}`, { method: "POST" });
}

export function configureDriver(
  devicePath: string,
  payload: ConfigureDriverPayload
): Promise<DriverRuntimeStatus> {
  const params = new URLSearchParams({ devicePath });
  return request(`/api/v1/drivers/runtime/configure?${params}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function pollDriver(devicePath: string): Promise<DriverRuntimeStatus> {
  const params = new URLSearchParams({ devicePath });
  return request(`/api/v1/drivers/runtime/poll?${params}`, { method: "POST" });
}

export function writeDriverPoint(
  devicePath: string,
  pointId: string,
  value: { schema?: unknown; rows: Array<Record<string, unknown>> }
): Promise<DriverRuntimeStatus> {
  const params = new URLSearchParams({ devicePath, pointId });
  return request(`/api/v1/drivers/runtime/write?${params}`, {
    method: "POST",
    body: JSON.stringify(value),
  });
}
