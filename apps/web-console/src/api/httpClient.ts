import { getAuthHeaders, getStoredSession } from "../auth/session";
import { parseApiError } from "../utils/parseApiError";
import { invalidateStoredSession } from "../auth/validateSession";
import { fetchWithIngressFallback } from "../utils/ingressFetch";

let authFailureCheck: Promise<void> | null = null;

async function handlePossibleAuthFailure(status: number): Promise<void> {
  if (status !== 401 && status !== 403) {
    return;
  }
  const token = getStoredSession()?.token;
  if (!token) {
    return;
  }
  if (authFailureCheck) {
    await authFailureCheck;
    return;
  }
  authFailureCheck = (async () => {
    try {
      const response = await fetchWithIngressFallback("/api/v1/auth/me", {
        cache: "no-store",
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!response.ok) {
        invalidateStoredSession();
        return;
      }
      const me = (await response.json()) as { authenticated?: boolean };
      if (!me.authenticated) {
        invalidateStoredSession();
      }
    } catch {
      invalidateStoredSession();
    } finally {
      authFailureCheck = null;
    }
  })();
  await authFailureCheck;
}

type RequestOptions = RequestInit & { authToken?: string };

export async function request<T>(url: string, init?: RequestOptions): Promise<T> {
  const { authToken, headers: extraHeaders, ...fetchInit } = init ?? {};
  const authHeaders = authToken?.trim()
    ? { Authorization: `Bearer ${authToken.trim()}` }
    : getAuthHeaders();
  const response = await fetchWithIngressFallback(url, {
    ...fetchInit,
    cache: "no-store",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders,
      ...(extraHeaders ?? {}),
    },
  });
  if (!response.ok) {
    await handlePossibleAuthFailure(response.status);
    const text = await response.text();
    let message = parseApiError(text, `Request failed: ${response.status}`);
    try {
      const json = JSON.parse(text) as { error?: string; message?: string };
      if (json.error === "REVISION_CONFLICT") {
        message = `REVISION_CONFLICT:${text}`;
      }
    } catch {
      // keep parsed message
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

export interface ObjectWriteOptions {
  revision?: number;
  force?: boolean;
  authToken?: string;
}

export function writeHeaders(options?: ObjectWriteOptions): Record<string, string> {
  const headers: Record<string, string> = {};
  if (options?.revision != null) {
    headers["If-Match"] = String(options.revision);
  }
  if (options?.force) {
    headers["X-ISPF-Force"] = "true";
  }
  return headers;
}
