import { useEffect, useState } from "react";
import { getStoredSession } from "../auth/session";
import { SESSION_INVALID_EVENT, SESSION_UPDATED_EVENT } from "../auth/validateSession";
import { isFederatedCatalogPath } from "../utils/federationPath";
import { preferDirectIngressRoute, resolveIngressWebSocketPaths } from "../utils/ingressFetch";
import { OBJECT_WS_EVENT, type ObjectWsMessage } from "./objectWebSocketTypes";

export type { ObjectWsMessage } from "./objectWebSocketTypes";
export { OBJECT_WS_EVENT } from "./objectWebSocketTypes";

/** Path-wide when `variables` is omitted/empty; otherwise only listed names. */
export interface ObjectVariableInterest {
  path: string;
  variables?: string[] | null;
}

let activeSocket: WebSocket | null = null;
let wsConnected = false;
let socketOwnerCount = 0;
let connectionToken = "";
let retryTimer: number | undefined;
const wsConnectionListeners = new Set<() => void>();

interface PathInterestState {
  pathWide: number;
  variables: Map<string, number>;
}

/** Ref-counted path/variable interests merged into one WS subscribe message. */
const interestByPath = new Map<string, PathInterestState>();

function setWsConnected(connected: boolean) {
  if (wsConnected === connected) {
    return;
  }
  wsConnected = connected;
  for (const listener of wsConnectionListeners) {
    listener();
  }
}

export function isObjectWebSocketConnected(): boolean {
  return wsConnected;
}

export function subscribeObjectWebSocketConnection(listener: () => void): () => void {
  wsConnectionListeners.add(listener);
  return () => wsConnectionListeners.delete(listener);
}

const WS_BEARER_PROTOCOL = "ispf-bearer";

let wsPathIndex = 0;
let wsPathCandidates: string[] = [];

function wsUrlAt(index: number): string {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const paths = wsPathCandidates.length > 0 ? wsPathCandidates : resolveIngressWebSocketPaths("/ws/objects");
  const path = paths[Math.min(index, paths.length - 1)] ?? "/ws/objects";
  return `${protocol}//${window.location.host}${path}`;
}

function openWebSocket(authToken: string, pathIndex: number): WebSocket {
  return new WebSocket(wsUrlAt(pathIndex), [WS_BEARER_PROTOCOL, authToken]);
}

function clearRetryTimer() {
  if (retryTimer !== undefined) {
    window.clearTimeout(retryTimer);
    retryTimer = undefined;
  }
}

function teardownSocket() {
  clearRetryTimer();
  if (activeSocket) {
    activeSocket.close();
    activeSocket = null;
  }
  setWsConnected(false);
}

function connectWebSocket(authToken: string, tryNextIngressPath = false) {
  if (
    activeSocket
    && connectionToken === authToken
    && !tryNextIngressPath
    && (activeSocket.readyState === WebSocket.OPEN || activeSocket.readyState === WebSocket.CONNECTING)
  ) {
    return;
  }

  if (!tryNextIngressPath) {
    wsPathCandidates = resolveIngressWebSocketPaths("/ws/objects");
    wsPathIndex = 0;
  }

  teardownSocket();
  connectionToken = authToken;
  const socket = openWebSocket(authToken, wsPathIndex);
  activeSocket = socket;
  let opened = false;

  socket.onopen = () => {
    opened = true;
    setWsConnected(true);
    pushMergedSubscriptionsToServer();
  };

  socket.onmessage = (event) => {
    try {
      const message = JSON.parse(event.data) as ObjectWsMessage;
      window.dispatchEvent(new CustomEvent(OBJECT_WS_EVENT, { detail: message }));

      if (message.type === "CREATED" || message.type === "UPDATED" || message.type === "DELETED") {
        window.dispatchEvent(new CustomEvent("ispf-tree-structure-change", { detail: message }));
      }
    } catch {
      // ignore malformed messages
    }
  };

  socket.onclose = () => {
    if (activeSocket === socket) {
      activeSocket = null;
    }
    setWsConnected(false);
    if (!opened && wsPathIndex + 1 < wsPathCandidates.length) {
      wsPathIndex += 1;
      preferDirectIngressRoute();
      connectWebSocket(authToken, true);
      return;
    }
    if (socketOwnerCount > 0 && connectionToken === authToken) {
      clearRetryTimer();
      retryTimer = window.setTimeout(() => connectWebSocket(authToken), 3000);
    }
  };
}

function ensureInterestState(path: string): PathInterestState {
  let state = interestByPath.get(path);
  if (!state) {
    state = { pathWide: 0, variables: new Map() };
    interestByPath.set(path, state);
  }
  return state;
}

function pruneInterestState(path: string, state: PathInterestState) {
  if (state.pathWide <= 0 && state.variables.size === 0) {
    interestByPath.delete(path);
  }
}

/** Build server subscribe payload from merged refcounts. */
export function buildSubscribePayload(): {
  type: "subscribe";
  paths: string[];
  variablesByPath: Record<string, string[]>;
} {
  const paths: string[] = [];
  const variablesByPath: Record<string, string[]> = {};
  for (const [path, state] of interestByPath) {
    paths.push(path);
    if (state.pathWide > 0) {
      continue;
    }
    const names = [...state.variables.keys()].sort();
    if (names.length > 0) {
      variablesByPath[path] = names;
    }
  }
  paths.sort();
  return { type: "subscribe", paths, variablesByPath };
}

function pushMergedSubscriptionsToServer() {
  if (!activeSocket || activeSocket.readyState !== WebSocket.OPEN) {
    return;
  }
  activeSocket.send(JSON.stringify(buildSubscribePayload()));
}

/** @deprecated Prefer trackObjectVariableSubscriptions / useObjectPathsSubscription. */
export function subscribeObjectPaths(paths: string[]) {
  interestByPath.clear();
  trackObjectVariableSubscriptions(paths.map((path) => ({ path })));
}

/**
 * Register interest in object paths / variables; merged with other dashboard consumers.
 * Omit `variables` (or pass empty) for path-wide interest (object explorer).
 */
export function trackObjectVariableSubscriptions(interests: ObjectVariableInterest[]): () => void {
  const normalized: Array<{ path: string; variables: string[] | null }> = [];
  for (const interest of interests) {
    const path = interest.path?.trim();
    if (!path) {
      continue;
    }
    const raw = interest.variables;
    if (!raw || raw.length === 0) {
      normalized.push({ path, variables: null });
      continue;
    }
    const variables = [...new Set(raw.map((name) => name.trim()).filter(Boolean))];
    if (variables.length === 0) {
      normalized.push({ path, variables: null });
    } else {
      normalized.push({ path, variables });
    }
  }

  for (const entry of normalized) {
    const state = ensureInterestState(entry.path);
    if (entry.variables == null) {
      state.pathWide += 1;
    } else {
      for (const variable of entry.variables) {
        state.variables.set(variable, (state.variables.get(variable) ?? 0) + 1);
      }
    }
  }
  pushMergedSubscriptionsToServer();

  return () => {
    for (const entry of normalized) {
      const state = interestByPath.get(entry.path);
      if (!state) {
        continue;
      }
      if (entry.variables == null) {
        state.pathWide = Math.max(0, state.pathWide - 1);
      } else {
        for (const variable of entry.variables) {
          const next = (state.variables.get(variable) ?? 1) - 1;
          if (next <= 0) {
            state.variables.delete(variable);
          } else {
            state.variables.set(variable, next);
          }
        }
      }
      pruneInterestState(entry.path, state);
    }
    pushMergedSubscriptionsToServer();
  };
}

/** Path-wide interest (legacy). */
export function trackObjectPathSubscriptions(paths: string[]): () => void {
  return trackObjectVariableSubscriptions(paths.map((path) => ({ path })));
}

export function useObjectPathsSubscription(paths: string[]) {
  const pathsKey = paths
    .filter((path) => path.trim().length > 0)
    .sort()
    .join("\0");

  useEffect(() => {
    if (!pathsKey) {
      return;
    }
    const unique = pathsKey.split("\0");
    return trackObjectPathSubscriptions(unique);
  }, [pathsKey]);
}

export function useObjectVariableSubscriptions(interests: ObjectVariableInterest[]) {
  const key = interests
    .map((interest) => {
      const path = interest.path?.trim() ?? "";
      if (!path) {
        return "";
      }
      const variables = interest.variables?.map((name) => name.trim()).filter(Boolean).sort() ?? [];
      return variables.length === 0 ? `${path}\0*` : `${path}\0${variables.join(",")}`;
    })
    .filter(Boolean)
    .sort()
    .join("|");

  useEffect(() => {
    if (!key) {
      return;
    }
    const parsed = key.split("|").map((entry) => {
      const [path, variablesPart] = entry.split("\0");
      if (variablesPart === "*") {
        return { path, variables: null as string[] | null };
      }
      return { path, variables: variablesPart.split(",").filter(Boolean) };
    });
    return trackObjectVariableSubscriptions(parsed);
  }, [key]);
}

export function sendPresence(path: string, username: string, mode: "view" | "edit") {
  if (!activeSocket || activeSocket.readyState !== WebSocket.OPEN) {
    return;
  }
  activeSocket.send(JSON.stringify({ type: "presence", path, username, mode }));
}

export function useObjectWebSocket(enabled = true) {
  const [authToken, setAuthToken] = useState(() => getStoredSession()?.token ?? "");

  useEffect(() => {
    const syncToken = () => setAuthToken(getStoredSession()?.token ?? "");
    window.addEventListener(SESSION_UPDATED_EVENT, syncToken);
    window.addEventListener(SESSION_INVALID_EVENT, syncToken);
    return () => {
      window.removeEventListener(SESSION_UPDATED_EVENT, syncToken);
      window.removeEventListener(SESSION_INVALID_EVENT, syncToken);
    };
  }, []);

  useEffect(() => {
    if (!enabled || !authToken) {
      return;
    }

    socketOwnerCount++;
    connectWebSocket(authToken);

    return () => {
      socketOwnerCount--;
      if (socketOwnerCount <= 0) {
        connectionToken = "";
        teardownSocket();
      }
    };
  }, [enabled, authToken]);
}

/** Subscribe WebSocket to a federated object/dashboard path for background refresh. */
export function useFederatedPathSubscription(path: string | null | undefined) {
  useEffect(() => {
    if (!path || !isFederatedCatalogPath(path)) {
      return;
    }
    return trackObjectPathSubscriptions([path]);
  }, [path]);
}
