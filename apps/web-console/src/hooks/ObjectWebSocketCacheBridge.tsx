import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { applyObjectWebSocketMessage } from "./objectWebSocketCache";
import { OBJECT_WS_EVENT, type ObjectWsMessage } from "./objectWebSocketTypes";

/** Applies React Query invalidations for every object-tree WebSocket payload. */
export function ObjectWebSocketCacheBridge() {
  const queryClient = useQueryClient();

  useEffect(() => {
    const handler = (event: Event) => {
      const message = (event as CustomEvent<ObjectWsMessage>).detail;
      if (!message?.type || !message.path) {
        return;
      }
      applyObjectWebSocketMessage(queryClient, message);
    };
    window.addEventListener(OBJECT_WS_EVENT, handler);
    return () => window.removeEventListener(OBJECT_WS_EVENT, handler);
  }, [queryClient]);

  return null;
}
