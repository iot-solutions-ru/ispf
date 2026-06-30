import type { QueryClient } from "@tanstack/react-query";
import { refreshWorkQueue } from "./workQueueCache";
import type { ObjectWsMessage } from "./objectWebSocketTypes";

/** React Query invalidations for a single `/ws/objects` payload. */
export function applyObjectWebSocketMessage(queryClient: QueryClient, message: ObjectWsMessage): void {
  switch (message.type) {
    case "CREATED":
    case "UPDATED":
    case "DELETED":
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
}
