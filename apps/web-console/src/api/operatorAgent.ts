import { getAuthHeaders } from "../auth/session";
import { parseApiError } from "../utils/ui/parseApiError";
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
  message: string,
  uiLocale?: string | null
): Promise<AiAgentChatResponse> {
  return fetch(
    `/api/v1/operator-apps/${encodeURIComponent(appId)}/agent/sessions/${encodeURIComponent(sessionId)}/messages`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify({
        message,
        ...(uiLocale ? { uiLocale } : {}),
      }),
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
  // EventSource cannot send Authorization; poll Bearer-authenticated progress instead.
  let cancelled = false;
  let requestSeq = 0;

  const tick = () => {
    if (cancelled) {
      return;
    }
    const seq = ++requestSeq;
    void fetchOperatorAgentProgress(appId, sessionId)
      .then((progress) => {
        if (!cancelled && seq === requestSeq) {
          onProgress(progress);
        }
      })
      .catch(() => {
        if (!cancelled && seq === requestSeq) {
          onError?.(new Event("error"));
        }
      });
  };

  tick();
  const timer = setInterval(tick, 500);
  return () => {
    cancelled = true;
    clearInterval(timer);
  };
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
