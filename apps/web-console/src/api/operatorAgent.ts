import { getAuthHeaders, getStoredSession } from "../auth/session";
import { parseApiError } from "../utils/parseApiError";
import type { AiAgentChatResponse, AiAgentRunProgress, AiAgentStep } from "./ai";

export interface OperatorAgentStatus {
  appId: string;
  title: string;
  pathPrefixes: string[];
  provider: { available?: boolean; reason?: string };
  tools?: { name: string; description: string }[];
  memoryCount?: number;
  documentCount?: number;
}

export function fetchOperatorAgentStatus(appId: string): Promise<OperatorAgentStatus> {
  return fetch(`/api/v1/operator-apps/${encodeURIComponent(appId)}/agent/status`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Operator agent status failed: ${response.status}`));
    }
    return response.json();
  });
}

export function createOperatorAgentSession(appId: string): Promise<{ sessionId: string; title: string }> {
  return fetch(`/api/v1/operator-apps/${encodeURIComponent(appId)}/agent/sessions`, {
    method: "POST",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Create operator session failed: ${response.status}`));
    }
    return response.json();
  });
}

export function sendOperatorAgentMessage(
  appId: string,
  sessionId: string,
  message: string
): Promise<AiAgentChatResponse> {
  return fetch(
    `/api/v1/operator-apps/${encodeURIComponent(appId)}/agent/sessions/${encodeURIComponent(sessionId)}/messages`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ message }),
    }
  ).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Operator agent message failed: ${response.status}`));
    }
    return response.json();
  });
}

export function fetchOperatorAgentProgress(
  appId: string,
  sessionId: string
): Promise<AiAgentRunProgress> {
  return fetch(
    `/api/v1/operator-apps/${encodeURIComponent(appId)}/agent/sessions/${encodeURIComponent(sessionId)}/progress`,
    { headers: getAuthHeaders() }
  ).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Operator agent progress failed: ${response.status}`));
    }
    return response.json();
  });
}

export function subscribeOperatorAgentProgress(
  appId: string,
  sessionId: string,
  onProgress: (progress: AiAgentRunProgress) => void,
  onError?: (error: Event) => void
): () => void {
  const base = `/api/v1/operator-apps/${encodeURIComponent(appId)}/agent/sessions/${encodeURIComponent(sessionId)}/progress/stream`;
  const token = getStoredSession()?.token;
  const url = token ? `${base}?${new URLSearchParams({ token }).toString()}` : base;
  const source = new EventSource(url);

  source.addEventListener("progress", (event) => {
    try {
      onProgress(JSON.parse((event as MessageEvent).data) as AiAgentRunProgress);
    } catch {
      // ignore malformed payloads
    }
  });

  source.onerror = (error) => {
    onError?.(error);
  };

  return () => source.close();
}

export function cancelOperatorAgentRun(appId: string, sessionId: string): Promise<void> {
  return fetch(
    `/api/v1/operator-apps/${encodeURIComponent(appId)}/agent/sessions/${encodeURIComponent(sessionId)}/cancel`,
    { method: "POST", headers: getAuthHeaders() }
  ).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Cancel operator agent failed: ${response.status}`));
    }
  });
}

export type { AiAgentStep };
