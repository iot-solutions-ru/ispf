import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, screen, waitFor } from "@testing-library/react";
import type { ObjectEditorDto, ObjectSummary, ObjectType } from "../../types";
import { renderWithInspector, testI18n } from "../../test/renderWithInspector";
import enWorkflow from "../../locales/en/workflow.json";
import * as api from "../../api";
import IspfBpmnPropertiesPanel from "./IspfBpmnPropertiesPanel";
import type { FlowNode } from "../../bpmn/model/types";

vi.mock("../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../api")>();
  return {
    ...actual,
    fetchObjects: vi.fn(),
    fetchObjectEditor: vi.fn(),
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

function PanelHarness({ initial }: { initial: FlowNode }) {
  const [node, setNode] = useState(initial);
  return (
    <IspfBpmnPropertiesPanel
      selection={{ kind: "node", node }}
      onChangeNode={(_id, patch) =>
        setNode((current) => ({
          ...current,
          ...patch,
          ispf: patch.ispf ? { ...current.ispf, ...patch.ispf } : current.ispf,
        }))
      }
      onChangeEdge={vi.fn()}
      onDelete={vi.fn()}
    />
  );
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

const invokeTask: FlowNode = {
  id: "task_invoke",
  type: "serviceTask",
  name: "Invoke",
  x: 0,
  y: 0,
  width: 100,
  height: 80,
  ispf: { action: "invoke_function", objectPath: "root.plant-hub", functionName: "", inputMap: "" },
};

describe("IspfBpmnPropertiesPanel targetObject", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    testI18n.addResourceBundle("en", "workflow", enWorkflow, true, true);
    vi.mocked(api.fetchObjectEditor).mockResolvedValue({} as ObjectEditorDto);
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

describe("IspfBpmnPropertiesPanel descriptors", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    testI18n.addResourceBundle("en", "workflow", enWorkflow, true, true);
    vi.mocked(api.fetchObjectEditor).mockResolvedValue({
      functions: [{ name: "acknowledge" }],
      variables: [{ name: "mode" }],
      events: [{ name: "alarm" }],
    } as ObjectEditorDto);
  });

  it("lists functions from the object descriptor", async () => {
    const onChangeNode = vi.fn();
    const user = userEvent.setup();
    renderWithInspector(
      <IspfBpmnPropertiesPanel
        selection={{ kind: "node", node: invokeTask }}
        onChangeNode={onChangeNode}
        onChangeEdge={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    const select = await screen.findByRole("combobox", { name: "ispf:functionName" });
    await waitFor(() => expect(screen.getByRole("option", { name: "acknowledge" })).toBeInTheDocument());
    await user.selectOptions(select, "acknowledge");
    expect(onChangeNode).toHaveBeenCalledWith("task_invoke", {
      ispf: expect.objectContaining({ functionName: "acknowledge" }),
    });
    expect(api.fetchObjectEditor).toHaveBeenCalledWith("root.plant-hub");
  });

  it("lists setVariable names from targetObject when a stale objectPath remains", async () => {
    vi.mocked(api.fetchObjectEditor).mockImplementation(async (path) => {
      if (path === "root.plant-hub") {
        return { variables: [{ name: "mode" }] } as ObjectEditorDto;
      }
      return { variables: [{ name: "stale" }] } as ObjectEditorDto;
    });
    const setTask: FlowNode = {
      ...invokeTask,
      id: "task_set",
      ispf: {
        action: "setVariable",
        objectPath: "root.stale",
        targetObject: "root.plant-hub",
        variable: "",
      },
    };
    renderWithInspector(
      <IspfBpmnPropertiesPanel
        selection={{ kind: "node", node: setTask }}
        onChangeNode={vi.fn()}
        onChangeEdge={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    await waitFor(() => expect(screen.getByRole("option", { name: "mode" })).toBeInTheDocument());
    expect(screen.queryByRole("option", { name: "stale" })).not.toBeInTheDocument();
    expect(api.fetchObjectEditor).toHaveBeenCalledWith("root.plant-hub");
    expect(api.fetchObjectEditor).not.toHaveBeenCalledWith("root.stale");
  });

  it("falls back to free-text input when descriptor has no matching names", async () => {
    vi.mocked(api.fetchObjectEditor).mockResolvedValue({ functions: [] } as unknown as ObjectEditorDto);
    const user = userEvent.setup();
    renderWithInspector(<PanelHarness initial={invokeTask} />);

    const input = await screen.findByRole("textbox", { name: "ispf:functionName" });
    await user.type(input, "customFn");
    expect(input).toHaveValue("customFn");
    expect(screen.getByText("This object's descriptor has no functions.")).toBeInTheDocument();
  });

  it("falls back to free-text input when descriptor fetch fails", async () => {
    vi.mocked(api.fetchObjectEditor).mockRejectedValue(new Error("404 Not Found"));
    const user = userEvent.setup();
    renderWithInspector(<PanelHarness initial={invokeTask} />);

    const input = await screen.findByRole("textbox", { name: "ispf:functionName" });
    await user.type(input, "offlineFn");
    expect(input).toHaveValue("offlineFn");
  });

  it("flags an inputMap that is neither pairs nor a scalar JSON object", async () => {
    const user = userEvent.setup();
    renderWithInspector(<PanelHarness initial={invokeTask} />);

    await user.type(screen.getByRole("textbox", { name: "ispf:inputMap" }), "not-a-pair");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "inputMap entries are key=value pairs separated by commas.",
    );
  });

  it("flags payloadJson that is not an object", async () => {
    const user = userEvent.setup();
    const fireTask: FlowNode = {
      ...invokeTask,
      id: "task_fire",
      ispf: { action: "fire_event", objectPath: "root.plant-hub", eventName: "", payloadJson: "" },
    };
    renderWithInspector(<PanelHarness initial={fireTask} />);

    await user.type(screen.getByRole("textbox", { name: "ispf:payloadJson" }), "[[1]");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "payloadJson values must be strings, numbers, or booleans.",
    );
  });
});
