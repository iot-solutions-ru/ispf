import type {
  ObjectSummary,
  CreateObjectPayload,
  DataRecord,
  DataSchema,
  UpdateObjectPayload,
  VariableDto,
  ObjectEditorDto,
  FunctionDescriptor,
  EventDescriptor,
  BindingRule,
} from "../types";
import { getAuthHeaders } from "../auth/session";
import { fetchWithIngressFallback } from "../utils/ingressFetch";
import { request, writeHeaders, type ObjectWriteOptions } from "./httpClient";

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

export interface AnalyticsTagEvaluateResult {
  tagPath: string;
  helper: string;
  status: string;
  outputs: Record<string, number>;
  message: string | null;
  latencyMs: number;
}

export function evaluateAnalyticsTag(
  path: string,
  asOf?: string
): Promise<AnalyticsTagEvaluateResult> {
  const params = new URLSearchParams({ path });
  if (asOf) {
    params.set("asOf", asOf);
  }
  return request(`/api/v1/platform/analytics/tags/evaluate?${params.toString()}`);
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
  const response = await fetchWithIngressFallback(`/api/v1/platform/analytics/query/export?${params}`, {
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

  const response = await fetchWithIngressFallback(
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
  telemetryPublishMode?: string | null;
  historySampleMode?: string | null;
  includePreviousValueInEvent?: boolean;
  storageMode?: string | null;
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

