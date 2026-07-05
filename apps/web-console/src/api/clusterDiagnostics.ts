import { getAuthHeaders } from "../auth/session";

export type DiagnosticsSuspectKind =
  | "subsystem"
  | "driver"
  | "thread"
  | "job"
  | "workflow";

export type DiagnosticsSeverity = "critical" | "warning" | "info";

export interface DiagnosticsSuspect {
  id: string;
  kind: DiagnosticsSuspectKind;
  severity: DiagnosticsSeverity;
  title: string;
  detail: string;
  ref: string;
  score: number;
}

export interface DiagnosticsTopSuspect {
  kind: DiagnosticsSuspectKind | string;
  title: string;
  detail: string;
  ref?: string;
  replicaId?: string;
}

export interface DriverDiagnosticsRow {
  devicePath: string;
  driverId: string;
  pollIntervalMs: number;
  telemetryPublishMode?: string;
  connected: boolean;
  lastError: string | null;
  ingressEnabled?: boolean;
  ingressPending?: number;
  ingressCoalesced?: number;
  ingressEvicted?: number;
  ingressWorkers?: number;
  pressureScore: number;
}

export interface ThreadGroupRow {
  prefix: string;
  threadCount: number;
  cpuPercentDelta: number;
}

export interface ThreadRow {
  name: string;
  cpuPercentDelta: number;
}

export interface DiagnosticsDetail {
  threadGroups?: ThreadGroupRow[];
  topThreads?: ThreadRow[];
  drivers?: DriverDiagnosticsRow[];
  runningJobs?: Record<string, unknown>[];
  runningWorkflows?: Record<string, unknown>[];
  ioPollQueueSize?: number;
  queues?: Record<string, unknown>;
}

export interface PlatformDiagnostics {
  replicaId: string;
  replicaProfile: string;
  serverPort: number;
  processCpuPercent: number;
  systemCpuPercent: number | null;
  heapUsedPercent: number | null;
  pressureScore: number;
  topSuspect?: DiagnosticsTopSuspect;
  suspects: DiagnosticsSuspect[];
  detail: DiagnosticsDetail;
}

export interface ClusterDiagnosticsNode {
  replicaId: string;
  replicaProfile: string | null;
  status: string;
  httpPort: number | null;
  self: boolean;
  reachable: boolean;
  error?: string;
  processCpuPercent: number;
  systemCpuPercent: number | null;
  heapUsedPercent: number | null;
  pressureScore: number;
  topSuspect?: DiagnosticsTopSuspect;
  suspects: DiagnosticsSuspect[];
  detail: DiagnosticsDetail;
  queues: Record<string, unknown>;
}

export interface ClusterDiagnosticsResponse {
  timestamp: string;
  nodes: ClusterDiagnosticsNode[];
  clusterTopSuspect: DiagnosticsTopSuspect & { replicaId?: string };
}

export function fetchClusterDiagnostics(): Promise<ClusterDiagnosticsResponse> {
  return fetch("/api/v1/platform/cluster/diagnostics", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Request failed: ${response.status}`);
    }
    return response.json();
  });
}
