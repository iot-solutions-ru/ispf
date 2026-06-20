import { getAuthHeaders } from "../auth/session";

export interface SecurityRoleSummary {
  name: string;
  displayName: string;
  description: string;
  builtIn: boolean;
  objectPath: string;
  createdAt: string;
  updatedAt: string;
}

async function securityRequest<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
      ...init?.headers,
    },
    ...init,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchSecurityRoles(): Promise<SecurityRoleSummary[]> {
  return securityRequest("/api/v1/security/roles");
}

export function createSecurityRole(payload: {
  name: string;
  displayName?: string;
  description?: string;
}): Promise<SecurityRoleSummary> {
  return securityRequest("/api/v1/security/roles", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateSecurityRole(
  name: string,
  payload: {
    displayName?: string;
    description?: string;
  }
): Promise<SecurityRoleSummary> {
  return securityRequest(`/api/v1/security/roles/${encodeURIComponent(name)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}
