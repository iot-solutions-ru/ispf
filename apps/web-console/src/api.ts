import type {
  ObjectSummary,
  CreateObjectPayload,
  DataRecord,
  DataSchema,
  PlatformInfo,
  UpdateObjectPayload,
  VariableDto,
  ObjectEditorDto,
  FunctionDescriptor,
  EventDescriptor,
  BindingRule,
} from "./types";
import type { DashboardView } from "./types/dashboard";
import type { WorkflowLifecycleStatus, WorkflowView } from "./types/workflow";
import { getAuthHeaders, getStoredSession } from "./auth/session";
import { parseApiError } from "./utils/parseApiError";
import { invalidateStoredSession } from "./auth/validateSession";

let authFailureCheck: Promise<void> | null = null;

async function handlePossibleAuthFailure(status: number): Promise<void> {
  if (status !== 401 && status !== 403) {
    return;
  }
  const token = getStoredSession()?.token;
  if (!token) {
    return;
  }
  if (authFailureCheck) {
    await authFailureCheck;
    return;
  }
  authFailureCheck = (async () => {
    try {
      const response = await fetch("/api/v1/auth/me", {
        cache: "no-store",
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!response.ok) {
        invalidateStoredSession();
        return;
      }
      const me = (await response.json()) as { authenticated?: boolean };
      if (!me.authenticated) {
        invalidateStoredSession();
      }
    } catch {
      invalidateStoredSession();
    } finally {
      authFailureCheck = null;
    }
  })();
  await authFailureCheck;
}

type RequestOptions = RequestInit & { authToken?: string };

async function request<T>(url: string, init?: RequestOptions): Promise<T> {
  const { authToken, headers: extraHeaders, ...fetchInit } = init ?? {};
  const authHeaders = authToken?.trim()
    ? { Authorization: `Bearer ${authToken.trim()}` }
    : getAuthHeaders();
  const response = await fetch(url, {
    ...fetchInit,
    cache: "no-store",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders,
      ...(extraHeaders ?? {}),
    },
  });
  if (!response.ok) {
    await handlePossibleAuthFailure(response.status);
    const text = await response.text();
    let message = parseApiError(text, `Request failed: ${response.status}`);
    try {
      const json = JSON.parse(text) as { error?: string; message?: string };
      if (json.error === "REVISION_CONFLICT") {
        message = `REVISION_CONFLICT:${text}`;
      }
    } catch {
      // keep parsed message
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

export interface ObjectWriteOptions {
  revision?: number;
  force?: boolean;
  authToken?: string;
}

function writeHeaders(options?: ObjectWriteOptions): Record<string, string> {
  const headers: Record<string, string> = {};
  if (options?.revision != null) {
    headers["If-Match"] = String(options.revision);
  }
  if (options?.force) {
    headers["X-ISPF-Force"] = "true";
  }
  return headers;
}

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

export function fetchObjects(parent?: string, lite = true): Promise<ObjectSummary[]> {
  const params = new URLSearchParams();
  if (parent) {
    params.set("parent", parent);
  }
  if (lite) {
    params.set("lite", "true");
  }
  const query = params.toString();
  return request(`/api/v1/objects${query ? `?${query}` : ""}`);
}

export function reorderObjectChildren(parentPath: string, orderedPaths: string[]): Promise<void> {
  return request("/api/v1/objects/reorder", {
    method: "PUT",
    body: JSON.stringify({ parentPath, orderedPaths }),
  });
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

const VARIABLES_BATCH_MAX_PATHS = 50;

export async function fetchVariablesBatch(paths: string[]): Promise<Record<string, VariableDto[]>> {
  if (paths.length === 0) {
    return {};
  }
  const uniquePaths = [...new Set(paths.filter(Boolean))];
  const result: Record<string, VariableDto[]> = {};
  for (let i = 0; i < uniquePaths.length; i += VARIABLES_BATCH_MAX_PATHS) {
    const chunk = uniquePaths.slice(i, i + VARIABLES_BATCH_MAX_PATHS);
    const pathsParam = chunk.map((path) => encodeURIComponent(path)).join(",");
    const part = await request<Record<string, VariableDto[]>>(
      `/api/v1/objects/variables/batch?paths=${pathsParam}`
    );
    Object.assign(result, part);
  }
  return result;
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
  dataSource?: "rollup" | "raw" | "none";
}

export function fetchVariableHistory(
  path: string,
  name: string,
  options?: {
    field?: string;
    from?: string;
    to?: string;
    calendarRange?: string;
    timeZone?: string;
    limit?: number;
  }
): Promise<VariableHistoryResponse> {
  const params = new URLSearchParams({ path, name });
  if (options?.field) params.set("field", options.field);
  if (options?.calendarRange) {
    params.set("calendarRange", options.calendarRange);
    if (options.timeZone) params.set("timeZone", options.timeZone);
  } else {
    if (options?.from) params.set("from", options.from);
    if (options?.to) params.set("to", options.to);
  }
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
    calendarRange?: string;
    timeZone?: string;
    limit?: number;
  }
): Promise<VariableHistoryAggregateResponse> {
  const params = new URLSearchParams({ path, name, bucket: options.bucket });
  if (options.field) params.set("field", options.field);
  if (options.calendarRange) {
    params.set("calendarRange", options.calendarRange);
    if (options.timeZone) params.set("timeZone", options.timeZone);
  } else {
    if (options.from) params.set("from", options.from);
    if (options.to) params.set("to", options.to);
  }
  if (options.limit != null) params.set("limit", String(options.limit));
  return request(`/api/v1/objects/by-path/variables/history/aggregate?${params}`);
}

export function refreshAnalyticsDerivedTag(devicePath: string): Promise<{ devicePath: string; status: string; message: string }> {
  return request(`/api/v1/platform/analytics/derived-tags/refresh?devicePath=${encodeURIComponent(devicePath)}`, {
    method: "POST",
  });
}

export interface AnalyticsTagSourceDto {
  path: string;
  variable: string;
  field: string;
}

export interface AnalyticsTagLineageNodeDto {
  id: string;
  kind: string;
  label: string;
  path: string;
  variable: string;
}

export interface AnalyticsTagLineageEdgeDto {
  from: string;
  to: string;
  relation: string;
}

export interface AnalyticsTagLineageGraphDto {
  nodes: AnalyticsTagLineageNodeDto[];
  edges: AnalyticsTagLineageEdgeDto[];
}

export interface AnalyticsTagCatalogEntryDto {
  path: string;
  displayName: string;
  helper: string;
  expression: string;
  outputVariable: string;
  sources: AnalyticsTagSourceDto[];
  upstreamTagPaths: string[];
  downstreamTagPaths: string[];
  windowBucket: string;
  rollupBuckets: string[];
  periodicMs: number;
  enabled: boolean;
  qualityStatus: string;
  lastEvalStatus: string;
  lastEvalAt: string | null;
  lastTickAt: string | null;
  lineage: AnalyticsTagLineageGraphDto;
}

export interface AnalyticsTagCatalogListDto {
  count: number;
  tags: AnalyticsTagCatalogEntryDto[];
}

export function fetchAnalyticsTags(pathPrefix?: string): Promise<AnalyticsTagCatalogListDto> {
  const params = pathPrefix ? `?path=${encodeURIComponent(pathPrefix)}` : "";
  return request(`/api/v1/platform/analytics/tags${params}`);
}

export function fetchAnalyticsTagByPath(path: string): Promise<AnalyticsTagCatalogEntryDto> {
  return request(`/api/v1/platform/analytics/tags/by-path?path=${encodeURIComponent(path)}`);
}

export interface AnalyticsExpressionValidateResult {
  valid: boolean;
  expandedExpression: string | null;
  historianSources: string[];
  errors: string[];
}

export interface AnalyticsExpressionEvaluateResult {
  value: number;
  expandedExpression: string;
  latencyMs: number;
}

export function validateAnalyticsExpression(
  expression: string,
  objectPath: string
): Promise<AnalyticsExpressionValidateResult> {
  return request("/api/v1/platform/analytics/expression/validate", {
    method: "POST",
    body: JSON.stringify({ expression, objectPath }),
  });
}

export function evaluateAnalyticsExpression(
  expression: string,
  objectPath: string,
  asOf?: string
): Promise<AnalyticsExpressionEvaluateResult> {
  return request("/api/v1/platform/analytics/expression/evaluate", {
    method: "POST",
    body: JSON.stringify({ expression, objectPath, asOf }),
  });
}

export interface AnalyticsQueryTagInput {
  path: string;
  variable: string;
  field?: string;
  label?: string;
}

export interface AnalyticsQueryRequestBody {
  tags: AnalyticsQueryTagInput[];
  from: string;
  to: string;
  bucket: string;
  agg?: "avg" | "min" | "max" | "last";
  maxBuckets?: number;
}

export interface AnalyticsQuerySeriesDto {
  id: string;
  path: string;
  variable: string;
  field: string;
  dataSource: string;
  values: Array<number | null>;
}

export interface AnalyticsQueryResponseDto {
  bucket: string;
  from: string;
  to: string;
  agg: string;
  timestamps: string[];
  series: AnalyticsQuerySeriesDto[];
  latencyMs: number;
}

export function fetchAnalyticsQuery(body: AnalyticsQueryRequestBody): Promise<AnalyticsQueryResponseDto> {
  return request("/api/v1/platform/analytics/query", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function downloadAnalyticsQueryExport(
  body: AnalyticsQueryRequestBody,
  format: "csv" | "parquet",
): Promise<Blob> {
  const params = new URLSearchParams({ format });
  const response = await fetch(`/api/v1/platform/analytics/query/export?${params}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response.blob();
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

export function updateObject(
  path: string,
  payload: UpdateObjectPayload,
  options?: ObjectWriteOptions
): Promise<ObjectSummary> {
  return request(`/api/v1/objects/by-path?path=${encodeURIComponent(path)}`, {
    method: "PATCH",
    headers: writeHeaders(options),
    body: JSON.stringify(payload),
  });
}

export function deleteObject(path: string): Promise<void> {
  return request(`/api/v1/objects/by-path?path=${encodeURIComponent(path)}`, {
    method: "DELETE",
  });
}

export interface VisualGroupMemberDto {
  path: string;
  sortOrder: number;
}

export interface BulkDeleteResult {
  deleted: number;
  results: Array<{ path: string; success: boolean; error: string | null }>;
}

export function fetchGroupMembers(groupPath: string): Promise<VisualGroupMemberDto[]> {
  return request(`/api/v1/objects/by-path/group-members?path=${encodeURIComponent(groupPath)}`);
}

export function updateGroupMembers(
  groupPath: string,
  action: "set" | "add" | "remove" | "reorder",
  payload: { members?: VisualGroupMemberDto[]; paths?: string[] },
): Promise<VisualGroupMemberDto[]> {
  return request(`/api/v1/objects/by-path/group-members?path=${encodeURIComponent(groupPath)}`, {
    method: "PUT",
    body: JSON.stringify({ action, ...payload }),
  });
}

export function bulkDeleteObjects(paths: string[]): Promise<BulkDeleteResult> {
  return request("/api/v1/objects/bulk-delete", {
    method: "POST",
    body: JSON.stringify({ paths }),
  });
}

export function setVariable(
  path: string,
  name: string,
  value: DataRecord,
  options?: ObjectWriteOptions
): Promise<VariableDto> {
  const params = new URLSearchParams({ path, name });
  return request(`/api/v1/objects/by-path/variables?${params}`, {
    method: "PUT",
    headers: writeHeaders(options),
    body: JSON.stringify(value),
    authToken: options?.authToken,
  });
}

export interface UpdateVariableHistoryPayload {
  historyEnabled: boolean;
  historyRetentionDays: number | null;
}

export function updateVariableHistory(
  path: string,
  name: string,
  payload: UpdateVariableHistoryPayload,
  options?: ObjectWriteOptions
): Promise<VariableDto> {
  const params = new URLSearchParams({ path, name });
  return request(`/api/v1/objects/by-path/variables/history?${params}`, {
    method: "PATCH",
    headers: writeHeaders(options),
    body: JSON.stringify(payload),
  });
}

export interface CreateVariablePayload {
  name: string;
  schema?: DataSchema;
  readable?: boolean;
  writable?: boolean;
  initialValue?: DataRecord | null;
  historyEnabled?: boolean;
  historyRetentionDays?: number | null;
}

export function createVariable(path: string, payload: CreateVariablePayload): Promise<VariableDto> {
  const params = new URLSearchParams({ path });
  return request(`/api/v1/objects/by-path/variables?${params}`, {
    method: "POST",
    body: JSON.stringify({
      readable: true,
      writable: false,
      historyEnabled: false,
      ...payload,
    }),
  });
}

export interface UpdateVariableDefinitionPayload {
  readable?: boolean;
  writable?: boolean;
  readRoles?: string[];
  writeRoles?: string[];
}

export function updateVariableDefinition(
  path: string,
  name: string,
  payload: UpdateVariableDefinitionPayload,
  options?: ObjectWriteOptions
): Promise<VariableDto> {
  const params = new URLSearchParams({ path, name });
  return request(`/api/v1/objects/by-path/variables?${params}`, {
    method: "PATCH",
    headers: writeHeaders(options),
    body: JSON.stringify(payload),
  });
}

export function deleteVariable(path: string, name: string): Promise<void> {
  const params = new URLSearchParams({ path, name });
  return request(`/api/v1/objects/by-path/variables?${params}`, {
    method: "DELETE",
  });
}

export function fetchBindingRules(path: string): Promise<BindingRule[]> {
  const params = new URLSearchParams({ path });
  return request(`/api/v1/objects/by-path/binding-rules?${params}`);
}

export function saveBindingRules(path: string, rules: BindingRule[]): Promise<BindingRule[]> {
  const params = new URLSearchParams({ path });
  return request(`/api/v1/objects/by-path/binding-rules?${params}`, {
    method: "PUT",
    body: JSON.stringify(rules),
  });
}

export function deleteBindingRule(path: string, ruleId: string): Promise<BindingRule[]> {
  const params = new URLSearchParams({ path });
  return request(`/api/v1/objects/by-path/binding-rules/${encodeURIComponent(ruleId)}?${params}`, {
    method: "DELETE",
  });
}

export function upsertFunction(path: string, functionDescriptor: FunctionDescriptor): Promise<FunctionDescriptor> {
  const params = new URLSearchParams({ path });
  return request(`/api/v1/objects/by-path/functions?${params}`, {
    method: "PUT",
    body: JSON.stringify(functionDescriptor),
  });
}

export function deleteFunction(path: string, name: string): Promise<void> {
  const params = new URLSearchParams({ path, name });
  return request(`/api/v1/objects/by-path/functions?${params}`, {
    method: "DELETE",
  });
}

export function upsertEvent(path: string, event: EventDescriptor): Promise<EventDescriptor> {
  const params = new URLSearchParams({ path });
  return request(`/api/v1/objects/by-path/events?${params}`, {
    method: "PUT",
    body: JSON.stringify(event),
  });
}

export function deleteEvent(path: string, name: string): Promise<void> {
  const params = new URLSearchParams({ path, name });
  return request(`/api/v1/objects/by-path/events?${params}`, {
    method: "DELETE",
  });
}

export function fetchDashboard(path: string): Promise<DashboardView> {
  return request(`/api/v1/dashboards/by-path?path=${encodeURIComponent(path)}`);
}

export function fetchDashboardContext(path: string): Promise<import("./utils/dashboardContext").DashboardContextView> {
  return request(`/api/v1/dashboards/by-path/context?path=${encodeURIComponent(path)}`);
}

export function saveDashboardContext(
  path: string,
  context: import("./utils/dashboardContext").DashboardContextPatch,
  updatedBy?: string
): Promise<import("./utils/dashboardContext").DashboardContextView> {
  return request(`/api/v1/dashboards/by-path/context?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify({ context, updatedBy: updatedBy ?? null }),
  });
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

export async function saveDashboardRefreshInterval(
  path: string,
  refreshIntervalMs: number
): Promise<DashboardView> {
  await setVariable(path, "refreshIntervalMs", {
    schema: { name: "refreshIntervalMs", fields: [{ name: "value", type: "INTEGER" }] },
    rows: [{ value: refreshIntervalMs }],
  });
  return fetchDashboard(path);
}

export function fetchMimic(path: string): Promise<import("./types/dashboard").MimicView> {
  return request(`/api/v1/mimics/by-path?path=${encodeURIComponent(path)}`);
}

export function saveMimicDiagram(path: string, diagramJson: string): Promise<import("./types/dashboard").MimicView> {
  return request(`/api/v1/mimics/by-path/diagram?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify({ diagramJson }),
  });
}

export function saveMimicTitle(path: string, title: string): Promise<import("./types/dashboard").MimicView> {
  return request(`/api/v1/mimics/by-path/title?path=${encodeURIComponent(path)}`, {
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

export function updateWorkflowOperatorApp(path: string, operatorAppId: string): Promise<WorkflowView> {
  return request(`/api/v1/workflows/by-path/operator-app?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify({ operatorAppId }),
  });
}

export function runWorkflow(path: string, triggerObjectPath?: string): Promise<WorkflowView> {
  const params = new URLSearchParams({ path });
  if (triggerObjectPath?.trim()) {
    params.set("triggerObjectPath", triggerObjectPath.trim());
  }
  return request(`/api/v1/workflows/by-path/run?${params}`, {
    method: "POST",
  });
}

export interface CancelWorkflowResult {
  instanceId: string;
  status: string;
  cancelled: boolean;
  reason?: string;
  message?: string;
}

export function cancelWorkflowInstance(
  instanceId: string,
  payload?: { reason?: string; detailJson?: string; cancelledBy?: string }
): Promise<CancelWorkflowResult> {
  return request(`/api/v1/workflows/instances/${encodeURIComponent(instanceId)}/cancel`, {
    method: "POST",
    body: JSON.stringify(payload ?? {}),
  });
}

export interface SignalWorkflowResult {
  instanceId: string;
  signal: string;
  status: string;
}

export function signalWorkflowInstance(
  instanceId: string,
  payload: { signal: string; operatorId?: string }
): Promise<SignalWorkflowResult> {
  return request(`/api/v1/workflows/instances/${encodeURIComponent(instanceId)}/signal`, {
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

export interface CreateQueryPayload {
  queryId: string;
  displayName?: string;
  description?: string;
  queryType?: string;
  sourcePathPattern?: string;
  fieldsJson?: string;
  filterExpression?: string;
  enabled?: boolean;
}

export interface QueryRecord extends CreateQueryPayload {
  path: string;
}

export function createQuery(payload: CreateQueryPayload): Promise<QueryRecord> {
  return request("/api/v1/queries", {
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
