// Extracted from AgentChatContext.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import { createContext, useContext } from "react";
import type {
  AiAgentStep,
  AiAgentTool,
  AiProviderStatus,
  AgentClientFocus,
  AgentInteractionMode,
  AgentMessageAttachmentMeta,
  AgentPlanState,
} from "../api/ai";
import type { AgentChatIndex } from "../utils/agent/agentChatStorage";
import type { AgentChatAttachment } from "../utils/agent/agentChatAttachments";

export interface ChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
  attachments?: AgentMessageAttachmentMeta[];
  interactionMode?: AgentInteractionMode;
  steps?: AiAgentStep[];
  result?: Record<string, unknown>;
  status?: string;
  turnId?: string;
}

export interface AgentChatContextValue {
  provider: AiProviderStatus | undefined;
  providerLoading: boolean;
  providerReachable: boolean;
  agentApiReady: boolean;
  agentApiChecking: boolean;
  agentApiBanner: string | null;
  agentTools: AiAgentTool[] | undefined;
  chatIndex: AgentChatIndex;
  activeSessionId: string | null;
  messages: ChatMessage[];
  loadingSession: boolean;
  isPending: boolean;
  liveSteps: AiAgentStep[];
  livePlanPhase?: AgentPlanState["planPhase"];
  pendingUserMessage: string | null;
  defaultRootPath: string;
  startNewChat: () => Promise<void>;
  switchSession: (sessionId: string) => Promise<void>;
  deleteChat: (sessionId: string) => Promise<void>;
  sendMessage: (
    text: string,
    options?: { attachments?: AgentChatAttachment[]; clientFocus?: AgentClientFocus | null }
  ) => Promise<void>;
  cancelRun: () => Promise<void>;
  clearLocalChatIndex: () => void;
  interactionMode: AgentInteractionMode;
  setInteractionMode: (mode: AgentInteractionMode) => void;
}

export const AgentChatContext = createContext<AgentChatContextValue | null>(null);

export function useAgentChat(): AgentChatContextValue {
  const ctx = useContext(AgentChatContext);
  if (!ctx) {
    throw new Error("useAgentChat must be used within AgentChatProvider");
  }
  return ctx;
}

export function useAgentChatOptional(): AgentChatContextValue | null {
  return useContext(AgentChatContext);
}
