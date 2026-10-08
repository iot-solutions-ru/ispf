import { describe, expect, it, vi, afterEach } from "vitest";
import { cleanup, fireEvent, screen, waitFor } from "@testing-library/react";
import WidgetEditorPanel from "./WidgetEditorPanel";
import { DASHBOARD_COLUMNS, newWidget } from "../../types/dashboard";
import { renderWithDashboard } from "../../test/renderWithDashboard";

describe("WidgetEditorPanel", () => {
  const onChange = vi.fn();
  const onWidgetsChange = vi.fn();
  const onDelete = vi.fn();

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("opens the editor after the empty state", () => {
    const widget = { ...newWidget("value", 0), id: "value", title: "Temperature" };
    const { rerender } = renderWithDashboard(
      <WidgetEditorPanel
        widget={null}
        widgets={[]}
        objects={[]}
        onChange={onChange}
        onWidgetsChange={onWidgetsChange}
        onDelete={onDelete}
      />,
    );

    rerender(
      <WidgetEditorPanel
        widget={widget}
        widgets={[widget]}
        objects={[]}
        onChange={onChange}
        onWidgetsChange={onWidgetsChange}
        onDelete={onDelete}
      />,
    );

    expect(screen.getByDisplayValue("Temperature")).toBeInTheDocument();
    expect(screen.getByText("Where the choice is stored")).toBeInTheDocument();
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

  it("allows fine-grid x within layout.columns (demo sparkline column)", () => {
    const widget = { ...newWidget("value", 0), x: 0, w: 28 };

    renderWithDashboard(
      <WidgetEditorPanel
        widget={widget}
        widgets={[widget]}
        objects={[]}
        gridColumns={DASHBOARD_COLUMNS}
        onChange={onChange}
        onWidgetsChange={onWidgetsChange}
        onDelete={onDelete}
      />,
    );

    const xCaption = screen.getByText("x", { selector: "span.field-caption" });
    const xInput = xCaption.parentElement?.querySelector('input[type="number"]');
    expect(xInput).toBeTruthy();
    fireEvent.change(xInput!, { target: { value: "56" } });
    expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ x: 56 }));
  });

  it("does not coerce cleared layout fields to zero", async () => {
    const widget = { ...newWidget("value", 0), x: 2, y: 3, w: 4, h: 5 };

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

    const xCaption = screen.getByText("x", { selector: "span.field-caption" });
    const xInput = xCaption.parentElement?.querySelector('input[type="number"]');
    expect(xInput).toHaveValue(2);
    fireEvent.change(xInput!, { target: { value: "" } });
    expect(xInput).toHaveValue(null);
    expect(onChange).not.toHaveBeenCalled();

    fireEvent.blur(xInput!);
    await waitFor(() => expect(xInput).toHaveValue(2));
    expect(onChange).not.toHaveBeenCalled();
  });

  it("updates zIndex only for finite numbers", () => {
    const widget = { ...newWidget("value", 0), zIndex: 5 };

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

    const zCaption = screen.getByText("Z-index");
    const zInput = zCaption.parentElement?.querySelector("input");
    expect(zInput).toBeTruthy();

    fireEvent.change(zInput!, { target: { value: "7" } });
    expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ zIndex: 7 }));

    fireEvent.change(zInput!, { target: { value: "" } });
    expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ zIndex: undefined }));
    for (const call of onChange.mock.calls) {
      const z = (call[0] as { zIndex?: number }).zIndex;
      if (z !== undefined) {
        expect(Number.isFinite(z)).toBe(true);
      }
    }
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

  it("suggests selection keys already set on the dashboard and still accepts a new name", async () => {
    const widget = { ...newWidget("value", 0), id: "value" };
    const table = { ...newWidget("object-table", 1), id: "table", selectionKey: "order" };
    const cards = { ...newWidget("card-grid", 2), id: "cards", cardSelectionKey: "pump" };

    renderWithDashboard(
      <WidgetEditorPanel
        widget={widget}
        widgets={[widget, table, cards]}
        objects={[]}
        onChange={onChange}
        onWidgetsChange={onWidgetsChange}
        onDelete={onDelete}
      />,
    );

    const caption = screen.getByText("Where the choice is stored");
    const input = caption.parentElement?.querySelector("input");
    expect(input).toBeTruthy();

    fireEvent.mouseDown(input!);
    const listbox = await screen.findByRole("listbox");
    expect(
      [...listbox.querySelectorAll("[role='option']")].map((option) => option.textContent),
    ).toEqual(["order", "pump"]);

    fireEvent.change(input!, { target: { value: "batch" } });
    expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ selectionKey: "batch" }));
  });
});
