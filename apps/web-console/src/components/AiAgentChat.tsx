import { useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchAiAgentTools } from "../api/ai";
import { useAgentChat } from "../context/AgentChatContext";

const EXAMPLE_PROMPTS = [
  "Создай SNMP-устройство localhost, выведи метрики мониторинга ресурсов системы и создай дашборд, где они будут отображаться.",
  "Покажи, какие устройства есть в root.platform.devices.",
  "Проверь, работает ли SNMP localhost, и запусти опрос драйвера если нужно.",
];

interface AiAgentChatProps {
  visible: boolean;
}

function formatChatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString(undefined, {
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "";
  }
}

function dashboardLink(path: string | undefined): string | null {
  if (!path) {
    return null;
  }
  return `/?path=${encodeURIComponent(path)}`;
}

export default function AiAgentChat({ visible }: AiAgentChatProps) {
  const {
    provider,
    chatIndex,
    activeSessionId,
    messages,
    input,
    setInput,
    loadingSession,
    isPending,
    startNewChat,
    switchSession,
    deleteChat,
    sendMessage,
  } = useAgentChat();
  const scrollRef = useRef<HTMLDivElement>(null);

  const agentToolsQuery = useQuery({
    queryKey: ["ai-agent-tools"],
    queryFn: fetchAiAgentTools,
  });

  useEffect(() => {
    if (visible && isPending) {
      scrollRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [visible, isPending, messages.length]);

  if (loadingSession) {
    return (
      <div className="ai-studio-tab-panel active" hidden={!visible}>
        <p className="op-muted">Загрузка чата…</p>
      </div>
    );
  }

  const providerReady = provider?.available ?? false;

  return (
    <div className={`ai-studio-tab-panel ${visible ? "active" : ""}`} hidden={!visible} aria-hidden={!visible}>
      {!providerReady && (
        <div className="op-alert op-alert-error">
          LLM не настроен — агент недоступен. Настройте провайдер на вкладке «Настройки».
        </div>
      )}

      <div className="ai-agent-chat ai-agent-chat-layout">
        <aside className="ai-agent-sidebar">
          <button
            type="button"
            className="btn primary ai-agent-new-chat"
            disabled={isPending}
            onClick={() => void startNewChat()}
          >
            + Новый чат
          </button>
          <ul className="ai-agent-chat-list">
            {chatIndex.chats.map((chat) => (
              <li
                key={chat.id}
                className={chat.id === activeSessionId ? "ai-agent-chat-row active" : "ai-agent-chat-row"}
              >
                <button
                  type="button"
                  className="ai-agent-chat-item"
                  onClick={() => void switchSession(chat.id)}
                >
                  <span className="ai-agent-chat-title">{chat.title}</span>
                  {chat.updatedAt && (
                    <span className="ai-agent-chat-date">{formatChatDate(chat.updatedAt)}</span>
                  )}
                </button>
                {isPending && chat.id === activeSessionId && (
                  <span className="ai-agent-chat-pending-dot" title="Выполняется запрос" />
                )}
                <button
                  type="button"
                  className="ai-agent-chat-delete"
                  aria-label={`Удалить чат «${chat.title}»`}
                  disabled={isPending}
                  onClick={() => void deleteChat(chat.id)}
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
          {agentToolsQuery.data && (
            <details className="ai-agent-sidebar-tools">
              <summary>Инструменты ({agentToolsQuery.data.tools.length})</summary>
              <ul>
                {agentToolsQuery.data.tools.map((tool) => (
                  <li key={tool.name}>
                    <code>{tool.name}</code> — {tool.description}
                  </li>
                ))}
              </ul>
            </details>
          )}
        </aside>

        <div className="ai-agent-chat-main">
          <div className="ai-agent-chat-examples">
            <span className="op-muted">Примеры:</span>
            {EXAMPLE_PROMPTS.map((example) => (
              <button
                key={example}
                type="button"
                className="btn small ai-agent-example-chip"
                title={example}
                disabled={isPending || !providerReady}
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
            {isPending && (
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
              disabled={isPending || !providerReady}
            />
            <button
              type="button"
              className="btn primary"
              disabled={isPending || !providerReady || !input.trim()}
              onClick={() => void sendMessage(input)}
            >
              Отправить
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function stepStatusBadge(status: string | undefined): React.ReactNode {
  if (!status) {
    return null;
  }
  const ok = status === "OK" || status === "SUCCESS";
  return (
    <span className={`badge ${ok ? "ok" : "danger"}`}>{ok ? "OK" : status}</span>
  );
}

function AgentRunDetails({
  steps,
  status,
  result,
}: {
  steps: import("../api/ai").AiAgentStep[];
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
              <code>{step.tool}</code>
              {" "}
              {step.label || ""}
              {" "}
              {stepStatusBadge(
                step.result?.status === "ERROR"
                  ? "ERROR"
                  : step.result?.status === "OK"
                    ? "OK"
                    : undefined
              )}
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
            Открыть устройство
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
