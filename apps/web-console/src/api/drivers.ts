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

export function pollDriver(devicePath: string, pointId?: string): Promise<DriverRuntimeStatus> {
  const params = new URLSearchParams({ devicePath });
  if (pointId) {
    params.set("pointId", pointId);
  }
  return request(`/api/v1/drivers/runtime/poll?${params}`, { method: "POST" });
}

export type DriverBrowseNode = {
  nodeId: string;
  displayName: string;
  nodeClass: string;
};

export function browseDriverNodes(
  devicePath: string,
  nodeId?: string,
): Promise<DriverBrowseNode[]> {
  const params = new URLSearchParams({ devicePath });
  if (nodeId) {
    params.set("nodeId", nodeId);
  }
  return request(`/api/v1/drivers/runtime/browse?${params}`);
}

export type DriverCatalogArtifact = {
  name: string;
  moduleName: string;
  sizeBytes: number;
  status: string;
};

export type DriverCatalogNode = {
  nodeId: string;
  displayName: string;
  nodeClass: string;
  oid: string;
  syntax: string;
  maxAccess: string;
  units: string;
  description: string;
  selectable: boolean;
};

export type DriverPointSelection = {
  nodeId: string;
  index?: string;
};

export type DriverImportPointsResult = {
  createdVariables: number;
  updatedMappings: number;
  variableNames: string[];
};

export function listDriverCatalogArtifacts(
  driverId = "snmp",
): Promise<DriverCatalogArtifact[]> {
  const params = new URLSearchParams({ driverId });
  return request(`/api/v1/drivers/runtime/catalog/artifacts?${params}`);
}

export function importDriverCatalogArtifact(
  fileName: string,
  contentText: string,
  driverId = "snmp",
): Promise<DriverCatalogArtifact> {
  const params = new URLSearchParams({ driverId });
  return request(`/api/v1/drivers/runtime/catalog/artifacts?${params}`, {
    method: "POST",
    body: JSON.stringify({ fileName, contentText }),
  });
}

export function deleteDriverCatalogArtifact(
  name: string,
  driverId = "snmp",
): Promise<{ deleted: boolean; name: string }> {
  const params = new URLSearchParams({ driverId, name });
  return request(`/api/v1/drivers/runtime/catalog/artifacts?${params}`, {
    method: "DELETE",
  });
}

export function browseDriverCatalog(
  nodeId?: string,
  driverId = "snmp",
): Promise<DriverCatalogNode[]> {
  const params = new URLSearchParams({ driverId });
  if (nodeId) {
    params.set("nodeId", nodeId);
  }
  return request(`/api/v1/drivers/runtime/catalog/browse?${params}`);
}

export function importDriverCatalogPoints(
  devicePath: string,
  selections: DriverPointSelection[],
  driverId = "snmp",
): Promise<DriverImportPointsResult> {
  const params = new URLSearchParams({ devicePath, driverId });
  return request(`/api/v1/drivers/runtime/catalog/import-points?${params}`, {
    method: "POST",
    body: JSON.stringify({ selections }),
  });
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
