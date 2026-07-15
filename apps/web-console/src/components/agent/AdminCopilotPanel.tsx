import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useAdminCopilotChat } from "../../context/AdminCopilotChatContext";
import {
  formatAdminFocusChip,
  useAdminFocusOptional,
  type AdminClientFocus,
} from "../../context/AdminFocusContext";
import { AgentRunDetails } from "./AgentRunDetails";
import AgentChatArtifacts, { AgentStarterSuggestions } from "./AgentChatArtifacts";
import { AgentChatMessageBody } from "../../utils/agentChatMarkdown";

interface AdminCopilotPanelProps {
  open: boolean;
  onClose: () => void;
}

function enrichMessageWithFocus(text: string, focus: AdminClientFocus | null): string {
  const trimmed = text.trim();
  if (!trimmed || !focus) {
    return trimmed;
  }
  const detail = focus.detail ?? {};
  const expression = typeof detail.expression === "string" ? detail.expression.trim() : "";
  const ruleId = typeof detail.ruleId === "string" ? detail.ruleId.trim() : "";
  const path = focus.objectPath?.trim() ?? "";
  const parts: string[] = [trimmed];
  if (expression && !trimmed.includes(expression.slice(0, Math.min(24, expression.length)))) {
    parts.push(
      ruleId
        ? `Текущее CEL правила «${ruleId}»:\n\`\`\`\n${expression}\n\`\`\``
        : `Текущее CEL в редакторе:\n\`\`\`\n${expression}\n\`\`\``
    );
  }
  if (path && !trimmed.includes(path) && focus.surface !== "expression-editor") {
    parts.push(`Объект UI focus: \`${path}\``);
  }
  if (
    focus.surface === "binding" &&
    Array.isArray(detail.rules) &&
    detail.rules.length > 0 &&
    !/cluster-error|member\d-sine|rules?/i.test(trimmed)
  ) {
    const lines = (detail.rules as Array<Record<string, unknown>>)
      .slice(0, 8)
      .map((rule) => `- ${String(rule.id ?? "?")} → ${String(rule.target ?? "")} :: ${String(rule.expression ?? "")}`);
    parts.push(`Правила на экране:\n${lines.join("\n")}`);
  }
  return parts.join("\n\n");
}

function suggestionKeysForFocus(focus: AdminClientFocus | null): string[] {
  const surface = focus?.surface;
  switch (surface) {
    case "expression-editor":
      return [
        "copilot.suggest.explainExpression",
        "copilot.suggest.bindings",
        "copilot.suggest.systemLookup",
      ];
    case "binding-rule":
      return [
        "copilot.suggest.explainRule",
        "copilot.suggest.bindings",
        "copilot.suggest.systemLookup",
      ];
    case "binding":
      return [
        "copilot.suggest.explainComputations",
        "copilot.suggest.bindings",
        "copilot.suggest.systemLookup",
      ];
    case "properties":
      return [
        "copilot.suggest.explain",
        "copilot.suggest.systemLookup",
        "copilot.suggest.bindings",
      ];
    case "dashboard":
      return [
        "copilot.suggest.explain",
        "copilot.suggest.dashboard",
        "copilot.suggest.systemLookup",
      ];
    case "mimic":
      return [
        "copilot.suggest.explain",
        "copilot.suggest.scada",
        "copilot.suggest.systemLookup",
      ];
    default:
      return [
        "copilot.suggest.explain",
        "copilot.suggest.bindings",
        "copilot.suggest.systemLookup",
      ];
  }
}

export default function AdminCopilotPanel({ open, onClose }: AdminCopilotPanelProps) {
  const { t } = useTranslation(["ai", "operator"]);
  const {
    provider,
    providerLoading,
    agentApiReady,
    messages,
    isPending,
    liveSteps,
    sendMessage,
    cancelRun,
    startNewChat,
  } = useAdminCopilotChat();
  const focusRegistry = useAdminFocusOptional();
  const [input, setInput] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const wasOpen = useRef(false);
  useEffect(() => {
    if (open && !wasOpen.current) {
      // Fresh helper session each time the drawer opens.
      startNewChat();
    }
    wasOpen.current = open;
  }, [open, startNewChat]);

  useEffect(() => {
    if (open) {
      scrollRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [open, messages.length, liveSteps.length, isPending]);

  const providerReady = provider?.available ?? false;
  const focus = focusRegistry?.focus ?? null;
  const chip = formatAdminFocusChip(focus, focusRegistry?.focusTrail);
  const toolStepCount = liveSteps.filter((s) => s.type === "tool").length;
  const suggestionKeys = useMemo(() => suggestionKeysForFocus(focus), [focus]);

  const handleSend = useCallback(
    async (text: string) => {
      const trimmed = enrichMessageWithFocus(text, focusRegistry?.focus ?? null);
      if (!trimmed || isPending || !providerReady || !agentApiReady) {
        return;
      }
      setInput("");
      await sendMessage(trimmed, {
        clientFocus: focusRegistry?.toClientFocusPayload() ?? null,
      });
    },
    [agentApiReady, focusRegistry, isPending, providerReady, sendMessage]
  );

  const openToken = focusRegistry?.copilotOpenToken ?? 0;
  const lastAutoPromptToken = useRef(0);
  useEffect(() => {
    if (!open || openToken <= 0 || openToken === lastAutoPromptToken.current) {
      return;
    }
    if (isPending || !providerReady || !agentApiReady) {
      return;
    }
    lastAutoPromptToken.current = openToken;
    const prompt = focusRegistry?.takePendingCopilotPrompt() ?? null;
    if (!prompt) {
      return;
    }
    void handleSend(prompt);
  }, [
    open,
    openToken,
    focusRegistry,
    isPending,
    providerReady,
    agentApiReady,
    handleSend,
  ]);

  const appendToInput = useCallback((text: string) => {
    const trimmed = text.trim();
    if (!trimmed) {
      return;
    }
    setInput((prev) => {
      const base = prev.trimEnd();
      return base ? `${base}\n${trimmed}` : trimmed;
    });
    requestAnimationFrame(() => inputRef.current?.focus());
  }, []);

  if (!open) {
    return null;
  }

  return (
    <div className="admin-copilot-drawer operator-agent-drawer" role="dialog" aria-label={t("ai:copilot.title")}>
      <div className="operator-agent-drawer-head">
        <div>
          <strong>{t("ai:copilot.title")}</strong>
          <p className="op-muted operator-agent-drawer-sub">{t("ai:copilot.subtitle")}</p>
          <p className="op-muted operator-agent-drawer-sub">
            {chip || t("ai:copilot.focusNone")}
          </p>
        </div>
        <div className="admin-copilot-head-actions">
          <button
            type="button"
            className="btn small"
            disabled={isPending || messages.length === 0}
            onClick={() => startNewChat()}
            title={t("ai:copilot.newChat")}
          >
            {t("ai:copilot.newChatShort")}
          </button>
          <button type="button" className="btn small" onClick={onClose} aria-label={t("ai:copilot.close")}>
            ×
          </button>
        </div>
      </div>

      {!providerReady && !providerLoading && (
        <div className="op-alert op-alert-error">{t("ai:copilot.llmUnavailable")}</div>
      )}

      <div className="admin-copilot-mode-row">
        <span className="hint">{t("ai:copilot.modeLocked")}</span>
      </div>

      <div className="operator-agent-drawer-log">
        {!messages.some((m) => m.role === "user") && !isPending && (
          <AgentStarterSuggestions
            i18nNs="ai"
            suggestionKeys={suggestionKeys}
            onPick={(text) => void handleSend(text)}
          />
        )}
        {messages.map((message) => (
          <div
            key={message.id}
            className={message.role === "user" ? "ai-agent-bubble user" : "ai-agent-bubble agent"}
          >
            <div className="ai-agent-bubble-text">
              {message.role === "agent" ? (
                <AgentChatMessageBody text={message.text} />
              ) : (
                message.text
              )}
            </div>
            {message.role === "agent" && (
              <AgentChatArtifacts
                result={message.result}
                onSuggestMessage={(text: string) => void handleSend(text)}
                onAppendToInput={appendToInput}
              />
            )}
            {message.steps && message.steps.length > 0 && (
              <AgentRunDetails steps={message.steps} status={message.status} />
            )}
          </div>
        ))}
        {isPending && (
          <div className="ai-agent-bubble agent ai-agent-bubble-pending">
            <div className="ai-agent-bubble-text">
              {toolStepCount > 0
                ? t("operator:agent.executing", { count: toolStepCount })
                : t("ai:copilot.thinking")}
            </div>
            <AgentRunDetails steps={liveSteps} status="RUNNING" open placeholder={toolStepCount === 0} />
          </div>
        )}
        <div ref={scrollRef} />
      </div>

      <form
        className="operator-agent-drawer-compose"
        onSubmit={(e) => {
          e.preventDefault();
          void handleSend(input);
        }}
      >
        <textarea
          ref={inputRef}
          rows={2}
          value={input}
          placeholder={t("ai:copilot.placeholder")}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              e.currentTarget.form?.requestSubmit();
            }
          }}
          disabled={isPending || !providerReady || !agentApiReady}
        />
        <div className="operator-agent-drawer-actions">
          {isPending && (
            <button type="button" className="btn danger" onClick={() => void cancelRun()}>
              {t("ai:copilot.cancel")}
            </button>
          )}
          <button
            type="submit"
            className="btn primary"
            disabled={isPending || !providerReady || !agentApiReady || !input.trim()}
          >
            {t("ai:copilot.send")}
          </button>
        </div>
      </form>
    </div>
  );
}
