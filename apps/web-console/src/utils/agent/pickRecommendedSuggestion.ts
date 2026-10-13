import type { OperatorAgentSuggestion } from "../operator/operatorAgentArtifacts";
import { isPlanApprovalSuggestion } from "../operator/operatorAgentArtifacts";

export const DEFAULT_AUTO_CONTINUE_SECONDS = 45;

/** Pick the recommended (primary) clarify option — skips plan-approval buttons. */
export function pickRecommendedSuggestion(
  suggestions: OperatorAgentSuggestion[] | undefined
): OperatorAgentSuggestion | null {
  if (!suggestions?.length) {
    return null;
  }
  const primary = suggestions.find(
    (item) => item?.primary && item.message?.trim() && !isPlanApprovalSuggestion(item)
  );
  return primary ?? null;
}

export function resolveAutoContinueSeconds(
  result: Record<string, unknown> | undefined,
  fallback = DEFAULT_AUTO_CONTINUE_SECONDS
): number {
  const raw = result?.autoContinueSeconds;
  if (typeof raw === "number" && Number.isFinite(raw) && raw > 0) {
    return Math.min(300, Math.max(5, Math.floor(raw)));
  }
  if (typeof raw === "string" && raw.trim()) {
    const parsed = Number(raw);
    if (Number.isFinite(parsed) && parsed > 0) {
      return Math.min(300, Math.max(5, Math.floor(parsed)));
    }
  }
  // Only when the agent explicitly awaits a choice (not post-success open-dashboard chips).
  if (result?.awaitingUser === true) {
    return fallback;
  }
  return 0;
}
