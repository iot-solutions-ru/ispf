import type { AiAgentStep } from "../../api/ai";

export interface AgentGraphNode {
  id: string;
  step: number;
  type: string;
  label: string;
  tool?: string;
  status?: string;
}

export interface AgentGraphEdge {
  from: string;
  to: string;
}

export function buildAgentTurnGraph(steps: AiAgentStep[]): {
  nodes: AgentGraphNode[];
  edges: AgentGraphEdge[];
} {
  const nodes: AgentGraphNode[] = [];
  const edges: AgentGraphEdge[] = [];
  let previousId: string | null = null;

  const ordered = [...steps].sort((a, b) => a.step - b.step);
  for (const step of ordered) {
    const id = `step-${step.step}`;
    const status =
      step.type === "tool"
        ? step.result?.status === "ERROR"
          ? "ERROR"
          : step.result?.status === "OK"
            ? "OK"
            : undefined
        : step.type === "error"
          ? "ERROR"
          : step.type === "finish"
            ? "OK"
            : undefined;
    nodes.push({
      id,
      step: step.step,
      type: step.type,
      label: step.label ?? step.tool ?? step.type,
      tool: step.tool,
      status,
    });
    if (previousId) {
      edges.push({ from: previousId, to: id });
    }
    previousId = id;
  }

  return { nodes, edges };
}
