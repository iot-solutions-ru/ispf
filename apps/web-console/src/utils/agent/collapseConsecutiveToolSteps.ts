import type { AiAgentStep } from "../../api/ai";

export type CollapsedToolStep = {
  key: string;
  step: AiAgentStep;
  count: number;
  status: string;
};

/**
 * Collapse consecutive OK tool steps with the same tool name into one row (×N).
 * ERROR steps are never collapsed — each stays visible in the Studio log.
 */
export function collapseConsecutiveToolSteps(steps: AiAgentStep[]): CollapsedToolStep[] {
  const out: CollapsedToolStep[] = [];
  for (const step of steps) {
    const status = String(step.result?.status ?? "");
    const tool = String(step.tool ?? "");
    const last = out[out.length - 1];
    if (
      last
      && status === "OK"
      && last.status === "OK"
      && last.step.tool === tool
    ) {
      last.count += 1;
      continue;
    }
    out.push({
      key: `${step.step}-${tool}-${status}`,
      step,
      count: 1,
      status,
    });
  }
  return out;
}
