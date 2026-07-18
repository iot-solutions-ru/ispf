import { useEffect } from "react";
import { fetchAgentRunProgress } from "../../api/ai";
import { loadAgentChatIndex } from "../../utils/agent/agentChatStorage";
import { publishAgentRunStatus } from "../../utils/agent/agentRunStatus";

/** Detects an in-flight agent run after reload without mounting the full chat UI. */
export default function AgentChatRunBootstrap({ enabled }: { enabled: boolean }) {
  useEffect(() => {
    if (!enabled) {
      publishAgentRunStatus({ isPending: false, pendingUserMessage: null });
      return;
    }
    const sessionId = loadAgentChatIndex("studio").activeSessionId;
    if (!sessionId) {
      return;
    }
    let cancelled = false;
    void fetchAgentRunProgress(sessionId)
      .then((progress) => {
        if (!cancelled && progress.running) {
          publishAgentRunStatus({ isPending: true, pendingUserMessage: null });
        }
      })
      .catch(() => {
        // ignore — session may be gone
      });
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  return null;
}
