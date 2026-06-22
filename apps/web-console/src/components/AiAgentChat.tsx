import { useEffect, useRef } from "react";
import { useAgentChat } from "../context/AgentChatContext";

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

export default function AiAgentChat() {
  const {
    provider,
    agentApiReady,
    agentApiBanner,
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

  useEffect(() => {
    if (isPending) {
      scrollRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [isPending, messages.length]);

  if (loadingSession) {
    return <p className="op-muted">Загрузка чата…</p>;
  }

  const providerReady = provider?.available ?? false;
  const sending = isPending;
  const chatEnabled = providerReady && agentApiReady;

  return (
    <div className="ai-agent-chat">
      {agentApiBanner && (
        <div className="op-alert op-alert-error" role="alert">
          {agentApiBanner}
        </div>
      )}

      {!providerReady && !agentApiBanner && (
        <div className="op-alert op-alert-error">
          LLM не настроен — откройте вкладку «Настройки».
        </div>
      )}

      <div className="ai-agent-toolbar" role="toolbar" aria-label="Чаты агента">
        <button
          type="button"
          className="btn primary ai-agent-new-chat"
          disabled={sending || !chatEnabled}
          onClick={() => void startNewChat()}
          title="Новый чат"
        >
          <span className="ai-agent-new-chat-label">+ Новый</span>
        </button>
        <div className="ai-agent-chat-strip" role="tablist" aria-label="Список чатов">
          {chatIndex.chats.length === 0 && (
            <span className="ai-agent-chat-strip-empty op-muted">Нет чатов</span>
          )}
          {chatIndex.chats.map((chat) => {
            const active = chat.id === activeSessionId;
            return (
              <div
                key={chat.id}
                className={active ? "ai-agent-chat-pill active" : "ai-agent-chat-pill"}
                role="presentation"
              >
                <button
                  type="button"
                  role="tab"
                  aria-selected={active}
                  className="ai-agent-chat-pill-btn"
                  disabled={sending}
                  onClick={() => void switchSession(chat.id)}
                  title={chat.updatedAt ? formatChatDate(chat.updatedAt) : chat.title}
                >
                  <span className="ai-agent-chat-title">{chat.title}</span>
                  {isPending && active && (
                    <span className="ai-agent-chat-pending-dot" title="Выполняется запрос" />
                  )}
                </button>
                <button
                  type="button"
                  className="ai-agent-chat-delete"
                  aria-label={`Удалить чат «${chat.title}»`}
                  disabled={sending}
                  onClick={() => void deleteChat(chat.id)}
                >
                  ×
                </button>
              </div>
            );
          })}
        </div>
        {isPending && <span className="ai-agent-toolbar-busy op-muted">Выполняю…</span>}
      </div>

      <div className="ai-agent-chat-main">
        <div className="ai-agent-chat-log">
          {messages.length === 0 && !isPending && (
            <p className="ai-agent-chat-empty op-muted">
              Опишите задачу — агент выполнит шаги на платформе и ответит текстом.
            </p>
          )}
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

        <form
          className="ai-agent-chat-compose"
          onSubmit={(e) => {
            e.preventDefault();
            void sendMessage(input);
          }}
        >
          <textarea
            rows={2}
            value={input}
            placeholder="Сообщение агенту…"
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                e.currentTarget.form?.requestSubmit();
              }
            }}
            disabled={sending || !chatEnabled}
          />
          <button
            type="submit"
            className="btn primary"
            disabled={sending || !chatEnabled || !input.trim()}
          >
            Отправить
          </button>
        </form>
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
