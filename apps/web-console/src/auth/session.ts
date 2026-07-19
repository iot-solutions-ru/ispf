export interface AuthSession {
  token: string;
  username: string;
  displayName: string;
  roles: string[];
  expiresAt?: string;
  /** When true, web console opens operator app instead of admin shell. */
  autoStartEnabled?: boolean;
  /** Operator manifest app id (e.g. demo, oil-terminal). */
  autoStartApp?: string;
  /** Tenant namespace for scoped operators (root.tenant.{id}.*). */
  tenantId?: string;
}

const SESSION_KEY = "ispf-auth-session";

/** In-memory copy — stays in sync with sessionStorage (avoids race after clear). */
let cachedSession: AuthSession | null | undefined;

function webStorage(): Storage | null {
  if (typeof sessionStorage !== "undefined") {
    return sessionStorage;
  }
  if (typeof localStorage !== "undefined") {
    return localStorage;
  }
  return null;
}

function readSessionFromStorage(): AuthSession | null {
  const storage = webStorage();
  if (!storage) {
    return null;
  }
  let raw = storage.getItem(SESSION_KEY);
  if (!raw && typeof sessionStorage !== "undefined" && typeof localStorage !== "undefined") {
    // One-time migration from legacy localStorage (XSS window reduced vs keeping both).
    raw = localStorage.getItem(SESSION_KEY);
    if (raw) {
      sessionStorage.setItem(SESSION_KEY, raw);
      localStorage.removeItem(SESSION_KEY);
    }
  }
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    return null;
  }
}

export function getStoredSession(): AuthSession | null {
  if (cachedSession !== undefined) {
    return cachedSession;
  }
  cachedSession = readSessionFromStorage();
  return cachedSession;
}

export function setStoredSession(session: AuthSession): void {
  cachedSession = session;
  const payload = JSON.stringify(session);
  if (typeof sessionStorage !== "undefined") {
    sessionStorage.setItem(SESSION_KEY, payload);
  } else if (typeof localStorage !== "undefined") {
    localStorage.setItem(SESSION_KEY, payload);
  }
  if (typeof localStorage !== "undefined") {
    localStorage.removeItem(SESSION_KEY);
  }
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event("ispf-session-updated"));
  }
}

export function clearStoredSession(): void {
  cachedSession = null;
  if (typeof sessionStorage !== "undefined") {
    sessionStorage.removeItem(SESSION_KEY);
  }
  if (typeof localStorage !== "undefined") {
    localStorage.removeItem(SESSION_KEY);
  }
}

export function getAuthHeaders(): Record<string, string> {
  const session = getStoredSession();
  if (session?.token) {
    return { Authorization: `Bearer ${session.token}` };
  }
  return {};
}

export function getPrimaryRole(
  session: AuthSession | null
): "admin" | "tenant-admin" | "developer" | "operator" | null {
  if (!session) {
    return null;
  }
  if (session.roles.includes("admin")) {
    return "admin";
  }
  if (session.roles.includes("tenant-admin")) {
    return "tenant-admin";
  }
  if (session.roles.includes("developer")) {
    return "developer";
  }
  if (session.roles.includes("operator")) {
    return "operator";
  }
  return null;
}

/** Mirrors backend IspfRoles.isConfigurator — can open admin shell / configure objects. */
export function isConfiguratorSession(session: AuthSession | null): boolean {
  if (!session) {
    return false;
  }
  return (
    session.roles.includes("admin")
    || session.roles.includes("developer")
    || session.roles.includes("tenant-admin")
  );
}

/** Global platform admin only (create tenants, federation, system). */
export function isAdminSession(session: AuthSession | null): boolean {
  return session?.roles.includes("admin") ?? false;
}

export function isTenantAdminSession(session: AuthSession | null): boolean {
  return session?.roles.includes("tenant-admin") ?? false;
}

/** Global admin or tenant-admin — Security users/roles for own scope. */
export function canManageTenantSecurity(session: AuthSession | null): boolean {
  return isAdminSession(session) || isTenantAdminSession(session);
}
