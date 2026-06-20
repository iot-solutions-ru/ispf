import { getAuthHeaders } from "../auth/session";

export interface FederationPeer {
  id: string;
  name: string;
  baseUrl: string;
  pathPrefix: string;
  enabled: boolean;
  description: string | null;
  hasAuthToken: boolean;
}

export interface FederationPeerPayload {
  name: string;
  baseUrl: string;
  authToken?: string;
  pathPrefix?: string;
  enabled?: boolean;
  description?: string;
}

export function fetchFederationPeers(): Promise<FederationPeer[]> {
  return fetch("/api/v1/federation/peers", { headers: getAuthHeaders() }).then(async (response) => {
    if (!response.ok) {
      throw new Error(`Federation peers failed: ${response.status}`);
    }
    return response.json();
  });
}

export function createFederationPeer(payload: FederationPeerPayload): Promise<FederationPeer> {
  return fetch("/api/v1/federation/peers", {
    method: "POST",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(`Create peer failed: ${response.status}`);
    }
    return response.json();
  });
}

export function deleteFederationPeer(id: string): Promise<void> {
  return fetch(`/api/v1/federation/peers/${encodeURIComponent(id)}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then((response) => {
    if (!response.ok) {
      throw new Error(`Delete peer failed: ${response.status}`);
    }
  });
}

export function syncFederationCatalog(peerId: string): Promise<{
  localRoot: string;
  created: number;
  updated: number;
  remoteCount: number;
}> {
  return fetch(`/api/v1/federation/peers/${encodeURIComponent(peerId)}/sync-catalog`, {
    method: "POST",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Sync catalog failed: ${response.status}`);
    }
    return response.json();
  });
}

export function probeFederationObject(peerId: string, path: string): Promise<Record<string, unknown>> {
  const params = new URLSearchParams({ peerId, path });
  return fetch(`/api/v1/federation/proxy/objects/by-path?${params.toString()}`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Proxy failed: ${response.status}`);
    }
    return response.json();
  });
}
