import type {
  ObjectSummary,
  CreateObjectPayload,
  DataRecord,
  PlatformInfo,
  UpdateObjectPayload,
  VariableDto,
  ObjectEditorDto,
} from "./types";
import type { DashboardView } from "./types/dashboard";
import type { WorkflowLifecycleStatus, WorkflowView } from "./types/workflow";
import { getAuthHeaders } from "./auth/session";

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

export function fetchPlatformInfo(): Promise<PlatformInfo> {
  return request("/api/v1/info");
}

export interface AuthMe {
  authenticated: boolean;
  principal?: string;
  roles: string[];
}

export function fetchAuthMe(): Promise<AuthMe> {
  return request("/api/v1/auth/me");
}

export function validateExpression(expression: string): Promise<{ valid: boolean; expression: string; error: string | null }> {
  return request("/api/v1/expressions/validate", {
    method: "POST",
    body: JSON.stringify({ expression }),
  });
}

export function fetchObjects(parent?: string): Promise<ObjectSummary[]> {
  const query = parent ? `?parent=${encodeURIComponent(parent)}` : "";
  return request(`/api/v1/objects${query}`);
}

export function fetchObjectEditor(path: string): Promise<ObjectEditorDto> {
  return request(`/api/v1/objects/by-path/editor?path=${encodeURIComponent(path)}`);
}

export function fetchObject(path: string): Promise<ObjectSummary> {
  return request(`/api/v1/objects/by-path?path=${encodeURIComponent(path)}`);
}

export function fetchVariables(path: string): Promise<VariableDto[]> {
  return request(`/api/v1/objects/by-path/variables?path=${encodeURIComponent(path)}`);
}

export interface VariableHistorySample {
  ts: string;
  value: number | null;
  text: string | null;
}

export interface VariableHistoryResponse {
  objectPath: string;
  variableName: string;
  field: string;
  samples: VariableHistorySample[];
}

export interface VariableHistoryBucket {
  ts: string;
  avg: number | null;
  min: number | null;
  max: number | null;
  count: number;
}

export interface VariableHistoryAggregateResponse {
  objectPath: string;
  variableName: string;
  field: string;
  bucket: string;
  buckets: VariableHistoryBucket[];
}

export function fetchVariableHistory(
  path: string,
  name: string,
  options?: { field?: string; from?: string; to?: string; limit?: number }
): Promise<VariableHistoryResponse> {
  const params = new URLSearchParams({ path, name });
  if (options?.field) params.set("field", options.field);
  if (options?.from) params.set("from", options.from);
  if (options?.to) params.set("to", options.to);
  if (options?.limit != null) params.set("limit", String(options.limit));
  return request(`/api/v1/objects/by-path/variables/history?${params}`);
}

export function fetchVariableHistoryAggregate(
  path: string,
  name: string,
  options: {
    bucket: string;
    field?: string;
    from?: string;
    to?: string;
    limit?: number;
  }
): Promise<VariableHistoryAggregateResponse> {
  const params = new URLSearchParams({ path, name, bucket: options.bucket });
  if (options.field) params.set("field", options.field);
  if (options.from) params.set("from", options.from);
  if (options.to) params.set("to", options.to);
  if (options.limit != null) params.set("limit", String(options.limit));
  return request(`/api/v1/objects/by-path/variables/history/aggregate?${params}`);
}

export async function downloadVariableHistoryExport(
  path: string,
  name: string,
  options: {
    format: "csv" | "json";
    field?: string;
    from?: string;
    to?: string;
    limit?: number;
  }
): Promise<void> {
  const params = new URLSearchParams({
    path,
    name,
    format: options.format,
  });
  if (options.field) params.set("field", options.field);
  if (options.from) params.set("from", options.from);
  if (options.to) params.set("to", options.to);
  if (options.limit != null) params.set("limit", String(options.limit));

  const response = await fetch(
    `/api/v1/objects/by-path/variables/history/export?${params}`,
    { headers: getAuthHeaders() }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Export failed: ${response.status}`);
  }

  const blob = await response.blob();
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = disposition.match(/filename="([^"]+)"/);
  const filename = match?.[1] ?? `${name}-${options.field ?? "value"}.${options.format}`;

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function createObject(payload: CreateObjectPayload): Promise<ObjectSummary> {
  return request("/api/v1/objects", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateObject(path: string, payload: UpdateObjectPayload): Promise<ObjectSummary> {
  return request(`/api/v1/objects/by-path?path=${encodeURIComponent(path)}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function deleteObject(path: string): Promise<void> {
  return request(`/api/v1/objects/by-path?path=${encodeURIComponent(path)}`, {
    method: "DELETE",
  });
}

export function setVariable(path: string, name: string, value: DataRecord): Promise<VariableDto> {
  const params = new URLSearchParams({ path, name });
  return request(`/api/v1/objects/by-path/variables?${params}`, {
    method: "PUT",
    body: JSON.stringify(value),
  });
}

export interface UpdateVariableHistoryPayload {
  historyEnabled: boolean;
  historyRetentionDays: number | null;
}

export function updateVariableHistory(
  path: string,
  name: string,
  payload: UpdateVariableHistoryPayload
): Promise<VariableDto> {
  const params = new URLSearchParams({ path, name });
  return request(`/api/v1/objects/by-path/variables/history?${params}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function fetchDashboard(path: string): Promise<DashboardView> {
  return request(`/api/v1/dashboards/by-path?path=${encodeURIComponent(path)}`);
}

export function saveDashboardLayout(path: string, layoutJson: string): Promise<DashboardView> {
  return request(`/api/v1/dashboards/by-path/layout?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify({ layoutJson }),
  });
}

export function saveDashboardTitle(path: string, title: string): Promise<DashboardView> {
  return request(`/api/v1/dashboards/by-path/title?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify({ title }),
  });
}

export function fetchWorkflow(path: string): Promise<WorkflowView> {
  return request(`/api/v1/workflows/by-path?path=${encodeURIComponent(path)}`);
}

export function saveWorkflowBpmn(path: string, bpmnXml: string): Promise<WorkflowView> {
  return request(`/api/v1/workflows/by-path/bpmn?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify({ bpmnXml }),
  });
}

export function updateWorkflowStatus(
  path: string,
  status: WorkflowLifecycleStatus
): Promise<WorkflowView> {
  return request(`/api/v1/workflows/by-path/status?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify({ status }),
  });
}

export function runWorkflow(path: string): Promise<WorkflowView> {
  return request(`/api/v1/workflows/by-path/run?path=${encodeURIComponent(path)}`, {
    method: "POST",
  });
}

export function fetchWorkQueue(limit = 50): Promise<import("./types/operator").WorkQueueItem[]> {
  return request(`/api/v1/work-queue?limit=${limit}`);
}

export function claimWorkTask(taskId: string, operatorId = "operator"): Promise<import("./types/operator").WorkQueueItem> {
  const params = new URLSearchParams({ taskId, operatorId });
  return request(`/api/v1/work-queue/claim?${params}`, { method: "POST" });
}

export function completeWorkTask(
  taskId: string,
  operatorId = "operator"
): Promise<import("./types/operator").WorkQueueItem> {
  const params = new URLSearchParams({ taskId, operatorId });
  return request(`/api/v1/work-queue/complete?${params}`, { method: "POST" });
}

export function invokeFunction(
  path: string,
  name: string,
  input?: unknown
): Promise<{ schema: unknown; rows: Array<Record<string, unknown>> }> {
  const params = new URLSearchParams({ path, name });
  return request(`/api/v1/objects/by-path/functions/invoke?${params}`, {
    method: "POST",
    body: input ? JSON.stringify(input) : undefined,
  });
}

export function fetchEvents(objectPath?: string, limit = 50): Promise<import("./types/event").ObjectEvent[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (objectPath) {
    params.set("objectPath", objectPath);
  }
  return request(`/api/v1/events?${params}`);
}

export function fetchAlertRules(): Promise<import("./types/event").AlertRule[]> {
  return request("/api/v1/alert-rules");
}

export function createAlertRule(
  payload: import("./types/automation").CreateAlertRulePayload
): Promise<import("./types/event").AlertRule> {
  return request("/api/v1/alert-rules", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateAlertRule(
  id: string,
  payload: Partial<import("./types/automation").CreateAlertRulePayload>
): Promise<import("./types/event").AlertRule> {
  return request(`/api/v1/alert-rules/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function deleteAlertRule(id: string): Promise<void> {
  return request(`/api/v1/alert-rules/${encodeURIComponent(id)}`, { method: "DELETE" });
}

export function fetchCorrelators(): Promise<import("./types/event").EventCorrelator[]> {
  return request("/api/v1/correlators");
}

export function createCorrelator(
  payload: import("./types/automation").CreateCorrelatorPayload
): Promise<import("./types/event").EventCorrelator> {
  return request("/api/v1/correlators", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateCorrelator(
  id: string,
  payload: Partial<import("./types/automation").CreateCorrelatorPayload>
): Promise<import("./types/event").EventCorrelator> {
  return request(`/api/v1/correlators/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function deleteCorrelator(id: string): Promise<void> {
  return request(`/api/v1/correlators/${encodeURIComponent(id)}`, { method: "DELETE" });
}
