import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, screen, waitFor } from "@testing-library/react";
import type { ObjectEditorDto } from "../../types";
import { renderWithInspector, testI18n } from "../../test/renderWithInspector";
import enWorkflow from "../../locales/en/workflow.json";
import * as api from "../../api";
import IspfBpmnPropertiesPanel from "./IspfBpmnPropertiesPanel";
import type { FlowNode } from "../../bpmn/model/types";

vi.mock("../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../api")>();
  return {
    ...actual,
    fetchObjectEditor: vi.fn(),
  };
});

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

    await user.type(screen.getByRole("textbox", { name: "ispf:payloadJson" }), "[1]");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "payloadJson values must be strings, numbers, or booleans.",
    );
  });
});
