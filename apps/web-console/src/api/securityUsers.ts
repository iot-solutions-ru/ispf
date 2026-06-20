import { getAuthHeaders } from "../auth/session";
import { parseApiError } from "../utils/parseApiError";

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
    const message = parseApiError(text, `Request failed: ${response.status}`);
    if (response.status === 403 && url.includes("federation-token")) {
      throw new Error(
        `${message}. Endpoint не найден или доступ запрещён — перезапустите ispf-server с последней сборкой и войдите как admin.`
      );
    }
    if (response.status === 403) {
      throw new Error(`${message}. Проверьте, что вы вошли как admin и сессия не истекла.`);
    }
    throw new Error(message);
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

export interface FederationTokenResponse {
  token: string;
  expiresAt?: string;
  username?: string;
  roles?: string[];
  purpose?: string;
  ttlHours?: number;
}

export function issueFederationToken(username: string, ttlHours?: number): Promise<FederationTokenResponse> {
  return securityRequest(`/api/v1/security/users/${encodeURIComponent(username)}/federation-token`, {
    method: "POST",
    body: JSON.stringify(ttlHours != null ? { ttlHours } : {}),
  });
}
