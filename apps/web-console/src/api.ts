import type { DataRecord, PlatformInfo } from "./types";
import { request } from "./api/httpClient";

export { writeHeaders, type ObjectWriteOptions } from "./api/httpClient";

export type {
  VariableHistorySample,
  VariableHistoryResponse,
  VariableHistoryBucket,
  VariableHistoryAggregateResponse,
  AnalyticsTagSourceDto,
  AnalyticsTagLineageNodeDto,
  AnalyticsTagLineageEdgeDto,
  AnalyticsTagLineageGraphDto,
  AnalyticsTagCatalogEntryDto,
  AnalyticsTagCatalogListDto,
  AnalyticsExpressionValidateResult,
  AnalyticsExpressionEvaluateResult,
  AnalyticsTagEvaluateResult,
  AnalyticsQueryTagInput,
  AnalyticsQueryRequestBody,
  AnalyticsQuerySeriesDto,
  AnalyticsQueryResponseDto,
  VisualGroupMemberDto,
  BulkDeleteResult,
  UpdateVariableHistoryPayload,
  CreateVariablePayload,
  UpdateVariableDefinitionPayload,
} from "./api/objectsCore";

export {
  fetchObjects,
  reorderObjectChildren,
  fetchObjectEditor,
  fetchObject,
  fetchVariables,
  fetchVariablesBatch,
  fetchVariableHistory,
  fetchVariableHistoryAggregate,
  refreshAnalyticsDerivedTag,
  fetchAnalyticsTags,
  fetchAnalyticsTagByPath,
  validateAnalyticsExpression,
  evaluateAnalyticsTag,
  askAnalyticsTag,
  evaluateAnalyticsExpression,
  fetchAnalyticsQuery,
  downloadAnalyticsQueryExport,
  downloadVariableHistoryExport,
  createObject,
  updateObject,
  deleteObject,
  fetchGroupMembers,
  updateGroupMembers,
  bulkDeleteObjects,
  setVariable,
  updateVariableHistory,
  createVariable,
  updateVariableDefinition,
  deleteVariable,
  fetchBindingRules,
  saveBindingRules,
  deleteBindingRule,
  upsertFunction,
  deleteFunction,
  upsertEvent,
  deleteEvent,
} from "./api/objectsCore";

export type {
  CancelWorkflowResult,
  SignalWorkflowResult,
} from "./api/dashboardsCore";

export {
  fetchDashboard,
  fetchDashboardContext,
  saveDashboardContext,
  saveDashboardLayout,
  saveDashboardTitle,
  saveDashboardRefreshInterval,
  fetchMimic,
  saveMimicDiagram,
  saveMimicTitle,
  fetchWorkflow,
  saveWorkflowBpmn,
  updateWorkflowStatus,
  updateWorkflowOperatorApp,
  runWorkflow,
  fetchWorkflowRuns,
  fetchWorkflowSteps,
  invokeWorkflowTool,
  cancelWorkflowInstance,
  signalWorkflowInstance,
} from "./api/dashboardsCore";

export function fetchPlatformInfo(): Promise<PlatformInfo> {
  return request("/api/v1/info");
}

export interface AuthMe {
  authenticated: boolean;
  principal?: string;
  roles: string[];
  timeZone?: string;
}

export function fetchAuthMe(): Promise<AuthMe> {
  return request("/api/v1/auth/me");
}

export function updateAuthTimeZone(timeZone: string): Promise<{ username: string; timeZone: string }> {
  return request("/api/v1/auth/me/timezone", {
    method: "PUT",
    body: JSON.stringify({ timeZone }),
  });
}

export function validateExpression(expression: string): Promise<{ valid: boolean; expression: string; error: string | null }> {
  return request("/api/v1/expressions/validate", {
    method: "POST",
    body: JSON.stringify({ expression }),
  });
}

export interface EvaluateExpressionPayload {
  objectPath: string;
  expression: string;
  targetVariable?: string;
}

export interface EvaluateExpressionStep {
  phase: string;
  status: string;
  detail?: unknown;
}

export function evaluateExpression(
  payload: EvaluateExpressionPayload
): Promise<{
  valid: boolean;
  expression: string;
  result: unknown;
  resultType: string | null;
  error: string | null;
  steps: EvaluateExpressionStep[];
}> {
  return request("/api/v1/expressions/evaluate", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function fetchWorkQueue(limit = 50, operatorAppId?: string): Promise<import("./types/operator").WorkQueueItem[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (operatorAppId?.trim()) {
    params.set("operatorAppId", operatorAppId.trim());
  }
  return request(`/api/v1/work-queue?${params}`);
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
  input?: DataRecord
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

export function fireEvent(
  objectPath: string,
  eventName: string,
  payload?: DataRecord
): Promise<import("./types/event").ObjectEvent> {
  const params = new URLSearchParams({ objectPath, eventName });
  return request(`/api/v1/events/fire?${params}`, {
    method: "POST",
    body: payload ? JSON.stringify(payload) : undefined,
  });
}

export function fetchFunctionInvocations(options: {
  objectPath?: string;
  functionName?: string;
  success?: boolean;
  limit?: number;
} = {}): Promise<import("./types/runtime").FunctionInvokeAuditEntry[]> {
  const params = new URLSearchParams({ limit: String(options.limit ?? 50) });
  if (options.objectPath) {
    params.set("objectPath", options.objectPath);
  }
  if (options.functionName) {
    params.set("functionName", options.functionName);
  }
  if (typeof options.success === "boolean") {
    params.set("success", String(options.success));
  }
  return request(`/api/v1/platform/function-invocations?${params}`);
}

export function fetchBindingInvocations(options: {
  objectPath?: string;
  bindingKind?: string;
  ruleId?: string;
  success?: boolean;
  changed?: boolean;
  limit?: number;
} = {}): Promise<import("./types/runtime").BindingInvokeAuditEntry[]> {
  const params = new URLSearchParams({ limit: String(options.limit ?? 50) });
  if (options.objectPath) {
    params.set("objectPath", options.objectPath);
  }
  if (options.bindingKind) {
    params.set("bindingKind", options.bindingKind);
  }
  if (options.ruleId) {
    params.set("ruleId", options.ruleId);
  }
  if (typeof options.success === "boolean") {
    params.set("success", String(options.success));
  }
  if (typeof options.changed === "boolean") {
    params.set("changed", String(options.changed));
  }
  return request(`/api/v1/platform/binding-invocations?${params}`);
}

export function fetchFunctionAuditStatus(objectPath?: string): Promise<{
  masterEnabled: boolean;
  objectEnabled: boolean;
  enabled: boolean;
}> {
  const params = objectPath ? `?objectPath=${encodeURIComponent(objectPath)}` : "";
  return request(`/api/v1/platform/function-audit-status${params}`);
}

export function fetchBindingAuditStatus(objectPath?: string): Promise<{
  masterEnabled: boolean;
  objectEnabled: boolean;
  enabled: boolean;
}> {
  const params = objectPath ? `?objectPath=${encodeURIComponent(objectPath)}` : "";
  return request(`/api/v1/platform/binding-audit-status${params}`);
}

export function fetchEventJournalStatus(objectPath?: string): Promise<{
  masterEnabled: boolean;
  globalTableEnabled: boolean;
  objectEnabled: boolean;
  enabled: boolean;
}> {
  const params = objectPath ? `?objectPath=${encodeURIComponent(objectPath)}` : "";
  return request(`/api/v1/events/journal-status${params}`);
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
  path: string,
  payload: Partial<import("./types/automation").CreateAlertRulePayload>
): Promise<import("./types/event").AlertRule> {
  return request(`/api/v1/alert-rules/by-path?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function deleteAlertRule(path: string): Promise<void> {
  return request(`/api/v1/alert-rules/by-path?path=${encodeURIComponent(path)}`, { method: "DELETE" });
}

export interface AlarmShelf {
  id: string;
  objectPath: string;
  eventName: string;
  alertRulePath: string | null;
  shelvedBy: string;
  shelvedAt: string;
  expiresAt: string | null;
  comment: string | null;
  active: boolean;
}

export interface AlarmShelfPendingRequest {
  id: string;
  objectPath: string;
  eventName: string;
  alertRulePath: string | null;
  durationMinutes: number | null;
  comment: string | null;
  requestedBy: string;
  requestedAt: string;
}

export function isAlarmShelfPendingRequest(
  value: AlarmShelf | AlarmShelfPendingRequest
): value is AlarmShelfPendingRequest {
  return "requestedBy" in value && !("active" in value);
}

export function fetchAlarmShelves(): Promise<AlarmShelf[]> {
  return request("/api/v1/alarm-shelves");
}

export function shelveAlarm(payload: {
  objectPath: string;
  eventName: string;
  alertRulePath?: string;
  durationMinutes?: number;
  comment?: string;
}): Promise<AlarmShelf | AlarmShelfPendingRequest> {
  return request("/api/v1/alarm-shelves", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function unshelveAlarm(id: string): Promise<void> {
  return request(`/api/v1/alarm-shelves/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export function fetchAlarmShelfRequests(): Promise<AlarmShelfPendingRequest[]> {
  return request("/api/v1/alarm-shelves/requests");
}

export function approveAlarmShelfRequest(id: string): Promise<AlarmShelf> {
  return request(`/api/v1/alarm-shelves/requests/${encodeURIComponent(id)}/approve`, {
    method: "POST",
  });
}

export function rejectAlarmShelfRequest(id: string): Promise<void> {
  return request(`/api/v1/alarm-shelves/requests/${encodeURIComponent(id)}/reject`, {
    method: "POST",
  });
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
  path: string,
  payload: Partial<import("./types/automation").CreateCorrelatorPayload>
): Promise<import("./types/event").EventCorrelator> {
  return request(`/api/v1/correlators/by-path?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function deleteCorrelator(path: string): Promise<void> {
  return request(`/api/v1/correlators/by-path?path=${encodeURIComponent(path)}`, { method: "DELETE" });
}

export function fetchEventFilters(): Promise<import("./types/automation").EventFilterPayload[]> {
  return request("/api/v1/event-filters");
}

export function createEventFilter(
  payload: import("./types/automation").EventFilterPayload
): Promise<import("./types/automation").EventFilterPayload & { path: string }> {
  return request("/api/v1/event-filters", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateEventFilter(
  path: string,
  payload: Partial<import("./types/automation").EventFilterPayload>
): Promise<import("./types/automation").EventFilterPayload> {
  return request(`/api/v1/event-filters/by-path?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify({ ...payload, path }),
  });
}

export interface ObjectConfigAuditEntry {
  id: string;
  objectPath: string;
  changeType: string;
  field: string;
  actor: string;
  occurredAt: string;
  revisionBefore: number;
  revisionAfter: number;
  summaryJson: string;
}

export function fetchObjectAudit(path: string, limit = 50): Promise<ObjectConfigAuditEntry[]> {
  const params = new URLSearchParams({ path, limit: String(limit) });
  return request(`/api/v1/objects/by-path/audit?${params}`);
}

export interface EditLease {
  id: string;
  pathPrefix: string;
  holder: string;
  expiresAt: string;
  createdAt: string;
}

export function fetchEditLeases(): Promise<EditLease[]> {
  return request("/api/v1/objects/leases");
}

export function acquireEditLease(pathPrefix: string, ttlMinutes = 120): Promise<EditLease> {
  return request("/api/v1/objects/leases", {
    method: "POST",
    body: JSON.stringify({ pathPrefix, ttlMinutes }),
  });
}

export function releaseEditLease(pathPrefix: string): Promise<void> {
  const params = new URLSearchParams({ pathPrefix });
  return request(`/api/v1/objects/leases?${params}`, { method: "DELETE" });
}

