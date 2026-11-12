import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { applyObjectWebSocketMessage } from "./objectWebSocketCache";
import { OBJECT_WS_EVENT, type ObjectWsMessage } from "./objectWebSocketTypes";
import { OBJECT_WS_GAP_EVENT } from "./useObjectWebSocket";

/** Applies React Query cache patches for every object-tree WebSocket payload. */
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
    const onGap = () => {
      void queryClient.invalidateQueries({ queryKey: ["variables"] });
      void queryClient.invalidateQueries({ queryKey: ["variables-batch"] });
      void queryClient.invalidateQueries({ queryKey: ["objects"] });
      void queryClient.invalidateQueries({ queryKey: ["events"] });
    };
    window.addEventListener(OBJECT_WS_EVENT, handler);
    window.addEventListener(OBJECT_WS_GAP_EVENT, onGap);
    return () => {
      window.removeEventListener(OBJECT_WS_EVENT, handler);
      window.removeEventListener(OBJECT_WS_GAP_EVENT, onGap);
    };
  }, [queryClient]);

  return null;
}
