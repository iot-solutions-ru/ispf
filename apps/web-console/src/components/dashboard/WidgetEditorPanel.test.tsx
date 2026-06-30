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
});
