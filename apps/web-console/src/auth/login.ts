import type { AuthSession } from "./session";
import { clearStoredSession, setStoredSession } from "./session";
import { fetchWithIngressFallback, resetIngressRouteCache } from "../utils/ingress/ingressFetch";

export interface LoginResponse {
  token: string;
  username: string;
  displayName: string;
  roles: string[];
  expiresAt?: string;
  autoStartEnabled?: boolean;
  autoStartApp?: string;
  tenantId?: string;
}

export async function login(
  username: string,
  password: string,
  totpCode?: string
): Promise<AuthSession> {
  const body: { username: string; password: string; totpCode?: string } = { username, password };
  if (totpCode != null && totpCode.trim() !== "") {
    body.totpCode = totpCode.trim();
  }
  const response = await fetchWithIngressFallback("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || "Login failed");
  }
  const data = (await response.json()) as LoginResponse;
  const session: AuthSession = {
    token: data.token,
    username: data.username,
    displayName: data.displayName,
    roles: data.roles,
    expiresAt: data.expiresAt,
    autoStartEnabled: data.autoStartEnabled === true,
    autoStartApp: data.autoStartApp,
    tenantId: data.tenantId,
  };
  setStoredSession(session);
  return session;
}

export async function logout(): Promise<void> {
  const session = JSON.parse(localStorage.getItem("ispf-auth-session") ?? "null") as AuthSession | null;
  if (session?.token) {
    await fetchWithIngressFallback("/api/v1/auth/logout", {
      method: "POST",
      headers: { Authorization: `Bearer ${session.token}` },
    }).catch(() => undefined);
  }
  clearStoredSession();
  resetIngressRouteCache();
}
