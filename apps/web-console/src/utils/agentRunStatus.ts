import { useSyncExternalStore } from "react";

export type AgentRunStatus = {
  isPending: boolean;
  pendingUserMessage: string | null;
};

let current: AgentRunStatus = { isPending: false, pendingUserMessage: null };
const listeners = new Set<() => void>();

export function publishAgentRunStatus(next: AgentRunStatus): void {
  if (
    current.isPending === next.isPending
    && current.pendingUserMessage === next.pendingUserMessage
  ) {
    return;
  }
  current = next;
  listeners.forEach((listener) => listener());
}

export function readAgentRunStatus(): AgentRunStatus {
  return current;
}

export function useAgentRunStatus(): AgentRunStatus {
  return useSyncExternalStore(
    (onStoreChange) => {
      listeners.add(onStoreChange);
      return () => listeners.delete(onStoreChange);
    },
    () => current,
    () => current
  );
}
