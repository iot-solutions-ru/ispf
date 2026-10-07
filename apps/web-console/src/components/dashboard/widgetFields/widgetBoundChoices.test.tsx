import { cleanup, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchObjectEditor, fetchVariables, fetchVariablesBatch } from "../../../api";
import { fetchReport } from "../../../api/reports";
import { renderWithDashboard } from "../../../test/renderWithDashboard";
import type { VariableDto } from "../../../types";
import { ObjectTableColumnsEditor } from "../widgetEditorStructured";
import {
  FunctionInputPairs,
  RecordFieldGroup,
  ReportColumnSelect,
  ReportParameterPairs,
} from "./widgetBoundChoices";

vi.mock("../../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../api")>();
  return {
    ...actual,
    fetchObjectEditor: vi.fn(),
    fetchVariables: vi.fn(),
    fetchVariablesBatch: vi.fn(),
  };
});

vi.mock("../../../api/reports", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../api/reports")>();
  return { ...actual, fetchReport: vi.fn() };
});

const temperature = {
  name: "temperature",
  value: {
    schema: {
      name: "temperature",
      fields: [
        { name: "value", type: "DOUBLE" },
        { name: "unit", type: "STRING" },
      ],
    },
    rows: [],
  },
} as VariableDto;

describe("widget editor dependent choices", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(fetchObjectEditor).mockReset();
    vi.mocked(fetchVariables).mockReset();
    vi.mocked(fetchVariablesBatch).mockReset();
    vi.mocked(fetchReport).mockReset();
  });

  it("keeps function input keys closed until an object and function are chosen", () => {
    renderWithDashboard(
      <FunctionInputPairs label="Input" value={undefined} onChange={() => {}} />,
    );
    expect(
      screen.getByText("Choose an object and a function first — the list is that function's inputs."),
    ).toBeInTheDocument();
    expect(fetchObjectEditor).not.toHaveBeenCalled();
  });

  it("offers the selected function's inputs as inputJson keys and keeps a saved key", async () => {
    vi.mocked(fetchObjectEditor).mockResolvedValue({
      functions: [
        {
          name: "calculate",
          inputSchema: {
            name: "in",
            fields: [
              { name: "inputA", type: "DOUBLE" },
              { name: "inputB", type: "DOUBLE" },
            ],
          },
        },
      ],
    } as Awaited<ReturnType<typeof fetchObjectEditor>>);
    renderWithDashboard(
      <FunctionInputPairs
        label="Input"
        objectPath="root.devices.handler"
        functionName="calculate"
        value={JSON.stringify({ kept: "1" })}
        onChange={() => {}}
      />,
    );
    const key = await screen.findByRole("combobox", { name: "Function input" });
    await waitFor(() => expect(key).toBeEnabled());
    expect(screen.getByRole("option", { name: "inputA" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "inputB" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "kept" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Value" })).toBeInTheDocument();
  });

  it("limits report parameter keys and the row column to the selected report", async () => {
    vi.mocked(fetchReport).mockResolvedValue({
      parameters: ["plant", "shift"],
      columns: [
        { field: "id", label: "Id" },
        { field: "name", label: "Name" },
      ],
    } as Awaited<ReturnType<typeof fetchReport>>);
    renderWithDashboard(
      <>
        <ReportParameterPairs
          reportPath="root.reports.shift"
          label="Fixed"
          valueCaption="Value"
          value={JSON.stringify({ kept: "1" })}
          onChange={() => {}}
        />
        <ReportColumnSelect
          reportPath="root.reports.shift"
          label="Column with object path"
          value="legacy"
          onChange={() => {}}
        />
      </>,
    );
    const parameter = await screen.findByRole("combobox", { name: "Report parameter" });
    await waitFor(() => expect(parameter).toBeEnabled());
    expect(screen.getByRole("option", { name: "plant" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "shift" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "kept" })).toBeInTheDocument();
    const column = screen.getByRole("combobox", { name: "Column with object path" });
    expect(screen.getAllByRole("option", { name: "id" }).length).toBeGreaterThan(0);
    expect(column).toHaveValue("legacy");
  });

  it("limits a record field to the selected variable schema", async () => {
    vi.mocked(fetchVariables).mockResolvedValue([temperature]);
    renderWithDashboard(
      <RecordFieldGroup
        objectPath="root.devices.sensor"
        variableName="temperature"
        allowCustom={false}
        fields={[{ label: "Record field", value: "raw", onChange: () => {} }]}
      />,
    );
    const field = await screen.findByRole("combobox", { name: "Record field" });
    await waitFor(() => expect(field).toBeEnabled());
    expect(screen.getByRole("option", { name: "value" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "unit" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "raw" })).toBeInTheDocument();
  });

  it("keeps a record field free when the object is resolved only from selection", () => {
    renderWithDashboard(
      <RecordFieldGroup
        variableName="temperature"
        allowCustom
        fields={[{ label: "Record field", value: "raw", onChange: () => {} }]}
      />,
    );
    expect(screen.getByRole("textbox", { name: "Record field" })).toHaveValue("raw");
    expect(screen.queryByRole("combobox", { name: "Record field" })).not.toBeInTheDocument();
    expect(fetchVariables).not.toHaveBeenCalled();
  });

  it("limits an object-table column to variables under the parent and that variable's fields", async () => {
    vi.mocked(fetchVariablesBatch).mockResolvedValue({
      "root.devices.pump": [temperature],
    });
    renderWithDashboard(
      <ObjectTableColumnsEditor
        parentPath="root.devices"
        objects={[{ path: "root.devices.pump", variableNames: ["temperature"] }]}
        value={JSON.stringify([{ label: "Temp", variable: "temperature", field: "raw" }])}
        onChange={() => {}}
      />,
    );
    const variable = await screen.findByRole("combobox", { name: "Variable" });
    expect(screen.getByRole("option", { name: "temperature" })).toBeInTheDocument();
    expect(variable).toHaveValue("temperature");
    await waitFor(() => {
      expect(screen.getByRole("combobox", { name: "Field" })).toBeEnabled();
    });
    expect(screen.getByRole("option", { name: "value" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "raw" })).toBeInTheDocument();
  });
});
