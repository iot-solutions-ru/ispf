import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { refreshWorkQueue } from "./workQueueCache";
import type { OperatorUi } from "../types/operatorUi";
import { OBJECT_WS_EVENT, type ObjectWsMessage } from "./useObjectWebSocket";

export const OPERATOR_SIDEBAR_EVENTS_QUERY_KEY = "operator-sidebar";

/**
 * Refresh operator sidebar events / work-queue on EVENT_FIRED only.
 * Do not subscribe to object paths: that would mark path-wide uiRefresh for every
 * variable under the journal prefix (e.g. all SNMP vars under devices.itm).
 */
export function useOperatorSidebarRefresh(_appId?: string, ui?: OperatorUi) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!_appId && !ui) {
      return;
    }
    const refresh = (message: ObjectWsMessage) => {
      if (message.type !== "EVENT_FIRED") {
        return;
      }
      void queryClient.invalidateQueries({ queryKey: ["events", OPERATOR_SIDEBAR_EVENTS_QUERY_KEY] });
      void refreshWorkQueue(queryClient);
    };

    const handler = (raw: Event) => {
      refresh((raw as CustomEvent<ObjectWsMessage>).detail);
    };
    window.addEventListener(OBJECT_WS_EVENT, handler);
    return () => window.removeEventListener(OBJECT_WS_EVENT, handler);
  }, [_appId, queryClient, ui]);
}
