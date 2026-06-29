import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getStoredSession } from "../auth/session";
import { SESSION_INVALID_EVENT, SESSION_UPDATED_EVENT } from "../auth/validateSession";
import { isFederatedCatalogPath } from "../utils/federationPath";
import { refreshWorkQueue } from "./workQueueCache";

export interface ObjectWsMessage {
  type: "CREATED" | "UPDATED" | "DELETED" | "VARIABLE_UPDATED" | "EVENT_FIRED" | "presence";
  path: string;
  variableName: string;
  timestamp: string;
  revision?: number;
  changedBy?: string;
}

export const OBJECT_WS_EVENT = "ispf-object-ws-message";

let activeSocket: WebSocket | null = null;
let wsConnected = false;
const wsConnectionListeners = new Set<() => void>();
/** Ref-counted path interests merged into one WS subscribe message. */
const pathRefCounts = new Map<string, number>();

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

function wsUrl(): string {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const base = `${protocol}//${window.location.host}/ws/objects`;
  const token = getStoredSession()?.token;
  if (!token) {
    return base;
  }
  const params = new URLSearchParams({ token });
  return `${base}?${params.toString()}`;
}

function pushMergedSubscriptionsToServer() {
  if (!activeSocket || activeSocket.readyState !== WebSocket.OPEN) {
    return;
  }
  const paths = [...pathRefCounts.keys()];
  activeSocket.send(JSON.stringify({ type: "subscribe", paths }));
}

/** @deprecated Prefer trackObjectPathSubscriptions / useObjectPathsSubscription. */
export function subscribeObjectPaths(paths: string[]) {
  pathRefCounts.clear();
  for (const path of paths) {
    if (path.trim().length > 0) {
      pathRefCounts.set(path.trim(), 1);
    }
  }
  pushMergedSubscriptionsToServer();
}

/** Register interest in object paths; merged with other dashboard/inspector consumers. */
export function trackObjectPathSubscriptions(paths: string[]): () => void {
  const unique = [...new Set(paths.filter((path) => path.trim().length > 0))];
  for (const path of unique) {
    pathRefCounts.set(path, (pathRefCounts.get(path) ?? 0) + 1);
  }
  pushMergedSubscriptionsToServer();
  return () => {
    for (const path of unique) {
      const next = (pathRefCounts.get(path) ?? 1) - 1;
      if (next <= 0) {
        pathRefCounts.delete(path);
      } else {
        pathRefCounts.set(path, next);
      }
    }
    pushMergedSubscriptionsToServer();
  };
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

export function sendPresence(path: string, username: string, mode: "view" | "edit") {
  if (!activeSocket || activeSocket.readyState !== WebSocket.OPEN) {
    return;
  }
  activeSocket.send(JSON.stringify({ type: "presence", path, username, mode }));
}

export function useObjectWebSocket(enabled = true) {
  const queryClient = useQueryClient();
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
    let active = true;
    let socket: WebSocket | null = null;
    let retryTimer: number | undefined;

    const connect = () => {
      if (!active) return;
      socket = new WebSocket(wsUrl());
      activeSocket = socket;

      socket.onopen = () => {
        setWsConnected(true);
        pushMergedSubscriptionsToServer();
      };

      socket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data) as ObjectWsMessage;
          window.dispatchEvent(new CustomEvent(OBJECT_WS_EVENT, { detail: message }));

          switch (message.type) {
            case "CREATED":
            case "UPDATED":
            case "DELETED":
              window.dispatchEvent(new CustomEvent("ispf-tree-structure-change", { detail: message }));
              queryClient.invalidateQueries({ queryKey: ["objects"] });
              queryClient.invalidateQueries({ queryKey: ["object", message.path] });
              queryClient.invalidateQueries({ queryKey: ["object-editor", message.path] });
              break;
            case "VARIABLE_UPDATED":
              queryClient.invalidateQueries({ queryKey: ["variables", message.path] });
              queryClient.invalidateQueries({ queryKey: ["variables-batch"] });
              queryClient.invalidateQueries({ queryKey: ["events", message.path] });
              queryClient.invalidateQueries({ queryKey: ["events", "all"] });
              queryClient.invalidateQueries({ queryKey: ["events", "operator-sidebar"] });
              if (message.variableName === "driverStatus") {
                queryClient.invalidateQueries({ queryKey: ["objects"] });
                queryClient.invalidateQueries({ queryKey: ["driver-status", message.path] });
              }
              break;
            case "EVENT_FIRED":
              queryClient.invalidateQueries({ queryKey: ["events", message.path] });
              queryClient.invalidateQueries({ queryKey: ["events", "all"] });
              queryClient.invalidateQueries({ queryKey: ["events", "operator-sidebar"] });
              break;
            case "presence":
              break;
          }

          if (message.type === "UPDATED" || message.type === "DELETED") {
            queryClient.invalidateQueries({ queryKey: ["dashboard", message.path] });
            queryClient.invalidateQueries({ queryKey: ["workflow", message.path] });
          }
          if (message.type === "UPDATED") {
            void refreshWorkQueue(queryClient);
          }
          if (message.type === "VARIABLE_UPDATED" || message.type === "EVENT_FIRED") {
            void refreshWorkQueue(queryClient);
          }
        } catch {
          // ignore malformed messages
        }
      };

      socket.onclose = () => {
        setWsConnected(false);
        if (activeSocket === socket) {
          activeSocket = null;
        }
        if (active) {
          retryTimer = window.setTimeout(connect, 3000);
        }
      };
    };

    connect();

    return () => {
      active = false;
      setWsConnected(false);
      if (retryTimer) {
        window.clearTimeout(retryTimer);
      }
      if (activeSocket === socket) {
        activeSocket = null;
      }
      socket?.close();
    };
  }, [queryClient, enabled, authToken]);
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
