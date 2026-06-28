import { getAuthHeaders } from "../auth/session";

export interface NatsHealth {
  enabled: boolean;
  connected: boolean;
  url: string | null;
  replicaId: string;
  replicaEventsEnabled: boolean;
  jetStreamEnabled: boolean;
  jetStreamActive: boolean;
  streamName: string | null;
  streamReady: boolean;
  streamMessages: number | null;
  streamBytes: number | null;
  consumerDurable: string | null;
  consumerPending: number | null;
  publishNatsAvailable: boolean;
  connectionError: string | null;
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchNatsHealth(): Promise<NatsHealth> {
  return fetch("/api/v1/platform/nats/health", {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<NatsHealth>(response));
}
