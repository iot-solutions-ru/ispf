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
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createAgentSession,
  deleteAgentSession,
  fetchAgentSession,
  fetchAiProviderStatus,
  sendAgentMessage,
  type AiAgentChatResponse,
  type AiAgentSessionSummary,
  type AiAgentStep,
  type AiAgentTurn,
  type AiProviderStatus,
} from "../api/ai";
import {
  clearAgentChatIndex,
  clearAgentPendingTurn,
  loadAgentChatIndex,
  loadAgentPendingTurn,
  loadAiStudioPrefs,
  removeChatEntry,
  saveAgentChatIndex,
  saveAgentPendingTurn,
  upsertChatEntry,
  type AgentChatIndex,
  type AgentPendingTurn,
} from "../utils/agentChatStorage";

export const WELCOME_TEXT =
  "Опишите задачу обычным языком — например, создать SNMP-устройство, настроить метрики и дашборд. Контекст чата сохраняется между вкладками и перезагрузкой страницы.";

export interface ChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
  steps?: AiAgentStep[];
  result?: Record<string, unknown>;
  status?: string;
}

interface AgentChatContextValue {
  provider: AiProviderStatus | undefined;
  providerLoading: boolean;
  chatIndex: AgentChatIndex;
  activeSessionId: string | null;
  messages: ChatMessage[];
  input: string;
  setInput: (value: string) => void;
  loadingSession: boolean;
  isPending: boolean;
  pendingUserMessage: string | null;
  defaultRootPath: string;
  startNewChat: () => Promise<void>;
  switchSession: (sessionId: string) => Promise<void>;
  deleteChat: (sessionId: string) => Promise<void>;
  sendMessage: (text: string) => Promise<void>;
  clearLocalChatIndex: () => void;
}

const AgentChatContext = createContext<AgentChatContextValue | null>(null);

function newId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function turnsToMessages(turns: AiAgentTurn[]): ChatMessage[] {
  const messages: ChatMessage[] = [];
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

function responseToAgentMessage(data: AiAgentChatResponse): ChatMessage {
  return {
    id: data.turnId + "-a",
    role: "agent",
    text: data.summary,
    steps: data.steps,
    result: data.result,
    status: data.status,
  };
}

function pendingAgeMs(pending: AgentPendingTurn): number {
  return Date.now() - new Date(pending.startedAt).getTime();
}

const PENDING_MAX_AGE_MS = 15 * 60 * 1000;

export function AgentChatProvider({
  children,
  enabled,
}: {
  children: ReactNode;
  enabled: boolean;
}) {
  const queryClient = useQueryClient();
  const initialPrefs = useMemo(() => loadAiStudioPrefs(), []);
  const [chatIndex, setChatIndex] = useState<AgentChatIndex>(() => loadAgentChatIndex());
  const [activeSessionId, setActiveSessionId] = useState<string | null>(
    () => loadAgentChatIndex().activeSessionId
  );
  const [messages, setMessages] = useState<ChatMessage[]>([
    { id: "welcome", role: "agent", text: WELCOME_TEXT },
  ]);
  const [input, setInput] = useState("");
  const [loadingSession, setLoadingSession] = useState(enabled);
  const [recoveringPending, setRecoveringPending] = useState(false);
  const turnCountRef = useRef(0);
  const pendingRef = useRef<AgentPendingTurn | null>(loadAgentPendingTurn());

  const providerQuery = useQuery({
    queryKey: ["ai-provider"],
    queryFn: fetchAiProviderStatus,
    enabled,
    staleTime: 60_000,
  });

  const persistIndex = useCallback((index: AgentChatIndex) => {
    setChatIndex(index);
    saveAgentChatIndex(index);
  }, []);

  const applySession = useCallback(
    (session: AiAgentSessionSummary, index: AgentChatIndex, turns: AiAgentTurn[] = []) => {
      const entry = {
        id: session.sessionId,
        title: session.title,
        updatedAt: session.updatedAt,
      };
      const next = upsertChatEntry(index, entry);
      persistIndex({ ...next, activeSessionId: session.sessionId });
      setActiveSessionId(session.sessionId);
      turnCountRef.current = turns.length;
      const turnMessages = turnsToMessages(turns);
      setMessages(
        turnMessages.length > 0
          ? turnMessages
          : [{ id: "welcome", role: "agent", text: WELCOME_TEXT }]
      );
    },
    [persistIndex]
  );

  const finishPending = useCallback(() => {
    pendingRef.current = null;
    clearAgentPendingTurn();
    setRecoveringPending(false);
  }, []);

  const tryRecoverPending = useCallback(
    async (pending: AgentPendingTurn, index: AgentChatIndex) => {
      if (pendingAgeMs(pending) > PENDING_MAX_AGE_MS) {
        finishPending();
        return;
      }
      setRecoveringPending(true);
      try {
        const session = await fetchAgentSession(pending.sessionId);
        if (session.turns.length > pending.turnCountBefore) {
          const lastTurn = session.turns[session.turns.length - 1];
          applySession(session, index, session.turns);
          finishPending();
          if (lastTurn.userMessage === pending.userMessage) {
            return;
          }
        }
        setMessages((prev) => {
          const hasUser = prev.some((m) => m.role === "user" && m.text === pending.userMessage);
          if (hasUser) {
            return prev;
          }
          return [...prev, { id: newId(), role: "user", text: pending.userMessage }];
        });
        setActiveSessionId(pending.sessionId);
      } catch {
        finishPending();
      }
    },
    [applySession, finishPending]
  );

  useEffect(() => {
    if (!enabled) {
      setLoadingSession(false);
      return;
    }
    let cancelled = false;
    (async () => {
      setLoadingSession(true);
      try {
        const index = loadAgentChatIndex();
        setChatIndex(index);
        const pending = loadAgentPendingTurn();
        pendingRef.current = pending;

        if (pending) {
          await tryRecoverPending(pending, index);
          if (!cancelled && pendingRef.current) {
            setLoadingSession(false);
            return;
          }
        }

        if (initialPrefs.restoreLastChat && index.activeSessionId) {
          try {
            const session = await fetchAgentSession(index.activeSessionId);
            if (!cancelled) {
              applySession(session, index, session.turns);
            }
          } catch {
            const cleaned = removeChatEntry(index, index.activeSessionId);
            persistIndex(cleaned);
            if (!cancelled) {
              setActiveSessionId(null);
              setMessages([{ id: "welcome", role: "agent", text: WELCOME_TEXT }]);
            }
          }
        }
      } finally {
        if (!cancelled) {
          setLoadingSession(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [applySession, enabled, initialPrefs.restoreLastChat, persistIndex, tryRecoverPending]);

  useEffect(() => {
    if (!enabled || !recoveringPending || !pendingRef.current) {
      return;
    }
    const pending = pendingRef.current;
    const interval = window.setInterval(() => {
      if (!pendingRef.current) {
        return;
      }
      if (pendingAgeMs(pending) > PENDING_MAX_AGE_MS) {
        finishPending();
        setMessages((prev) => [
          ...prev,
          {
            id: newId(),
            role: "agent",
            text: "Запрос прерван — ответ не получен вовремя. Повторите сообщение или проверьте сессию позже.",
          },
        ]);
        return;
      }
      void fetchAgentSession(pending.sessionId)
        .then((session) => {
          if (session.turns.length > pending.turnCountBefore) {
            applySession(session, loadAgentChatIndex(), session.turns);
            finishPending();
          }
        })
        .catch(() => {
          finishPending();
        });
    }, 2500);
    return () => window.clearInterval(interval);
  }, [applySession, enabled, finishPending, recoveringPending]);

  const sendMutation = useMutation({
    mutationFn: ({ sessionId, text, rootPath }: { sessionId: string; text: string; rootPath: string }) =>
      sendAgentMessage(sessionId, text, rootPath),
    onSuccess: (data) => {
      finishPending();
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      setMessages((prev) => [...prev, responseToAgentMessage(data)]);
      turnCountRef.current += 1;
      const entry = {
        id: data.sessionId,
        title: data.title,
        updatedAt: new Date().toISOString(),
      };
      setChatIndex((prev) => {
        const next = upsertChatEntry({ ...prev, activeSessionId: data.sessionId }, entry);
        persistIndex(next);
        return next;
      });
      setActiveSessionId(data.sessionId);
    },
    onError: (error) => {
      finishPending();
      setMessages((prev) => [
        ...prev,
        {
          id: newId(),
          role: "agent",
          text: `Не удалось выполнить задачу: ${String(error)}`,
        },
      ]);
    },
  });

  const startNewChat = useCallback(async () => {
    const rootPath = loadAiStudioPrefs().defaultRootPath;
    const session = await createAgentSession(rootPath);
    const entry = {
      id: session.sessionId,
      title: session.title,
      updatedAt: session.updatedAt,
    };
    setChatIndex((prev) => {
      const next = upsertChatEntry(prev, entry);
      persistIndex({ ...next, activeSessionId: session.sessionId });
      return next;
    });
    setActiveSessionId(session.sessionId);
    turnCountRef.current = 0;
    setMessages([{ id: "welcome", role: "agent", text: WELCOME_TEXT }]);
    setInput("");
  }, [persistIndex]);

  const switchSession = useCallback(
    async (sessionId: string) => {
      if (sessionId === activeSessionId || sendMutation.isPending || recoveringPending) {
        return;
      }
      try {
        const session = await fetchAgentSession(sessionId);
        applySession(session, { ...chatIndex, activeSessionId: sessionId }, session.turns);
      } catch {
        const cleaned = removeChatEntry(chatIndex, sessionId);
        persistIndex(cleaned);
      }
    },
    [activeSessionId, applySession, chatIndex, persistIndex, recoveringPending, sendMutation.isPending]
  );

  const deleteChat = useCallback(
    async (sessionId: string) => {
      try {
        await deleteAgentSession(sessionId);
      } catch {
        // session may already be gone on server
      }
      const cleaned = removeChatEntry(chatIndex, sessionId);
      persistIndex(cleaned);
      if (activeSessionId === sessionId) {
        if (cleaned.chats.length > 0 && cleaned.activeSessionId) {
          await switchSession(cleaned.activeSessionId);
        } else {
          setActiveSessionId(null);
          turnCountRef.current = 0;
          setMessages([{ id: "welcome", role: "agent", text: WELCOME_TEXT }]);
        }
      }
    },
    [activeSessionId, chatIndex, persistIndex, switchSession]
  );

  const sendMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || sendMutation.isPending || recoveringPending) {
        return;
      }
      let sessionId = activeSessionId;
      const rootPath = loadAiStudioPrefs().defaultRootPath;
      if (!sessionId) {
        const session = await createAgentSession(rootPath);
        sessionId = session.sessionId;
        applySession(session, chatIndex);
      }
      const pending: AgentPendingTurn = {
        sessionId,
        startedAt: new Date().toISOString(),
        userMessage: trimmed,
        turnCountBefore: turnCountRef.current,
      };
      pendingRef.current = pending;
      saveAgentPendingTurn(pending);
      setMessages((prev) => [...prev, { id: newId(), role: "user", text: trimmed }]);
      setInput("");
      sendMutation.mutate({ sessionId, text: trimmed, rootPath });
    },
    [activeSessionId, applySession, chatIndex, recoveringPending, sendMutation]
  );

  const clearLocalChatIndex = useCallback(() => {
    const cleared = clearAgentChatIndex();
    setChatIndex(cleared);
    setActiveSessionId(null);
    turnCountRef.current = 0;
    setMessages([{ id: "welcome", role: "agent", text: WELCOME_TEXT }]);
  }, []);

  const isPending = sendMutation.isPending || recoveringPending;
  const pendingUserMessage = isPending ? pendingRef.current?.userMessage ?? null : null;

  const value = useMemo<AgentChatContextValue>(
    () => ({
      provider: providerQuery.data,
      providerLoading: providerQuery.isLoading,
      chatIndex,
      activeSessionId,
      messages,
      input,
      setInput,
      loadingSession,
      isPending,
      pendingUserMessage,
      defaultRootPath: loadAiStudioPrefs().defaultRootPath,
      startNewChat,
      switchSession,
      deleteChat,
      sendMessage,
      clearLocalChatIndex,
    }),
    [
      providerQuery.data,
      providerQuery.isLoading,
      chatIndex,
      activeSessionId,
      messages,
      input,
      loadingSession,
      isPending,
      pendingUserMessage,
      startNewChat,
      switchSession,
      deleteChat,
      sendMessage,
      clearLocalChatIndex,
    ]
  );

  return <AgentChatContext.Provider value={value}>{children}</AgentChatContext.Provider>;
}

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
