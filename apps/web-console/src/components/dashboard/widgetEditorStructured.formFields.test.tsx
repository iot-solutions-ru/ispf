import { cleanup, fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchObjectEditor } from "../../api";
import { renderWithDashboard } from "../../test/renderWithDashboard";
import { FormFieldsEditor } from "./widgetEditorStructured";

vi.mock("../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../api")>();
  return {
    ...actual,
    fetchObjectEditor: vi.fn(),
  };
});

describe("FormFieldsEditor function inputs", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(fetchObjectEditor).mockReset();
  });

  it("keeps the field name closed until an object and function are chosen", () => {
    renderWithDashboard(
      <FormFieldsEditor
        mode="function-form"
        value={JSON.stringify([{ name: "", label: "", type: "text" }])}
        onChange={() => {}}
      />,
    );
    expect(screen.getByRole("combobox", { name: "Field name" })).toBeDisabled();
    expect(
      screen.getByText("Choose an object and a function first — the list is that function's inputs."),
    ).toBeInTheDocument();
    expect(fetchObjectEditor).not.toHaveBeenCalled();
  });

  it("offers only the selected function's input fields and keeps a saved name", async () => {
    vi.mocked(fetchObjectEditor).mockResolvedValue({
      functions: [
        {
          name: "appendTableRow",
          inputSchema: {
            name: "in",
            fields: [
              { name: "qty", type: "DOUBLE" },
              { name: "code", type: "STRING" },
            ],
          },
        },
      ],
    } as Awaited<ReturnType<typeof fetchObjectEditor>>);
    const onChange = vi.fn();
    renderWithDashboard(
      <FormFieldsEditor
        mode="function-form"
        objectPath="root.devices.pump"
        functionName="appendTableRow"
        value={JSON.stringify([{ name: "legacy", label: "Old", type: "text" }])}
        onChange={onChange}
      />,
    );

    const select = await screen.findByRole("combobox", { name: "Field name" });
    await waitFor(() => {
      expect(screen.getByRole("option", { name: "code" })).toBeInTheDocument();
    });
    expect(screen.getByRole("option", { name: "qty" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "legacy" })).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Field name" })).not.toBeInTheDocument();

    fireEvent.change(select, { target: { value: "code" } });
    const saved = JSON.parse(onChange.mock.lastCall?.[0] as string) as Array<{ name: string }>;
    expect(saved[0]?.name).toBe("code");
    expect(fetchObjectEditor).toHaveBeenCalledWith("root.devices.pump");
  });

  it("leaves input-form field names as free text", () => {
    renderWithDashboard(
      <FormFieldsEditor
        mode="input-form"
        value={JSON.stringify([{ name: "note", label: "Note", type: "text" }])}
        onChange={() => {}}
      />,
    );
    expect(screen.queryByRole("combobox", { name: "Field name" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("textbox").length).toBeGreaterThan(0);
    expect(fetchObjectEditor).not.toHaveBeenCalled();
  });
});
