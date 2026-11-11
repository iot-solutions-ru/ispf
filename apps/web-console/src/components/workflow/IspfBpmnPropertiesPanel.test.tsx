import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, screen, waitFor } from "@testing-library/react";
import type { ObjectSummary, ObjectType } from "../../types";
import { renderWithInspector } from "../../test/renderWithInspector";
import * as api from "../../api";
import IspfBpmnPropertiesPanel from "./IspfBpmnPropertiesPanel";
import type { FlowNode } from "../../bpmn/model/types";

vi.mock("../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../api")>();
  return {
    ...actual,
    fetchObjects: vi.fn(),
  };
});

function object(path: string, type: ObjectType, displayName: string): ObjectSummary {
  return {
    id: path,
    path,
    type,
    displayName,
    description: "",
    templateId: null,
    createdAt: "",
    sortOrder: 0,
    variableNames: [],
    eventNames: [],
  };
}

const serviceTask: FlowNode = {
  id: "task_set",
  type: "serviceTask",
  name: "Set mode",
  x: 0,
  y: 0,
  width: 100,
  height: 80,
  ispf: { action: "setVariable", targetObject: "", variable: "mode", value: "auto" },
};

describe("IspfBpmnPropertiesPanel targetObject", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.mocked(api.fetchObjects).mockImplementation(async (parent) => {
      if (parent === "root") {
        return [
          object("root.plant-hub", "CUSTOM", "Plant hub"),
          object("root.sensor", "DEVICE", "Sensor"),
          object("root.board", "DASHBOARD", "Board"),
        ];
      }
      return [];
    });
  });

  it("lets setVariable pick a CUSTOM hub and keeps a dashboard out", async () => {
    const onChangeNode = vi.fn();
    const user = userEvent.setup();
    renderWithInspector(
      <IspfBpmnPropertiesPanel
        selection={{ kind: "node", node: serviceTask }}
        onChangeNode={onChangeNode}
        onChangeEdge={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Browse tree" }));
    expect(await screen.findByText("Selectable types: CUSTOM, DEVICE, APPLICATION")).toBeInTheDocument();

    await user.click(await screen.findByTitle("root.board"));
    expect(onChangeNode).not.toHaveBeenCalled();

    await user.click(await screen.findByTitle("root.plant-hub"));
    await waitFor(() =>
      expect(onChangeNode).toHaveBeenCalledWith("task_set", {
        ispf: expect.objectContaining({ targetObject: "root.plant-hub" }),
      }),
    );
  });

  it("writes a start_workflow trigger to objectPath", async () => {
    const onChangeNode = vi.fn();
    const user = userEvent.setup();
    const startTask: FlowNode = {
      ...serviceTask,
      id: "task_start",
      ispf: { action: "start_workflow", workflowPath: "root.platform.workflows.child" },
    };
    renderWithInspector(
      <IspfBpmnPropertiesPanel
        selection={{ kind: "node", node: startTask }}
        onChangeNode={onChangeNode}
        onChangeEdge={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    const browse = screen.getAllByRole("button", { name: "Browse tree" });
    await user.click(browse[1]);
    await user.click(await screen.findByTitle("root.plant-hub"));
    await waitFor(() =>
      expect(onChangeNode).toHaveBeenCalledWith("task_start", {
        ispf: expect.objectContaining({ objectPath: "root.plant-hub" }),
      }),
    );
  });

  it("shows errorCode instead of durationSeconds for error boundary events", async () => {
    const onChangeNode = vi.fn();
    const user = userEvent.setup();
    const errorBoundary: FlowNode = {
      id: "boundary_err",
      type: "boundaryEvent",
      name: "On lock",
      x: 0,
      y: 0,
      width: 36,
      height: 36,
      catchKind: "error",
      attachedToRef: "task_set",
      cancelActivity: true,
      ispf: { errorCode: "E_LOCKED" },
    };

    renderWithInspector(
      <IspfBpmnPropertiesPanel
        selection={{ kind: "node", node: errorBoundary }}
        onChangeNode={onChangeNode}
        onChangeEdge={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    expect(screen.queryByLabelText("ispf:durationSeconds")).not.toBeInTheDocument();
    const codeInput = screen.getByLabelText("ispf:errorCode");
    expect(codeInput).toHaveValue("E_LOCKED");

    await user.type(codeInput, "_2");
    expect(onChangeNode).toHaveBeenCalledWith("boundary_err", {
      ispf: expect.objectContaining({ errorCode: "E_LOCKED_" }),
    });
  });
});
