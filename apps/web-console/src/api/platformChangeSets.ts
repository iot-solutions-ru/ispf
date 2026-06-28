import { getAuthHeaders } from "../auth/session";

export interface ChangeSetOp {
  op: string;
  path: string;
  expectedRevision?: number | null;
  payload?: Record<string, unknown> | null;
}

export interface ChangeSetSummary {
  id: string;
  title: string;
  author: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChangeSetDetail extends ChangeSetSummary {
  baseSnapshot: string | null;
  ops: ChangeSetOp[];
}

export interface ChangeSetPreview {
  changeSetId: string;
  title: string;
  conflicts: Array<Record<string, unknown>>;
  applicable: Array<Record<string, unknown>>;
  conflictCount: number;
}

export interface ChangeSetApplyResult {
  status: string;
  applied: string[];
  count: number;
}

async function platformRequest<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    cache: "no-store",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
      ...(init?.headers ?? {}),
    },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function fetchChangeSets(status?: string): Promise<ChangeSetSummary[]> {
  const params = status ? `?status=${encodeURIComponent(status)}` : "";
  return platformRequest(`/api/v1/platform/change-sets${params}`);
}

export function fetchChangeSet(id: string): Promise<ChangeSetDetail> {
  return platformRequest(`/api/v1/platform/change-sets/${encodeURIComponent(id)}`);
}

export function createChangeSet(title: string, ops: ChangeSetOp[]): Promise<ChangeSetDetail> {
  return platformRequest("/api/v1/platform/change-sets", {
    method: "POST",
    body: JSON.stringify({ title, ops }),
  });
}

export function previewChangeSet(id: string): Promise<ChangeSetPreview> {
  return platformRequest(`/api/v1/platform/change-sets/${encodeURIComponent(id)}/preview`, {
    method: "POST",
  });
}

export function applyChangeSet(id: string, force = false): Promise<ChangeSetApplyResult> {
  const params = force ? "?force=true" : "";
  return platformRequest(`/api/v1/platform/change-sets/${encodeURIComponent(id)}/apply${params}`, {
    method: "POST",
  });
}
