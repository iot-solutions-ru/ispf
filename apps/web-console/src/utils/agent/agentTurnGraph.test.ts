import { describe, expect, it } from "vitest";
import { buildAgentTurnGraph } from "./agentTurnGraph";

describe("buildAgentTurnGraph", () => {
  it("builds nodes and edges in step order", () => {
    const graph = buildAgentTurnGraph([
      { step: 2, type: "tool", tool: "list_objects", result: { status: "OK" } },
      { step: 1, type: "guard", label: "Blocked" },
      { step: 3, type: "finish", summary: "done" },
    ]);
    expect(graph.nodes.map((n) => n.step)).toEqual([1, 2, 3]);
    expect(graph.edges).toHaveLength(2);
    expect(graph.edges[0]).toEqual({ from: "step-guard-1-0", to: "step-tool-2-1" });
    expect(graph.edges[1]).toEqual({ from: "step-tool-2-1", to: "step-finish-3-2" });
  });

  it("maps llm status and keeps chronological order with tools", () => {
    const graph = buildAgentTurnGraph([
      { step: 1, type: "llm", label: "Model → call list_objects", result: { status: "OK" } },
      { step: 2, type: "tool", tool: "list_objects", result: { status: "OK" } },
      { step: 3, type: "llm", label: "Model reply", result: { status: "ERROR" } },
    ]);
    expect(graph.nodes.map((n) => n.type)).toEqual(["llm", "tool", "llm"]);
    expect(graph.nodes.map((n) => n.status)).toEqual(["OK", "OK", "ERROR"]);
    expect(graph.edges[0]).toEqual({ from: "step-llm-1-0", to: "step-tool-2-1" });
  });
});
