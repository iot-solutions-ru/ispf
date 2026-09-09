import type { AuthMe } from "../api";
import { fetchWithIngressFallback, resetIngressRouteCache } from "../utils/ingress/ingressFetch";
import {
  clearStoredSession,
  getStoredSession,
  setStoredSession,
  type AuthSession,
} from "./session";

export const SESSION_INVALID_EVENT = "ispf-session-invalid";
export const SESSION_UPDATED_EVENT = "ispf-session-updated";

export function isSessionExpired(session: AuthSession | null): boolean {
  if (!session?.expiresAt) {
    return false;
  }
  const expiresAt = Date.parse(session.expiresAt);
  return Number.isFinite(expiresAt) && expiresAt <= Date.now();
}

export function invalidateStoredSession(): void {
  clearStoredSession();
  resetIngressRouteCache();
  window.dispatchEvent(new Event(SESSION_INVALID_EVENT));
}

/**
 * Returns session when token is present and accepted by /auth/me.
 * Clears storage on explicit auth rejection or expiry; keeps the last session
 * on offline/transient network errors so the operator PWA shell can remount (BL-151).
 */
export async function validateStoredSession(): Promise<AuthSession | null> {
  const session = getStoredSession();
  if (!session?.token?.trim()) {
    clearStoredSession();
    return null;
  }
  if (isSessionExpired(session)) {
    invalidateStoredSession();
    return null;
  }
  try {
    const response = await fetchWithIngressFallback("/api/v1/auth/me", {
      cache: "no-store",
      headers: { Authorization: `Bearer ${session.token}` },
    });
    if (response.status === 401 || response.status === 403) {
      invalidateStoredSession();
      return null;
    }
    if (!response.ok) {
      return session;
    }
    const me = (await response.json()) as AuthMe;
    if (!me.authenticated) {
      invalidateStoredSession();
      return null;
    }
    const nextRoles = Array.isArray(me.roles) ? me.roles : session.roles;
    const nextUsername = me.principal?.trim() ? me.principal.trim() : session.username;
    if (sameStringList(session.roles, nextRoles) && session.username === nextUsername) {
      return session;
    }
    const next: AuthSession = {
      ...session,
      username: nextUsername,
      roles: nextRoles,
    };
    setStoredSession(next);
    return next;
  } catch {
    return session;
  }
}

function sameStringList(left: string[], right: string[]): boolean {
  if (left.length !== right.length) {
    return false;
  }
  const a = [...left].sort();
  const b = [...right].sort();
  return a.every((value, index) => value === b[index]);
}
