import { Button } from "antd";
import { useTranslation } from "react-i18next";
import { useAgentRunStatus } from "../../utils/agent/agentRunStatus";

interface AgentChatStatusBarProps {
  workspaceTab: string;
  onOpenAiStudio: () => void;
}

export default function AgentChatStatusBar({ workspaceTab, onOpenAiStudio }: AgentChatStatusBarProps) {
  const { t } = useTranslation("shell");
  const { isPending, pendingUserMessage } = useAgentRunStatus();
  if (!isPending) {
    return null;
  }

  const onAgentTab = workspaceTab === "ai-studio";
  if (onAgentTab) {
    return null;
  }

  const pendingMessage = pendingUserMessage ?? "";
  const truncated =
    pendingMessage.length > 48 ? `${pendingMessage.slice(0, 48)}…` : pendingMessage;
  const label = pendingMessage
    ? t("agentStatusBar.runningWithMessage", { message: truncated })
    : t("agentStatusBar.running");

  return (
    <div className="ai-agent-status-bar" role="status">
      <span className="ai-agent-status-bar-pulse" aria-hidden />
      <span className="ai-agent-status-bar-text">{label}</span>
      <Button type="primary" size="small" onClick={onOpenAiStudio}>
        {t("agentStatusBar.openStudio")}
      </Button>
    </div>
  );
}
