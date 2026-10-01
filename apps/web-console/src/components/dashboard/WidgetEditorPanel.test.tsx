import { describe, expect, it, vi, afterEach } from "vitest";
import { cleanup, fireEvent, screen } from "@testing-library/react";
import WidgetEditorPanel from "./WidgetEditorPanel";
import { newWidget } from "../../types/dashboard";
import { renderWithDashboard } from "../../test/renderWithDashboard";

describe("WidgetEditorPanel", () => {
  const onChange = vi.fn();
  const onWidgetsChange = vi.fn();
  const onDelete = vi.fn();

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows empty-state hint when no widget is selected", () => {
    renderWithDashboard(
      <WidgetEditorPanel
        widget={null}
        widgets={[]}
        objects={[]}
        onChange={onChange}
        onWidgetsChange={onWidgetsChange}
        onDelete={onDelete}
      />,
    );

    expect(screen.getByText(/Select a widget on the grid/i)).toBeInTheDocument();
  });

  it("updates widget title from editor form", async () => {
    const widget = { ...newWidget("label", 0), title: "Initial title" };

    renderWithDashboard(
      <WidgetEditorPanel
        widget={widget}
        widgets={[widget]}
        objects={[]}
        onChange={onChange}
        onWidgetsChange={onWidgetsChange}
        onDelete={onDelete}
      />,
    );

    const titleInput = screen.getByDisplayValue("Initial title");
    fireEvent.change(titleInput, { target: { value: "Updated title" } });

    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ title: "Updated title" }),
    );
  });

  it("binds object path with a typed field and tree picker, not a flat object list", () => {
    const widget = { ...newWidget("value", 0), objectPath: "root.platform.devices.pump-1" };

    renderWithDashboard(
      <WidgetEditorPanel
        widget={widget}
        widgets={[widget]}
        objects={[
          { path: "root.platform.devices.pump-1", displayName: "Pump 1", variableNames: ["level"] },
          { path: "root.platform.devices.pump-2", displayName: "Pump 2", variableNames: ["level"] },
        ]}
        onChange={onChange}
        onWidgetsChange={onWidgetsChange}
        onDelete={onDelete}
      />,
    );

    const pathField = document.querySelector(".path-select-field");
    expect(pathField).toBeTruthy();
    expect(pathField?.querySelector(".ant-select")).toBeNull();
    expect(pathField?.querySelector("input")).toHaveValue("root.platform.devices.pump-1");
    expect(pathField?.querySelector(".object-path-browse")).toBeTruthy();

    fireEvent.change(pathField!.querySelector("input")!, {
      target: { value: "root.platform.devices.pump-2" },
    });
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ objectPath: "root.platform.devices.pump-2", variableName: "" }),
    );
  });
});
