// Extracted from AdminCopilotChatContext.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import { createContext, useContext } from "react";
import type {
  AiAgentStep,
  AiProviderStatus,
  AgentClientFocus,
  AgentInteractionMode,
} from "../api/ai";

export interface CopilotChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
  steps?: AiAgentStep[];
  result?: Record<string, unknown>;
  status?: string;
}

export interface AdminCopilotChatContextValue {
  provider: AiProviderStatus | undefined;
  providerLoading: boolean;
  agentApiReady: boolean;
  messages: CopilotChatMessage[];
  isPending: boolean;
  liveSteps: AiAgentStep[];
  sendMessage: (
    text: string,
    options?: { clientFocus?: AgentClientFocus | null }
  ) => Promise<void>;
  cancelRun: () => Promise<void>;
  startNewChat: () => void;
  interactionMode: AgentInteractionMode;
  setInteractionMode: (mode: AgentInteractionMode) => void;
}

export const AdminCopilotChatContext = createContext<AdminCopilotChatContextValue | null>(null);

export function useAdminCopilotChat(): AdminCopilotChatContextValue {
  const ctx = useContext(AdminCopilotChatContext);
  if (!ctx) {
    throw new Error("useAdminCopilotChat must be used within AdminCopilotChatProvider");
  }
  return ctx;
}

export function useAdminCopilotChatOptional(): AdminCopilotChatContextValue | null {
  return useContext(AdminCopilotChatContext);
}
