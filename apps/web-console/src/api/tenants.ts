import { getAuthHeaders } from "../auth/session";

export interface TenantSummary {
  tenantId: string;
  displayName: string;
  enabled: boolean;
  objectPath: string;
  platformPath: string;
}

export interface CreateTenantPayload {
  tenantId: string;
  displayName: string;
  enabled?: boolean;
}

export function fetchTenants(): Promise<TenantSummary[]> {
  return fetch("/api/v1/tenants", { headers: getAuthHeaders() }).then(async (response) => {
    if (!response.ok) {
      throw new Error(`Tenants failed: ${response.status}`);
    }
    return response.json();
  });
}

export function createTenant(payload: CreateTenantPayload): Promise<TenantSummary> {
  return fetch("/api/v1/tenants", {
    method: "POST",
    headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(`Create tenant failed: ${response.status}`);
    }
    return response.json();
  });
}

export function assignTenantUser(tenantId: string, username: string): Promise<void> {
  return fetch(`/api/v1/tenants/${encodeURIComponent(tenantId)}/users/${encodeURIComponent(username)}`, {
    method: "PUT",
    headers: getAuthHeaders(),
  }).then((response) => {
    if (!response.ok) {
      throw new Error(`Assign tenant user failed: ${response.status}`);
    }
  });
}
