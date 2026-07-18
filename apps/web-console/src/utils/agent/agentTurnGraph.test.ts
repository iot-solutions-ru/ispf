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
    expect(graph.edges[0]).toEqual({ from: "step-1", to: "step-2" });
  });
});
