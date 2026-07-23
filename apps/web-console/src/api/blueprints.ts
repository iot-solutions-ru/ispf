import { getAuthHeaders } from "../auth/session";
import type {
  CreateBlueprintPayload,
  BlueprintAttachmentDto,
  BlueprintDto,
  BlueprintType,
  UpdateBlueprintPayload,
} from "../types/blueprints";
import type { ObjectSummary } from "../types";

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
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

export function fetchBlueprints(): Promise<BlueprintDto[]> {
  return request("/api/v1/blueprints");
}

export function fetchMixinBlueprints(): Promise<BlueprintDto[]> {
  return request("/api/v1/mixin-blueprints");
}

export function fetchInstanceTypes(platformType?: string, parentPath?: string): Promise<BlueprintDto[]> {
  const params = new URLSearchParams();
  if (platformType) params.set("platformType", platformType);
  if (parentPath) params.set("parentPath", parentPath);
  const query = params.toString();
  return request(`/api/v1/instance-types${query ? `?${query}` : ""}`);
}

export function fetchSingletonBlueprints(): Promise<BlueprintDto[]> {
  return request("/api/v1/singleton-blueprints");
}

export function fetchBlueprintByName(name: string): Promise<BlueprintDto> {
  return request(`/api/v1/blueprints/by-name/${encodeURIComponent(name)}`);
}

export function createBlueprint(payload: CreateBlueprintPayload): Promise<BlueprintDto> {
  return request("/api/v1/blueprints", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateBlueprint(id: string, payload: UpdateBlueprintPayload): Promise<BlueprintDto> {
  return request(`/api/v1/blueprints/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function deleteBlueprint(id: string): Promise<void> {
  return request(`/api/v1/blueprints/${encodeURIComponent(id)}`, { method: "DELETE" });
}

export function applyBlueprint(blueprintId: string, objectPath: string): Promise<BlueprintAttachmentDto> {
  return request(
    `/api/v1/blueprints/${encodeURIComponent(blueprintId)}/apply?objectPath=${encodeURIComponent(objectPath)}`,
    { method: "POST" }
  );
}

export function fetchSingletonBlueprintInstance(blueprintId: string): Promise<ObjectSummary> {
  return request(`/api/v1/singleton-blueprints/${encodeURIComponent(blueprintId)}/instance`);
}

export function instantiateBlueprint(
  blueprintId: string,
  parentPath: string,
  instanceName: string,
  parameters?: Record<string, string>
): Promise<ObjectSummary> {
  return request(`/api/v1/blueprints/${encodeURIComponent(blueprintId)}/instantiate`, {
    method: "POST",
    body: JSON.stringify({ parentPath, instanceName, parameters: parameters ?? {} }),
  });
}

export function createBlueprintFromObject(
  sourcePath: string,
  blueprintName: string,
  description?: string,
  type?: BlueprintType
): Promise<BlueprintDto> {
  return request("/api/v1/blueprints/from-object", {
    method: "POST",
    body: JSON.stringify({ sourcePath, blueprintName, description, type }),
  });
}

export function fetchBlueprintAttachments(objectPath?: string): Promise<BlueprintAttachmentDto[]> {
  const query = objectPath ? `?objectPath=${encodeURIComponent(objectPath)}` : "";
  return request(`/api/v1/blueprints/attachments${query}`);
}

export function upgradeBlueprintInstances(blueprintId: string): Promise<{
  status: string;
  blueprintVersion: string;
  upgraded: string[];
  count: number;
}> {
  return request(`/api/v1/blueprints/${encodeURIComponent(blueprintId)}/upgrade-instances`, {
    method: "POST",
  });
}

export function fetchBlueprintInstances(blueprintId: string): Promise<{ objectPath: string }[]> {
  return request(`/api/v1/blueprints/${encodeURIComponent(blueprintId)}/instances`);
}

export function upgradeBlueprint(
  blueprintId: string,
  targetPath: string,
  targetVersion?: string
): Promise<Record<string, unknown>> {
  const params = new URLSearchParams({ targetPath });
  if (targetVersion) params.set("targetVersion", targetVersion);
  return request(`/api/v1/blueprints/${encodeURIComponent(blueprintId)}/upgrade?${params}`, {
    method: "POST",
  });
}

export interface BlueprintDiffResult {
  objectPath: string;
  blueprintVersion: string;
  variablesToAdd: string[];
  variablesOnlyOnObject: string[];
  eventsToAdd: string[];
  functionsToAdd: string[];
  bindingsCount: number;
}

export function fetchBlueprintDiff(blueprintId: string, objectPath: string): Promise<BlueprintDiffResult> {
  const params = new URLSearchParams({ objectPath });
  return request(`/api/v1/blueprints/${encodeURIComponent(blueprintId)}/diff?${params}`);
}

export interface BlueprintMergePreviewResult {
  objectPath: string;
  baseBlueprintId: string;
  theirsBlueprintId: string;
  variableConflicts: Array<{ name: string; baseSchema: string; theirsSchema: string; onObject: boolean }>;
  conflictCount: number;
}

export function fetchBlueprintMergePreview(
  baseBlueprintId: string,
  theirsBlueprintId: string,
  objectPath: string
): Promise<BlueprintMergePreviewResult> {
  return request("/api/v1/blueprints/merge-preview", {
    method: "POST",
    body: JSON.stringify({ baseBlueprintId, theirsBlueprintId, objectPath }),
  });
}
