import { getAuthHeaders } from "../auth/session";

export type ClusterNodeStatus = "UP" | "STALE" | "DOWN";

export interface ClusterNode {
  replicaId: string;
  status: ClusterNodeStatus;
  version: string | null;
  environment: string | null;
  javaVersion: string | null;
  startedAt: string | null;
  lastHeartbeatAt: string | null;
  heldDriverLocks: number;
  self: boolean;
}

export interface ClusterHealth {
  clusterEnabled: boolean;
  driverOwnershipEnabled: boolean;
  replicaId: string;
  heldDriverLocks: number;
  heldDevicePaths: string[];
  driverLockTtlSeconds: number;
  natsEnabled: boolean;
  natsReplicaEventsEnabled: boolean;
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
