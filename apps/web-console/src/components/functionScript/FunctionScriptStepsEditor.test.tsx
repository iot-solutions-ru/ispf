import { cleanup, fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useState } from "react";
import { renderWithInspector } from "../../test/renderWithInspector";
import FunctionScriptStepsEditor from "./FunctionScriptStepsEditor";

describe("FunctionScriptStepsEditor", () => {
  afterEach(cleanup);

  it("keeps invalid JSON visible instead of switching to the visual editor", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithInspector(
      <FunctionScriptStepsEditor
        value={'{"steps":[{"type":"return","fields":{"ok":true}}]}'}
        onChange={onChange}
      />,
    );

    const jsonToggle = screen.getByRole("checkbox", { name: "Show JSON" });
    await user.click(jsonToggle);
    const editor = screen.getByRole("textbox");
    fireEvent.change(editor, { target: { value: "{invalid" } });
    await user.click(jsonToggle);

    expect(jsonToggle).toBeChecked();
    expect(editor).toHaveValue("{invalid");
  });

  it("adds a fields row on a return step after clearing all pairs", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithInspector(
      <FunctionScriptStepsEditor
        value={'{"steps":[{"type":"return","fields":{"ok":true}}]}'}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Remove row" }));
    expect(screen.getAllByPlaceholderText("fieldName")).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: "+ Key/value pair" }));
    expect(screen.getAllByPlaceholderText("fieldName")).toHaveLength(2);
  });

  it("keeps focus while SQL is typed in an exec step (controlled parent)", async () => {
    const user = userEvent.setup();
    const initial = JSON.stringify(
      { steps: [{ type: "exec", sql: "SELECT 1", params: [] }] },
      null,
      2,
    );

    function Harness() {
      const [value, setValue] = useState(initial);
      return <FunctionScriptStepsEditor value={value} onChange={setValue} />;
    }

    renderWithInspector(<Harness />);
    const sql = screen.getByPlaceholderText("SELECT id FROM orders WHERE id = ?");
    await user.click(sql);
    await user.keyboard(" WHERE id > 0");

    expect(sql).toHaveFocus();
    expect(sql).toHaveValue("SELECT 1 WHERE id > 0");
  });

  it("returns to the visual editor after valid JSON is applied", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const body = '{"steps":[{"type":"return","fields":{"ok":true}}]}';
    renderWithInspector(<FunctionScriptStepsEditor value={body} onChange={onChange} />);

    const jsonToggle = screen.getByRole("checkbox", { name: "Show JSON" });
    await user.click(jsonToggle);
    await user.click(jsonToggle);

    expect(jsonToggle).not.toBeChecked();
    expect(document.querySelector("textarea.json-editor")).not.toBeInTheDocument();
    expect(onChange).toHaveBeenLastCalledWith(body);
  });
});
