import { describe, expect, it, vi, afterEach } from "vitest";
import { cleanup, fireEvent, screen } from "@testing-library/react";
import { KeyValueEditor, StringListEditor } from "./widgetEditorStructured";
import { FieldPairs, Section } from "./widgetFields/widgetFieldPrimitives";
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

    expect(screen.getByRole("textbox", { name: "key" })).toHaveValue("");
    expect(screen.getByRole("textbox", { name: "value" })).toHaveValue("");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("stores the pair once the name is entered", () => {
    const onChange = vi.fn();
    renderWithDashboard(
      <KeyValueEditor label="paramBindingsJson" value={undefined} onChange={onChange} />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Add pair" }));
    fireEvent.change(screen.getByRole("textbox", { name: "key" }), { target: { value: "file_name" } });
    fireEvent.change(screen.getByRole("textbox", { name: "value" }), { target: { value: "fileName" } });

    expect(onChange).toHaveBeenLastCalledWith('{\n  "file_name": "fileName"\n}');
  });

  it("lets the form field be chosen only from the given names", () => {
    const onChange = vi.fn();
    renderWithDashboard(
      <KeyValueEditor
        label="paramBindingsJson"
        keyCaption="Поле формы"
        keyOptions={["file_name", "qty"]}
        value={undefined}
        onChange={onChange}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Add pair" }));
    const field = screen.getByRole("combobox", { name: "Поле формы" });
    expect(screen.queryByRole("textbox", { name: "Поле формы" })).toBeNull();
    expect([...field.querySelectorAll("option")].map((option) => option.textContent)).toEqual([
      "—",
      "file_name",
      "qty",
    ]);

    fireEvent.change(field, { target: { value: "qty" } });
    expect(onChange).toHaveBeenLastCalledWith('{\n  "qty": ""\n}');
  });

  it("offers session param names already used on the dashboard", async () => {
    const onChange = vi.fn();
    renderWithDashboard(
      <KeyValueEditor
        label="paramBindingsJson"
        valueCaption="Параметр сессии"
        valueSuggestions={["fileName", "orderId"]}
        value={undefined}
        onChange={onChange}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Add pair" }));
    fireEvent.change(screen.getByRole("textbox", { name: "key" }), { target: { value: "file_name" } });
    const valueInput = screen.getByRole("combobox", { name: "Параметр сессии" });
    fireEvent.mouseDown(valueInput);
    const listbox = await screen.findByRole("listbox");
    expect(
      [...listbox.querySelectorAll("[role='option']")].map((option) => option.textContent),
    ).toEqual(["fileName", "orderId"]);

    fireEvent.change(valueInput, { target: { value: "fileName" } });
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

describe("widget editor block titles", () => {
  afterEach(cleanup);

  it("moves a parenthetical description out of a field title", () => {
    renderWithDashboard(
      <FieldPairs>
        <label>
          {"Max points (maxPoints)"}
          <input />
        </label>
      </FieldPairs>,
    );
    expect(screen.getByText("Max points")).toBeInTheDocument();
    expect(screen.queryByText("Max points (maxPoints)")).not.toBeInTheDocument();
  });

  it("moves a section description onto the heading", () => {
    renderWithDashboard(<Section title="Multi-tag analytics query (BL-206)" hint="Tags replace objectPath." />);
    expect(screen.getByRole("heading", { name: "Multi-tag analytics query" })).toBeInTheDocument();
    expect(screen.queryByText("Tags replace objectPath.")).not.toBeInTheDocument();
    expect(screen.queryByText("BL-206")).not.toBeInTheDocument();
  });

  it("moves a parenthetical description out of a structured block title", () => {
    renderWithDashboard(
      <KeyValueEditor label="inputJson (static input)" value={undefined} onChange={() => {}} />,
    );
    expect(screen.getByText("inputJson")).toBeInTheDocument();
    expect(screen.queryByText("inputJson (static input)")).not.toBeInTheDocument();
  });
});
