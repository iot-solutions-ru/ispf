import { Button } from "antd";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { OperatorAgentSuggestion } from "../../utils/operator/operatorAgentArtifacts";
import {
  pickRecommendedSuggestion,
  resolveAutoContinueSeconds,
} from "../../utils/agent/pickRecommendedSuggestion";

type AgentAutoContinueProps = {
  result?: Record<string, unknown>;
  suggestions: OperatorAgentSuggestion[];
  /** Only the latest agent bubble should auto-continue. */
  enabled: boolean;
  onSuggestMessage?: (message: string) => void;
  i18nNs?: "ai" | "operator";
};

/**
 * AI Studio Autopilot only: when the agent pauses with interactive suggestions,
 * count down and send the recommended (primary) option if the user does not answer.
 * Admin/Operator Copilot must pass enabled=false — assistive UIs wait for a click.
 */
export function AgentAutoContinue({
  result,
  suggestions,
  enabled,
  onSuggestMessage,
  i18nNs = "ai",
}: AgentAutoContinueProps) {
  const { t } = useTranslation(i18nNs);
  const recommended = pickRecommendedSuggestion(suggestions);
  const secondsTotal =
    recommended != null ? resolveAutoContinueSeconds(result) : 0;
  const [remaining, setRemaining] = useState(secondsTotal);
  const [cancelled, setCancelled] = useState(false);
  const firedRef = useRef(false);

  useEffect(() => {
    setRemaining(secondsTotal);
    setCancelled(false);
    firedRef.current = false;
  }, [secondsTotal, recommended?.message]);

  useEffect(() => {
    if (!enabled || cancelled || !recommended?.message || secondsTotal <= 0 || !onSuggestMessage) {
      return;
    }
    if (remaining <= 0) {
      if (!firedRef.current) {
        firedRef.current = true;
        onSuggestMessage(recommended.message);
      }
      return;
    }
    const timer = window.setTimeout(() => setRemaining((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [enabled, cancelled, recommended?.message, remaining, secondsTotal, onSuggestMessage]);

  if (!enabled || cancelled || !recommended?.message || secondsTotal <= 0) {
    return null;
  }

  return (
    <div className="agent-auto-continue" role="status">
      <p className="hint">
        {t("agent.autoContinue.countdown", {
          label: recommended.label || recommended.message,
          seconds: remaining,
        })}
      </p>
      <Button size="small" onClick={() => setCancelled(true)}>
        {t("agent.autoContinue.cancel")}
      </Button>
    </div>
  );
}
