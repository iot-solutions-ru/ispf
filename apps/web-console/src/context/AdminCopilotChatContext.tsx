import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useQuery } from "@tanstack/react-query";
import {
  cancelAgentRun,
  createAgentSession,
  fetchAgentSession,
  fetchAiAgentTools,
  fetchAiProviderStatus,
  fetchNewAgentTurnWithRetry,
  sendAgentMessage,
  waitForAgentTurnCompletion,
  isAgentAcceptedResponse,
  isAgentTurnResultDeliveryError,
  waitUntilAgentRunIdle,
  type AiAgentChatResponse,
  type AiAgentStep,
  type AiAgentTurn,
  type AiProviderStatus,
  type AgentClientFocus,
  type AgentInteractionMode,
  type AgentPlanState,
} from "../api/ai";
import { useAdminFocusOptional } from "./AdminFocusContext";
import {
  loadAgentChatIndex,
  loadAiStudioPrefs,
  loadCopilotPrefs,
  purgeLegacyAgentPending,
  removeChatEntry,
  saveAgentChatIndex,
  saveCopilotPrefs,
  upsertChatEntry,
  type AgentChatIndex,
} from "../utils/agentChatStorage";
import i18n from "../i18n";

export interface CopilotChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
  steps?: AiAgentStep[];
  result?: Record<string, unknown>;
  status?: string;
}

interface AdminCopilotChatContextValue {
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

const AdminCopilotChatContext = createContext<AdminCopilotChatContextValue | null>(null);

const CHANNEL = "copilot" as const;

function newId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function resolveRootPath(): string {
  return loadAiStudioPrefs().defaultRootPath.trim() || "root";
}

function turnsToMessages(turns: AiAgentTurn[]): CopilotChatMessage[] {
  const messages: CopilotChatMessage[] = [];
  for (const turn of turns) {
    messages.push({ id: turn.turnId + "-u", role: "user", text: turn.userMessage });
    messages.push({
      id: turn.turnId + "-a",
      role: "agent",
      text: turn.assistantSummary,
      steps: turn.steps,
      result: turn.result,
      status: turn.status,
    });
  }
  return messages;
}

function isAgentRunInProgressError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return message.includes("already in progress") || message.includes("409");
}

export function AdminCopilotChatProvider({
  children,
  enabled,
}: {
  children: ReactNode;
  enabled: boolean;
}) {
  const adminFocus = useAdminFocusOptional();
  const adminFocusRef = useRef(adminFocus);
  adminFocusRef.current = adminFocus;

  const [chatIndex, setChatIndex] = useState<AgentChatIndex>(() => loadAgentChatIndex(CHANNEL));
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<CopilotChatMessage[]>([]);
  const [isPending, setIsPending] = useState(false);
  const [liveSteps, setLiveSteps] = useState<AiAgentStep[]>([]);
  const [interactionMode, setInteractionModeState] = useState<AgentInteractionMode>(
    () => loadCopilotPrefs().interactionMode
  );

  const turnCountRef = useRef(0);
  const baselineTurnsAtSendRef = useRef(0);
  const turnDeliveredRef = useRef(false);
  const activeSessionIdRef = useRef<string | null>(null);

  useEffect(() => {
    activeSessionIdRef.current = activeSessionId;
  }, [activeSessionId]);

  useEffect(() => {
    purgeLegacyAgentPending();
  }, []);

  const providerQuery = useQuery({
    queryKey: ["ai-provider"],
    queryFn: fetchAiProviderStatus,
    enabled,
    staleTime: 60_000,
  });

  const agentToolsQuery = useQuery({
    queryKey: ["ai-agent-tools"],
    queryFn: fetchAiAgentTools,
    enabled,
    staleTime: 60_000,
    retry: false,
  });

  const persistIndex = useCallback((index: AgentChatIndex) => {
    setChatIndex(index);
    saveAgentChatIndex(index, CHANNEL);
  }, []);

  const setInteractionMode = useCallback((mode: AgentInteractionMode) => {
    setInteractionModeState(mode);
    saveCopilotPrefs({ ...loadCopilotPrefs(), interactionMode: mode });
  }, []);

  const startNewChat = useCallback(() => {
    setIsPending(false);
    setLiveSteps([]);
    setActiveSessionId(null);
    activeSessionIdRef.current = null;
    turnCountRef.current = 0;
    setMessages([]);
    persistIndex({ ...chatIndex, activeSessionId: null });
  }, [chatIndex, persistIndex]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let cancelled = false;
    (async () => {
      const index = loadAgentChatIndex(CHANNEL);
      setChatIndex(index);
      const prefs = loadCopilotPrefs();
      if (!prefs.restoreLastChat || !index.activeSessionId) {
        return;
      }
      try {
        const session = await fetchAgentSession(index.activeSessionId);
        if (cancelled) {
          return;
        }
        setActiveSessionId(session.sessionId);
        activeSessionIdRef.current = session.sessionId;
        turnCountRef.current = session.turns.length;
        setMessages(turnsToMessages(session.turns));
      } catch {
        const cleaned = removeChatEntry(index, index.activeSessionId);
        if (!cancelled) {
          persistIndex(cleaned);
          setActiveSessionId(null);
          activeSessionIdRef.current = null;
          setMessages([]);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [enabled, persistIndex]);

  const deliverTurn = useCallback(
    (data: AiAgentChatResponse) => {
      if (turnDeliveredRef.current) {
        return;
      }
      turnDeliveredRef.current = true;
      setLiveSteps([]);
      setMessages((prev) => [
        ...prev,
        {
          id: `${data.turnId ?? newId()}-a`,
          role: "agent",
          text: data.summary,
          steps: data.steps,
          result: data.result,
          status: data.status,
        },
      ]);
      turnCountRef.current += 1;
      const entry = {
        id: data.sessionId,
        title: data.title,
        updatedAt: new Date().toISOString(),
      };
      setChatIndex((prev) => {
        const next = upsertChatEntry({ ...prev, activeSessionId: data.sessionId }, entry);
        saveAgentChatIndex(next, CHANNEL);
        return next;
      });
      setActiveSessionId(data.sessionId);
      activeSessionIdRef.current = data.sessionId;
    },
    []
  );

  const sendMessage = useCallback(
    async (text: string, options?: { clientFocus?: AgentClientFocus | null }) => {
      const trimmed = text.trim();
      if (!trimmed || isPending) {
        return;
      }

      setLiveSteps([]);
      // Here-and-now: only the current Q&A is shown/used (fresh server session each send).
      setMessages([{ id: newId(), role: "user", text: trimmed }]);

      try {
        const rootPath = resolveRootPath();
        const clientFocus =
          options?.clientFocus !== undefined
            ? options.clientFocus
            : adminFocusRef.current?.toClientFocusPayload() ?? null;

        activeSessionIdRef.current = null;
        setActiveSessionId(null);
        const session = await createAgentSession(rootPath, "ask");
        const sessionId = session.sessionId;
        const entry = {
          id: session.sessionId,
          title: session.title,
          updatedAt: session.updatedAt,
        };
        const next = upsertChatEntry(loadAgentChatIndex(CHANNEL), entry);
        persistIndex({ ...next, activeSessionId: sessionId });
        setActiveSessionId(sessionId);
        activeSessionIdRef.current = sessionId;
        turnCountRef.current = 0;

        const liveSession = await fetchAgentSession(sessionId);
        baselineTurnsAtSendRef.current = liveSession.turns.length;
        turnCountRef.current = liveSession.turns.length;
        turnDeliveredRef.current = false;
        setIsPending(true);

        const onProgress = (progress: { steps?: AiAgentStep[]; planState?: AgentPlanState }) => {
          if (progress.steps && progress.steps.length > 0) {
            setLiveSteps(progress.steps);
          }
        };

        const runTurnAsync = async () => {
          const ack = await sendAgentMessage(
            sessionId!,
            trimmed,
            rootPath,
            "ask",
            undefined,
            true,
            clientFocus,
            "copilot"
          );
          if (isAgentAcceptedResponse(ack)) {
            return waitForAgentTurnCompletion(sessionId!, onProgress, {
              baselineTurnCount: baselineTurnsAtSendRef.current,
            });
          }
          return ack;
        };

        let data: AiAgentChatResponse;
        try {
          data = await runTurnAsync();
        } catch (error) {
          if (isAgentRunInProgressError(error) && sessionId) {
            await cancelAgentRun(sessionId);
            await waitUntilAgentRunIdle(sessionId);
            data = await runTurnAsync();
          } else {
            throw error;
          }
        }
        deliverTurn(data);
      } catch (error) {
        const recovered =
          activeSessionIdRef.current &&
          (await fetchNewAgentTurnWithRetry(
            activeSessionIdRef.current,
            baselineTurnsAtSendRef.current,
            40,
            500
          ));
        if (recovered) {
          deliverTurn(recovered);
        } else if (!isAgentTurnResultDeliveryError(error)) {
          setMessages((prev) => [
            ...prev,
            {
              id: newId(),
              role: "agent",
              text: `${i18n.t("agent.sendFailed", { ns: "ai" })}: ${
                error instanceof Error ? error.message : String(error)
              }`,
            },
          ]);
        }
      } finally {
        setIsPending(false);
      }
    },
    [deliverTurn, isPending, persistIndex]
  );

  const cancelRun = useCallback(async () => {
    const sessionId = activeSessionIdRef.current;
    if (!sessionId || !isPending) {
      return;
    }
    try {
      await cancelAgentRun(sessionId);
    } catch {
      // ignore
    }
  }, [isPending]);

  const value = useMemo<AdminCopilotChatContextValue>(
    () => ({
      provider: providerQuery.data,
      providerLoading: providerQuery.isLoading,
      agentApiReady: agentToolsQuery.isSuccess,
      messages,
      isPending,
      liveSteps,
      sendMessage,
      cancelRun,
      startNewChat,
      interactionMode,
      setInteractionMode,
    }),
    [
      agentToolsQuery.isSuccess,
      cancelRun,
      interactionMode,
      isPending,
      liveSteps,
      messages,
      providerQuery.data,
      providerQuery.isLoading,
      sendMessage,
      setInteractionMode,
      startNewChat,
    ]
  );

  return (
    <AdminCopilotChatContext.Provider value={value}>{children}</AdminCopilotChatContext.Provider>
  );
}

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
