import { getAuthHeaders } from "../auth/session";

export interface SecurityUserSummary {
  username: string;
  displayName: string;
  roles: string[];
  enabled: boolean;
  objectPath: string;
  autoStartEnabled?: boolean;
  autoStartApp?: string;
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

export function fetchSecurityUsers(): Promise<SecurityUserSummary[]> {
  return securityRequest("/api/v1/security/users");
}

export function createSecurityUser(payload: {
  username: string;
  displayName?: string;
  password: string;
  roles: string[];
}): Promise<SecurityUserSummary> {
  return securityRequest("/api/v1/security/users", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateSecurityUser(
  username: string,
  payload: {
    autoStartEnabled?: boolean;
    autoStartApp?: string | null;
    displayName?: string;
    roles?: string[];
    enabled?: boolean;
  }
): Promise<SecurityUserSummary> {
  return securityRequest(`/api/v1/security/users/${encodeURIComponent(username)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function setSecurityUserPassword(username: string, password: string): Promise<void> {
  return securityRequest(`/api/v1/security/users/${encodeURIComponent(username)}/password`, {
    method: "PUT",
    body: JSON.stringify({ password }),
  });
}
