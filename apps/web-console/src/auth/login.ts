import type { AuthSession } from "./session";
import { clearStoredSession, setStoredSession } from "./session";

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

export async function login(username: string, password: string): Promise<AuthSession> {
  const response = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
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
    await fetch("/api/v1/auth/logout", {
      method: "POST",
      headers: { Authorization: `Bearer ${session.token}` },
    }).catch(() => undefined);
  }
  clearStoredSession();
}
