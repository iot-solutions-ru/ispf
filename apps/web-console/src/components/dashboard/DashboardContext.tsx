import { useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
  emptySession,
  noop,
  selectionOwnerFromSession,
  sessionWithSelection,
  DashboardContext,
} from "./useDashboardContext";
import type {
  DashboardSession,
  OpenDashboardOptions,
  DashboardContextValue,
} from "./useDashboardContext";

interface DashboardProviderProps {
  children: ReactNode;
  value?: DashboardContextValue;
  operatorMode?: boolean;
  embeddedModal?: boolean;
  closeDashboardModal?: () => void;
  session?: DashboardSession;
  selection?: Record<string, string>;
  params?: Record<string, unknown>;
  onSessionChange?: (next: DashboardSession) => void;
  onSelectionChange?: (next: Record<string, string>) => void;
  onParamsChange?: (next: Record<string, unknown>) => void;
  onNavigateDashboard?: (path: string, options?: OpenDashboardOptions) => void;
  onOpenDashboardModal?: (path: string, title?: string, options?: OpenDashboardOptions) => void;
}

export function DashboardProvider({
  children,
  value,
  operatorMode = false,
  embeddedModal = false,
  closeDashboardModal,
  session: controlledSession,
  selection: controlledSelection,
  params: controlledParams,
  onSessionChange,
  onSelectionChange,
  onParamsChange,
  onNavigateDashboard,
  onOpenDashboardModal,
}: DashboardProviderProps) {
  const [internalSession, setInternalSession] = useState<DashboardSession>(emptySession);

  const session =
    controlledSession ??
    ({
      selection: controlledSelection ?? internalSession.selection,
      params: controlledParams ?? internalSession.params,
      widgets: internalSession.widgets,
    } satisfies DashboardSession);

  const sessionRef = useRef(session);
  sessionRef.current = session;

  const derivedValue = useMemo<DashboardContextValue>(() => {
    if (value) {
      return value;
    }

    const publishSession = (next: DashboardSession) => {
      sessionRef.current = next;
      if (onSessionChange) {
        onSessionChange(next);
      } else {
        if (onSelectionChange) {
          onSelectionChange(next.selection);
        }
        if (onParamsChange) {
          onParamsChange(next.params);
        }
        if (!onSelectionChange && !onParamsChange) {
          setInternalSession(next);
        }
      }
    };

    const setSelection = (key: string, path: string, ownerId?: string) => {
      publishSession(sessionWithSelection(sessionRef.current, key, path, ownerId));
    };

    const setParams = (patch: Record<string, unknown>) => {
      const current = sessionRef.current;
      publishSession({
        ...current,
        params: { ...current.params, ...patch },
      });
    };

    return {
      operatorMode,
      embeddedModal,
      selection: session.selection,
      params: session.params,
      widgets: session.widgets,
      selectionOwner: selectionOwnerFromSession(session),
      setSelection,
      setParams,
      navigateToDashboard: onNavigateDashboard ?? noop,
      openDashboardModal: onOpenDashboardModal ?? noop,
      closeDashboardModal: closeDashboardModal ?? noop,
    };
  }, [
    value,
    session,
    onSessionChange,
    onSelectionChange,
    onParamsChange,
    onNavigateDashboard,
    onOpenDashboardModal,
    operatorMode,
    embeddedModal,
    closeDashboardModal,
  ]);

  return <DashboardContext.Provider value={derivedValue}>{children}</DashboardContext.Provider>;
}

