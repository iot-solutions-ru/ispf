import { useEffect, useState } from "react";
import { getStoredSession } from "../auth/session";
import { SESSION_INVALID_EVENT, SESSION_UPDATED_EVENT } from "../auth/validateSession";
import { isFederatedCatalogPath } from "../utils/federationPath";
import { OBJECT_WS_EVENT, type ObjectWsMessage } from "./objectWebSocketTypes";

export type { ObjectWsMessage } from "./objectWebSocketTypes";
export { OBJECT_WS_EVENT } from "./objectWebSocketTypes";

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

          if (message.type === "CREATED" || message.type === "UPDATED" || message.type === "DELETED") {
            window.dispatchEvent(new CustomEvent("ispf-tree-structure-change", { detail: message }));
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
