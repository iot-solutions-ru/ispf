import { useCallback, useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createAgentSession,
  deleteAgentSession,
  fetchAgentSession,
  fetchAiAgentTools,
  fetchAiProviderStatus,
  sendAgentMessage,
  type AiAgentChatResponse,
  type AiAgentSessionSummary,
  type AiAgentStep,
  type AiAgentTurn,
} from "../api/ai";
import {
  loadAgentChatIndex,
  removeChatEntry,
  saveAgentChatIndex,
  upsertChatEntry,
  type AgentChatIndex,
} from "../utils/agentChatStorage";

const EXAMPLE_PROMPTS = [
  "Создай SNMP-устройство localhost, выведи метрики мониторинга ресурсов системы и создай дашборд, где они будут отображаться.",
  "Покажи, какие устройства есть в root.platform.devices.",
  "Проверь, работает ли SNMP localhost, и запусти опрос драйвера если нужно.",
];

const WELCOME_TEXT =
  "Опишите задачу обычным языком — например, создать SNMP-устройство, настроить метрики и дашборд. Контекст чата сохраняется, пока вы не начнёте новый.";

interface ChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
  steps?: AiAgentStep[];
  result?: Record<string, unknown>;
  status?: string;
}

function newId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function dashboardLink(path: string | undefined): string | null {
  if (!path) {
    return null;
  }
  return `/?path=${encodeURIComponent(path)}`;
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

export default function AiAgentChat() {
  const queryClient = useQueryClient();
  const [chatIndex, setChatIndex] = useState<AgentChatIndex>(() => loadAgentChatIndex());
  const [activeSessionId, setActiveSessionId] = useState<string | null>(
    () => loadAgentChatIndex().activeSessionId
  );
  const [messages, setMessages] = useState<ChatMessage[]>([
    { id: "welcome", role: "agent", text: WELCOME_TEXT },
  ]);
  const [input, setInput] = useState("");
  const [loadingSession, setLoadingSession] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);

  const providerQuery = useQuery({
    queryKey: ["ai-provider"],
    queryFn: fetchAiProviderStatus,
  });

  const agentToolsQuery = useQuery({
    queryKey: ["ai-agent-tools"],
    queryFn: fetchAiAgentTools,
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
      const turnMessages = turnsToMessages(turns);
      setMessages(
        turnMessages.length > 0
          ? turnMessages
          : [{ id: "welcome", role: "agent", text: WELCOME_TEXT }]
      );
    },
    [persistIndex]
  );

  const startNewChat = useCallback(async () => {
    const session = await createAgentSession("root");
    const entry = {
      id: session.sessionId,
      title: session.title,
      updatedAt: session.updatedAt,
    };
    const next = upsertChatEntry(chatIndex, entry);
    persistIndex({ ...next, activeSessionId: session.sessionId });
    setActiveSessionId(session.sessionId);
    setMessages([{ id: "welcome", role: "agent", text: WELCOME_TEXT }]);
  }, [chatIndex, persistIndex]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoadingSession(true);
      try {
        const index = loadAgentChatIndex();
        setChatIndex(index);
        if (index.activeSessionId) {
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
        } else if (index.chats.length === 0) {
          const session = await createAgentSession("root");
          if (!cancelled) {
            applySession(session, index);
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
  }, [applySession, persistIndex]);

  const sendMutation = useMutation({
    mutationFn: ({ sessionId, text }: { sessionId: string; text: string }) =>
      sendAgentMessage(sessionId, text),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      setMessages((prev) => [...prev, responseToAgentMessage(data)]);
      const entry = {
        id: data.sessionId,
        title: data.title,
        updatedAt: new Date().toISOString(),
      };
      persistIndex(
        upsertChatEntry({ ...chatIndex, activeSessionId: data.sessionId }, entry)
      );
      scrollRef.current?.scrollIntoView({ behavior: "smooth" });
    },
    onError: (error) => {
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

  const switchSession = async (sessionId: string) => {
    if (sessionId === activeSessionId || sendMutation.isPending) {
      return;
    }
    try {
      const session = await fetchAgentSession(sessionId);
      applySession(session, { ...chatIndex, activeSessionId: sessionId }, session.turns);
    } catch {
      const cleaned = removeChatEntry(chatIndex, sessionId);
      persistIndex(cleaned);
    }
  };

  const handleDeleteChat = async (sessionId: string, event: React.MouseEvent) => {
    event.stopPropagation();
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
        await startNewChat();
      }
    }
  };

  const sendMessage = async (text: string) => {
    const trimmed = text.trim();
    if (!trimmed || sendMutation.isPending) {
      return;
    }
    let sessionId = activeSessionId;
    if (!sessionId) {
      const session = await createAgentSession("root");
      sessionId = session.sessionId;
      applySession(session, chatIndex);
    }
    setMessages((prev) => [...prev, { id: newId(), role: "user", text: trimmed }]);
    setInput("");
    sendMutation.mutate({ sessionId, text: trimmed });
    setTimeout(() => scrollRef.current?.scrollIntoView({ behavior: "smooth" }), 50);
  };

  const provider = providerQuery.data;

  if (loadingSession) {
    return <p className="op-muted">Загрузка чата…</p>;
  }

  return (
    <div className="ai-agent-chat-layout">
      <aside className="ai-agent-sidebar">
        <button
          type="button"
          className="btn primary ai-agent-new-chat"
          disabled={sendMutation.isPending}
          onClick={() => void startNewChat()}
        >
          + New chat
        </button>
        <ul className="ai-agent-chat-list">
          {chatIndex.chats.map((chat) => (
            <li key={chat.id}>
              <button
                type="button"
                className={
                  chat.id === activeSessionId
                    ? "ai-agent-chat-item active"
                    : "ai-agent-chat-item"
                }
                onClick={() => void switchSession(chat.id)}
              >
                <span className="ai-agent-chat-title">{chat.title}</span>
                <span
                  className="ai-agent-chat-delete"
                  role="button"
                  tabIndex={0}
                  aria-label="Delete chat"
                  onClick={(e) => void handleDeleteChat(chat.id, e)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      void handleDeleteChat(chat.id, e as unknown as React.MouseEvent);
                    }
                  }}
                >
                  ×
                </span>
              </button>
            </li>
          ))}
        </ul>
      </aside>

      <div className="ai-agent-chat-main">
        <div className="ai-agent-chat-examples">
          <span className="op-muted">Примеры:</span>
          {EXAMPLE_PROMPTS.map((example) => (
            <button
              key={example}
              type="button"
              className="btn small ai-agent-example-chip"
              disabled={sendMutation.isPending || !provider?.available}
              onClick={() => void sendMessage(example)}
            >
              {example.length > 72 ? `${example.slice(0, 72)}…` : example}
            </button>
          ))}
        </div>

        <div className="ai-agent-chat-log">
          {messages.map((message) => (
            <div
              key={message.id}
              className={
                message.role === "user" ? "ai-agent-bubble user" : "ai-agent-bubble agent"
              }
            >
              <div className="ai-agent-bubble-text">{message.text}</div>
              {message.steps && message.steps.length > 0 && (
                <AgentRunDetails
                  steps={message.steps}
                  status={message.status}
                  result={message.result}
                />
              )}
            </div>
          ))}
          {sendMutation.isPending && (
            <div className="ai-agent-bubble agent ai-agent-bubble-pending">
              <div className="ai-agent-bubble-text">Выполняю…</div>
            </div>
          )}
          <div ref={scrollRef} />
        </div>

        <div className="ai-agent-chat-compose">
          <textarea
            rows={3}
            value={input}
            placeholder="Например: теперь добавь на дашборд виджет CPU…"
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                void sendMessage(input);
              }
            }}
            disabled={sendMutation.isPending || !provider?.available}
          />
          <button
            type="button"
            className="btn primary"
            disabled={sendMutation.isPending || !provider?.available || !input.trim()}
            onClick={() => void sendMessage(input)}
          >
            Отправить
          </button>
        </div>

        {agentToolsQuery.data && (
          <details className="ai-agent-tools">
            <summary>Инструменты агента ({agentToolsQuery.data.tools.length})</summary>
            <ul>
              {agentToolsQuery.data.tools.map((tool) => (
                <li key={tool.name}>
                  <code>{tool.name}</code> — {tool.description}
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>
    </div>
  );
}

function AgentRunDetails({
  steps,
  status,
  result,
}: {
  steps: AiAgentStep[];
  status?: string;
  result?: Record<string, unknown>;
}) {
  const devicePath = typeof result?.devicePath === "string" ? result.devicePath : undefined;
  const dashboardPath =
    typeof result?.dashboardPath === "string" ? result.dashboardPath : undefined;
  const toolSteps = steps.filter((s) => s.type === "tool");

  return (
    <details className="ai-agent-run-details">
      <summary>
        {status === "OK" ? "Подробности" : "Что пошло не так"}
        {toolSteps.length > 0 ? ` (${toolSteps.length} шагов)` : ""}
      </summary>
      {toolSteps.length > 0 && (
        <ol className="ai-agent-step-list">
          {toolSteps.map((step) => (
            <li key={step.step}>
              {step.label || step.tool}
              {step.result?.status === "ERROR" && (
                <span className="ai-agent-step-error">
                  {" "}
                  — {String(step.result.error ?? "ошибка")}
                </span>
              )}
            </li>
          ))}
        </ol>
      )}
      <div className="ai-agent-run-links">
        {devicePath && (
          <a className="btn small" href={dashboardLink(devicePath) ?? "#"}>
            Устройство
          </a>
        )}
        {dashboardPath && (
          <a className="btn small" href={dashboardLink(dashboardPath) ?? "#"}>
            Открыть дашборд
          </a>
        )}
      </div>
    </details>
  );
}
