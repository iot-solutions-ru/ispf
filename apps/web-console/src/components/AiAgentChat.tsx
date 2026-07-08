import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { downloadAgentAuditCsv } from "../api/ai";
import { useAgentChat } from "../context/AgentChatContext";
import type { AgentInteractionMode } from "../api/ai";
import AgentChatArtifacts, { AgentStarterSuggestions } from "./agent/AgentChatArtifacts";
import AgentChatComposeAttachments, {
  AgentMessageAttachmentPreview,
} from "./agent/AgentChatComposeAttachments";
import { AgentChatMessageBody } from "../utils/agentChatMarkdown";
import AgentSessionKnowledgePanel from "./agent/AgentSessionKnowledgePanel";
import { AgentRunDetails } from "./agent/AgentRunDetails";
import {
  type AgentChatAttachment,
} from "../utils/agentChatAttachments";
import { formatUserDateTime } from "../utils/formatDateTime";
const CHAT_INPUT_MAX_HEIGHT_PX = 320;

function resizeChatInput(textarea: HTMLTextAreaElement | null) {
  if (!textarea) {
    return;
  }
  const maxHeight = Math.min(window.innerHeight * 0.4, CHAT_INPUT_MAX_HEIGHT_PX);
  textarea.style.height = "auto";
  const next = Math.min(textarea.scrollHeight, maxHeight);
  textarea.style.height = `${next}px`;
  textarea.style.overflowY = textarea.scrollHeight > maxHeight ? "auto" : "hidden";
}

function formatChatDate(iso: string): string {
  return formatUserDateTime(iso);
}

function AgentModeBadge({ mode }: { mode: AgentInteractionMode }) {
  const { t } = useTranslation("ai");
  return (
    <span
      className={`ai-agent-mode-badge ai-agent-mode-badge--${mode}`}
      title={t(`agent.mode.hint.${mode}`)}
    >
      {t(`agent.mode.${mode}`)}
    </span>
  );
}

export default function AiAgentChat() {
  const { t } = useTranslation("ai");
  const [auditExportBusy, setAuditExportBusy] = useState(false);
  const [input, setInput] = useState("");
  const [pendingAttachments, setPendingAttachments] = useState<AgentChatAttachment[]>([]);
  const [attachmentRejectHint, setAttachmentRejectHint] = useState<string | null>(null);
  const {
    provider,
    agentApiReady,
    agentApiBanner,
    chatIndex,
    activeSessionId,
    messages,
    loadingSession,
    isPending,
    liveSteps,
    livePlanPhase,
    startNewChat,
    switchSession,
    deleteChat,
    sendMessage,
    cancelRun,
    interactionMode,
    setInteractionMode,
  } = useAgentChat();
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const lastScrolledToolCountRef = useRef(0);
  const prevMessageCountRef = useRef(0);

  const clearAttachmentRejectHint = useCallback(() => {
    setAttachmentRejectHint(null);
  }, []);

  const rejectAttachment = useCallback((reason: "unsupported" | "vision-not-supported") => {
    setAttachmentRejectHint(
      reason === "vision-not-supported"
        ? t("agent.attachments.visionNotSupported")
        : t("agent.attachments.unsupported")
    );
  }, [t]);

  const submitMessage = useCallback(
    (text: string) => {
      const attachments = pendingAttachments;
      setPendingAttachments([]);
      void sendMessage(text, { attachments });
    },
    [pendingAttachments, sendMessage]
  );
  const appendToInput = useCallback(
    (text: string) => {
      const trimmed = text.trim();
      if (!trimmed) {
        return;
      }
      const base = input.trimEnd();
      setInput(base ? `${base}\n${trimmed}` : trimmed);
      requestAnimationFrame(() => {
        inputRef.current?.focus();
        resizeChatInput(inputRef.current);
      });
    },
    [input, setInput]
  );

  useEffect(() => {
    resizeChatInput(inputRef.current);
  }, [input]);

  useEffect(() => {
    if (!isPending) {
      lastScrolledToolCountRef.current = 0;
      prevMessageCountRef.current = messages.length;
      return;
    }
    const toolStepCount = liveSteps.filter((step) => step.type === "tool").length;
    const messagesChanged = messages.length !== prevMessageCountRef.current;
    if (!messagesChanged && toolStepCount === lastScrolledToolCountRef.current) {
      return;
    }
    lastScrolledToolCountRef.current = toolStepCount;
    prevMessageCountRef.current = messages.length;
    scrollRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [isPending, liveSteps, messages.length]);

  if (loadingSession) {
    return <p className="op-muted">{t("agent.loadingChat")}</p>;
  }

  const providerReady = provider?.available ?? false;
  const sending = isPending;
  const chatEnabled = providerReady && agentApiReady;
  const toolStepCount = liveSteps.filter((s) => s.type === "tool").length;
  const hasUserTurns = messages.some((message) => message.role === "user");

  const modeOptions: AgentInteractionMode[] = ["auto", "execute", "plan", "ask"];

  return (
    <div className="ai-agent-chat">
      {agentApiBanner && (
        <div className="op-alert op-alert-error" role="alert">
          {agentApiBanner}
        </div>
      )}

      {!providerReady && !agentApiBanner && (
        <div className="op-alert op-alert-error">
          {t("agent.llmNotConfigured")}
        </div>
      )}

      <div className="ai-agent-toolbar" role="toolbar" aria-label={t("agent.toolbarAria")}>
        <button
          type="button"
          className="btn primary ai-agent-new-chat"
          disabled={sending || !chatEnabled}
          onClick={() => {
            setInput("");
            setPendingAttachments([]);
            void startNewChat();
          }}
          title={t("agent.newChatTitle")}
        >
          <span className="ai-agent-new-chat-label">{t("agent.newChat")}</span>
        </button>
        {activeSessionId && (
          <button
            type="button"
            className="btn"
            disabled={auditExportBusy || !chatEnabled}
            onClick={() => {
              setAuditExportBusy(true);
              void downloadAgentAuditCsv(activeSessionId)
                .catch(() => undefined)
                .finally(() => setAuditExportBusy(false));
            }}
            title={t("agent.exportAuditTitle")}
          >
            {auditExportBusy ? t("agent.exportAuditBusy") : t("agent.exportAudit")}
          </button>
        )}
        <div className="ai-agent-chat-strip" role="tablist" aria-label={t("agent.toolbarAria")}>
          {chatIndex.chats.length === 0 && (
            <span className="ai-agent-chat-strip-empty op-muted">{t("agent.noChats")}</span>
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
                    <span className="ai-agent-chat-pending-dot" title={t("agent.pendingTitle")} />
                  )}
                </button>
                <button
                  type="button"
                  className="ai-agent-chat-delete"
                  aria-label={t("agent.deleteChatAria", { title: chat.title })}
                  disabled={sending}
                  onClick={() => void deleteChat(chat.id)}
                >
                  ×
                </button>
              </div>
            );
          })}
        </div>
        {activeSessionId && <AgentSessionKnowledgePanel sessionId={activeSessionId} />}
        {isPending && (
          <button
            type="button"
            className="btn danger ai-agent-cancel-run"
            disabled={!chatEnabled}
            onClick={() => void cancelRun()}
            title={t("agent.cancelTitle")}
          >
            {t("agent.cancel")}
          </button>
        )}
        {isPending && (
          <span className="ai-agent-toolbar-busy op-muted">
            {toolStepCount > 0
              ? t("agent.stepProgress", { count: toolStepCount })
              : t("agent.thinking")}
          </span>
        )}
      </div>

      {livePlanPhase === "awaiting_approval" && (
        <div className="ai-agent-approve-banner" role="status">
          <p>{t("agent.approveBanner")}</p>
          <button
            type="button"
            className="btn primary small"
            disabled={isPending}
            onClick={() => void submitMessage(t("agent.approveMessage"))}
          >
            {t("agent.approveAction")}
          </button>
        </div>
      )}

      <div className="ai-agent-chat-main">
        <div className="ai-agent-chat-log">
          {!hasUserTurns && !isPending && (
            <AgentStarterSuggestions
              i18nNs="ai"
              suggestionKeys={[
                "agent.suggest.explore",
                "agent.suggest.snmp",
                "agent.suggest.report",
                "agent.suggest.scada",
                "agent.suggest.bundle",
              ]}
              onPick={(text) => void submitMessage(text)}
            />
          )}
          {messages.map((message) => (
            <div
              key={message.id}
              className={
                message.role === "user" ? "ai-agent-bubble user" : "ai-agent-bubble agent"
              }
            >
              {message.role === "user" && message.interactionMode && (
                <AgentModeBadge mode={message.interactionMode} />
              )}
              <div className="ai-agent-bubble-text">
                {message.role === "agent" ? (
                  <AgentChatMessageBody text={message.text} />
                ) : (
                  message.text
                )}
              </div>
              {message.role === "user" && (
                <AgentMessageAttachmentPreview attachments={message.attachments} />
              )}
              {message.role === "agent" && (
                <AgentChatArtifacts
                  result={message.result}
                  i18nNs="ai"
                  onSuggestMessage={(text) => void submitMessage(text)}
                  onAppendToInput={appendToInput}
                />
              )}
              {message.steps && message.steps.length > 0 && (
                <AgentRunDetails
                  steps={message.steps}
                  status={message.status}
                  result={message.result}
                  sessionId={activeSessionId}
                  turnId={message.turnId}
                />
              )}
            </div>
          ))}
          {isPending && (
            <div className="ai-agent-bubble agent ai-agent-bubble-pending">
              <div className="ai-agent-bubble-text">
                {toolStepCount > 0
                  ? livePlanPhase === "planning" || livePlanPhase === "awaiting_approval"
                    ? t("agent.planning", {
                        count: toolStepCount,
                        steps: t("agent.step", { count: toolStepCount }),
                      })
                    : t("agent.executing", {
                        count: toolStepCount,
                        steps: t("agent.step", { count: toolStepCount }),
                      })
                  : t("agent.thinking")}
              </div>
              {isPending && (
                <AgentRunDetails
                  steps={liveSteps}
                  status="RUNNING"
                  open
                  placeholder={toolStepCount === 0}
                />
              )}
            </div>
          )}
          <div ref={scrollRef} />
        </div>

        <form
          className="ai-agent-chat-compose"
          onSubmit={(e) => {
            e.preventDefault();
            const text = input;
            setInput("");
            void submitMessage(text);
          }}        >
          <label className={`ai-agent-mode-select ai-agent-mode-select--compose ai-agent-mode-select--${interactionMode}`}>
            <span className="ai-agent-mode-label">{t("agent.mode.label")}</span>
            <select
              className={`ai-agent-mode-select-input ai-agent-mode-select-input--${interactionMode}`}
              value={interactionMode}
              disabled={sending || !chatEnabled}
              aria-label={t("agent.mode.label")}
              onChange={(event) => setInteractionMode(event.target.value as AgentInteractionMode)}
            >
              {modeOptions.map((mode) => (
                <option key={mode} value={mode} title={t(`agent.mode.hint.${mode}`)}>
                  {t(`agent.mode.${mode}`)}
                </option>
              ))}
            </select>
          </label>
          {attachmentRejectHint && (
            <div className="op-alert op-alert-warn ai-agent-attach-reject" role="status">
              {attachmentRejectHint}
              <button type="button" className="btn link small" onClick={clearAttachmentRejectHint}>
                {t("agent.attachments.dismiss")}
              </button>
            </div>
          )}
          <AgentChatComposeAttachments
            provider={provider}
            attachments={pendingAttachments}
            disabled={sending || !chatEnabled}
            onChange={setPendingAttachments}
            onReject={rejectAttachment}
          />
          <div className="ai-agent-chat-compose-row">
            <textarea
              ref={inputRef}
              rows={1}
              value={input}
              placeholder={t("agent.placeholder")}
              onChange={(e) => {
                setInput(e.target.value);
                resizeChatInput(e.target);
              }}
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
              disabled={sending || !chatEnabled || (!input.trim() && pendingAttachments.length === 0)}
            >
              {t("agent.send")}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}