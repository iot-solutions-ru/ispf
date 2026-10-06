import { describe, expect, it, vi, afterEach } from "vitest";
import { cleanup, fireEvent, screen } from "@testing-library/react";
import { KeyValueEditor, StringListEditor } from "./widgetEditorStructured";
import { renderWithDashboard } from "../../test/renderWithDashboard";

describe("KeyValueEditor", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows a blank row when adding a pair before the name is filled", () => {
    const onChange = vi.fn();
    renderWithDashboard(
      <KeyValueEditor label="paramBindingsJson" value={undefined} onChange={onChange} />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Add pair" }));

    expect(screen.getByPlaceholderText("key")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("value")).toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("stores the pair once the name is entered", () => {
    const onChange = vi.fn();
    renderWithDashboard(
      <KeyValueEditor label="paramBindingsJson" value={undefined} onChange={onChange} />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Add pair" }));
    fireEvent.change(screen.getByPlaceholderText("key"), { target: { value: "file_name" } });
    fireEvent.change(screen.getByPlaceholderText("value"), { target: { value: "fileName" } });

    expect(onChange).toHaveBeenLastCalledWith('{\n  "file_name": "fileName"\n}');
  });
});

describe("StringListEditor", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows a blank row when adding an item before it is filled", () => {
    const onChange = vi.fn();
    renderWithDashboard(
      <StringListEditor label="requireSessionParamsJson" value={undefined} onChange={onChange} />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Add" }));

    expect(screen.getByRole("textbox")).toHaveValue("");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("stores the item once it is filled", () => {
    const onChange = vi.fn();
    renderWithDashboard(
      <StringListEditor label="requireSessionParamsJson" value={undefined} onChange={onChange} />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Add" }));
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "fileName" } });

    expect(onChange).toHaveBeenLastCalledWith('[\n  "fileName"\n]');
  });
});
