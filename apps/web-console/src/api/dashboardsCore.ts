import type { DashboardView } from "../types/dashboard";
import type { WorkflowLifecycleStatus, WorkflowView } from "../types/workflow";
import { request } from "./httpClient";
import { setVariable } from "./objectsCore";

export function fetchDashboard(path: string): Promise<DashboardView> {
  return request(`/api/v1/dashboards/by-path?path=${encodeURIComponent(path)}`);
}

export function fetchDashboardContext(path: string): Promise<import("../utils/dashboard/dashboardContext").DashboardContextView> {
  return request(`/api/v1/dashboards/by-path/context?path=${encodeURIComponent(path)}`);
}

export function saveDashboardContext(
  path: string,
  context: import("../utils/dashboard/dashboardContext").DashboardContextPatch,
  updatedBy?: string
): Promise<import("../utils/dashboard/dashboardContext").DashboardContextView> {
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

export function fetchDashboardLayoutTemplates(): Promise<string[]> {
  return request("/api/v1/dashboards/layout-templates");
}

export function applyDashboardLayoutTemplate(path: string, template: string): Promise<DashboardView> {
  return request(`/api/v1/dashboards/by-path/layout?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify({ template }),
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

export function fetchMimic(path: string): Promise<import("../types/dashboard").MimicView> {
  return request(`/api/v1/mimics/by-path?path=${encodeURIComponent(path)}`);
}

export function saveMimicDiagram(path: string, diagramJson: string): Promise<import("../types/dashboard").MimicView> {
  return request(`/api/v1/mimics/by-path/diagram?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify({ diagramJson }),
  });
}

export function saveMimicTitle(path: string, title: string): Promise<import("../types/dashboard").MimicView> {
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

export function runWorkflow(
  path: string,
  triggerObjectPath?: string,
  input?: Record<string, string>
): Promise<WorkflowView> {
  const params = new URLSearchParams({ path });
  if (triggerObjectPath?.trim()) {
    params.set("triggerObjectPath", triggerObjectPath.trim());
  }
  return request(`/api/v1/workflows/by-path/run?${params}`, {
    method: "POST",
    body: JSON.stringify({ input: input ?? {} }),
  });
}

export function fetchWorkflowRuns(path: string): Promise<import("../types/workflow").WorkflowRunSummary[]> {
  return request(`/api/v1/workflows/by-path/runs?path=${encodeURIComponent(path)}`);
}

export function fetchWorkflowSteps(
  instanceId: string
): Promise<import("../types/workflow").WorkflowStepSummary[]> {
  return request(`/api/v1/workflows/instances/${encodeURIComponent(instanceId)}/steps`);
}

export function invokeWorkflowTool(
  path: string,
  input?: Record<string, string>
): Promise<Record<string, unknown>> {
  return request(`/api/v1/workflows/by-path/invoke-tool?path=${encodeURIComponent(path)}`, {
    method: "POST",
    body: JSON.stringify({ input: input ?? {} }),
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

