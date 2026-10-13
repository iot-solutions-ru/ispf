import { describe, expect, it } from "vitest";
import type { AiAgentStep } from "../../api/ai";
import { collapseConsecutiveToolSteps } from "./collapseConsecutiveToolSteps";

function tool(step: number, name: string, status: string, error?: string): AiAgentStep {
  return {
    step,
    type: "tool",
    tool: name,
    result: error ? { status, error } : { status },
  };
}

describe("collapseConsecutiveToolSteps", () => {
  it("collapses consecutive OK same tool", () => {
    const collapsed = collapseConsecutiveToolSteps([
      tool(1, "add_dashboard_widget", "OK"),
      tool(2, "add_dashboard_widget", "OK"),
      tool(3, "add_dashboard_widget", "OK"),
      tool(4, "set_dashboard_layout", "OK"),
    ]);
    expect(collapsed).toHaveLength(2);
    expect(collapsed[0]?.count).toBe(3);
    expect(collapsed[0]?.step.tool).toBe("add_dashboard_widget");
    expect(collapsed[1]?.count).toBe(1);
  });

  it("does not collapse ERROR steps", () => {
    const collapsed = collapseConsecutiveToolSteps([
      tool(1, "create_binding_rule", "ERROR", "path, id, expression are required"),
      tool(2, "create_binding_rule", "ERROR", "path, id, expression are required"),
    ]);
    expect(collapsed).toHaveLength(2);
    expect(collapsed.every((row) => row.count === 1)).toBe(true);
  });
});
