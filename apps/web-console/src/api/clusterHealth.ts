import { getAuthHeaders } from "../auth/session";

export type ClusterNodeStatus = "UP" | "STALE" | "DOWN";

/** @deprecated Use ClusterReplicaProfile */
export type ClusterReplicaRole = "all" | "api" | "worker" | "io" | "hmi-read";

export type ClusterReplicaProfile =
  | "unified"
  | "edge-api"
  | "hmi-read"
  | "io"
  | "compute";

export interface ClusterNode {
  replicaId: string;
  status: ClusterNodeStatus;
  version: string | null;
  environment: string | null;
  javaVersion: string | null;
  /** Deprecated ADR-0031 alias */
  replicaRole: ClusterReplicaRole | string | null;
  replicaProfile: ClusterReplicaProfile | string | null;
  replicaCapabilities: string | null;
  httpPort?: number | null;
  startedAt: string | null;
  lastHeartbeatAt: string | null;
  heldDriverLocks: number;
  self: boolean;
}

export interface ClusterHealth {
  clusterEnabled: boolean;
  driverOwnershipEnabled: boolean;
  replicaProfile: ClusterReplicaProfile | string;
  /** Deprecated ADR-0031 alias */
  replicaRole: ClusterReplicaRole | string;
  replicaCapabilities: string[];
  jobConsumerActive: boolean;
  replicaId: string;
  heldDriverLocks: number;
  heldDevicePaths: string[];
  driverLockTtlSeconds: number;
  natsEnabled: boolean;
  natsReplicaEventsEnabled: boolean;
  liveVariableSyncEnabled: boolean;
  liveVariableSyncCoalesceMs: number;
  clusterPathInterestEnabled: boolean;
  nodes: ClusterNode[];
  nodesUp: number;
  nodesTotal: number;
  timestamp: string;
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchClusterHealth(): Promise<ClusterHealth> {
  return fetch("/api/v1/platform/cluster/health", {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<ClusterHealth>(response));
}
