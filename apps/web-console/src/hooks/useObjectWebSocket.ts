import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";

export interface ObjectWsMessage {
  type: "CREATED" | "UPDATED" | "DELETED" | "VARIABLE_UPDATED" | "EVENT_FIRED";
  path: string;
  variableName: string;
  timestamp: string;
}

function wsUrl(): string {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws/objects`;
}

export function useObjectWebSocket() {
  const queryClient = useQueryClient();

  useEffect(() => {
    let active = true;
    let socket: WebSocket | null = null;
    let retryTimer: number | undefined;

    const connect = () => {
      if (!active) return;
      socket = new WebSocket(wsUrl());

      socket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data) as ObjectWsMessage;
          queryClient.invalidateQueries({ queryKey: ["objects"] });
          queryClient.invalidateQueries({ queryKey: ["object", message.path] });
          queryClient.invalidateQueries({ queryKey: ["object-editor", message.path] });
          queryClient.invalidateQueries({ queryKey: ["variables", message.path] });
          queryClient.invalidateQueries({ queryKey: ["dashboard", message.path] });
          queryClient.invalidateQueries({ queryKey: ["workflow", message.path] });
          queryClient.invalidateQueries({ queryKey: ["work-queue"] });
          queryClient.invalidateQueries({ queryKey: ["events"] });
          if (message.type === "VARIABLE_UPDATED") {
            queryClient.invalidateQueries({ queryKey: ["events", message.path] });
            queryClient.invalidateQueries({ queryKey: ["events", "all"] });
          }
          if (message.type === "EVENT_FIRED") {
            queryClient.invalidateQueries({ queryKey: ["events", message.path] });
            queryClient.invalidateQueries({ queryKey: ["events", "all"] });
          }
        } catch {
          // ignore malformed messages
        }
      };

      socket.onclose = () => {
        if (active) {
          retryTimer = window.setTimeout(connect, 3000);
        }
      };
    };

    connect();

    return () => {
      active = false;
      if (retryTimer) {
        window.clearTimeout(retryTimer);
      }
      socket?.close();
    };
  }, [queryClient]);
}
