import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { EventDescriptor, FunctionDescriptor } from "../../types";
import { renderWithInspector } from "../../test/renderWithInspector";
import EditDescriptorDialog from "./EditDescriptorDialog";
import * as api from "../../api";

vi.mock("../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../api")>();
  return {
    ...actual,
    upsertFunction: vi.fn(),
    upsertEvent: vi.fn(),
    fetchObjects: vi.fn().mockResolvedValue([
      {
        path: "root.platform.data-sources.app_myapp",
        type: "DATA_SOURCE",
        displayName: "App",
      },
    ]),
  };
});

vi.mock("../../api/securityRoles", () => ({
  fetchSecurityRoles: vi.fn().mockResolvedValue([
    { name: "operator", displayName: "Operator" },
    { name: "admin", displayName: "Admin" },
  ]),
}));

vi.mock("../schema/DataSchemaEditor", () => ({
  default: ({ showSchemaName = true }: { showSchemaName?: boolean }) => (
    <div data-testid="schema-editor" data-show-schema-name={String(showSchemaName)} />
  ),
}));

vi.mock("../functionScript/FunctionScriptStepsEditor", () => ({
  default: ({ value, onChange }: { value: string; onChange: (next: string) => void }) => (
    <textarea aria-label="Steps source" value={value} onChange={(event) => onChange(event.target.value)} />
  ),
}));

vi.mock("../functionScript/JavaFunctionEditor", () => ({
  default: ({ value, onChange }: { value: string; onChange: (next: string) => void }) => (
    <textarea aria-label="Java source" value={value} onChange={(event) => onChange(event.target.value)} />
  ),
}));

const javaFunction: FunctionDescriptor = {
  name: "agentFunction",
  description: "Created by agent",
  inputSchema: {
    name: "agentFunctionInput",
    fields: [{ name: "value", type: "STRING", description: "Value", nullable: false }],
  },
  outputSchema: {
    name: "agentFunctionOutput",
    fields: [{ name: "ok", type: "BOOLEAN", description: "Success", nullable: false }],
  },
  sourceType: "java",
  sourceBody: "public class AgentFunction { /* original */ }",
  dataSourcePath: "root.platform.data-sources.main",
  version: "1.2.3",
};

describe("EditDescriptorDialog", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    vi.mocked(api.upsertFunction).mockResolvedValue(javaFunction);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  function renderDialog(initial: FunctionDescriptor = javaFunction) {
    return renderWithInspector(
      <QueryClientProvider client={queryClient}>
        <EditDescriptorDialog
          objectPath="root.platform.functions"
          kind="function"
          initial={initial}
          onClose={vi.fn()}
          onSaved={vi.fn()}
        />
      </QueryClientProvider>,
    );
  }

  async function selectSourceType(user: ReturnType<typeof userEvent.setup>, label: string) {
    await user.click(screen.getByRole("combobox", { name: "Source type" }));
    const option = await screen.findByRole("option", { name: label });
    // Ant Design Select options sit in a popup; skip pointer-events checks under jsdom.
    await user.click(option, { pointerEventsCheck: 0 });
    await waitFor(() =>
      expect(screen.getByRole("combobox", { name: "Source type" })).toHaveAttribute(
        "aria-expanded",
        "false",
      ),
    );
  }

  it("hides schema names and saves new functions as in and out", async () => {
    const user = userEvent.setup();
    renderWithInspector(
      <QueryClientProvider client={queryClient}>
        <EditDescriptorDialog
          objectPath="root.platform.functions"
          kind="function"
          onClose={vi.fn()}
          onSaved={vi.fn()}
        />
      </QueryClientProvider>,
    );

    const editors = screen.getAllByTestId("schema-editor");
    expect(editors).toHaveLength(2);
    for (const editor of editors) {
      expect(editor).toHaveAttribute("data-show-schema-name", "false");
    }

    const nameInput = document.querySelector("input[required]") as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: "calculate" } });
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.upsertFunction).toHaveBeenCalledTimes(1));
    const saved = vi.mocked(api.upsertFunction).mock.calls[0][1];
    expect(saved.inputSchema.name).toBe("in");
    expect(saved.outputSchema.name).toBe("out");
  });

  it("hides the event payload schema name and saves a new event as namePayload", async () => {
    const user = userEvent.setup();
    renderWithInspector(
      <QueryClientProvider client={queryClient}>
        <EditDescriptorDialog
          objectPath="root.platform.devices.lab"
          kind="event"
          onClose={vi.fn()}
          onSaved={vi.fn()}
        />
      </QueryClientProvider>,
    );

    expect(screen.getByTestId("schema-editor")).toHaveAttribute("data-show-schema-name", "false");

    const nameInput = document.querySelector("input[required]") as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: "alarm" } });
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.upsertEvent).toHaveBeenCalledTimes(1));
    const saved = vi.mocked(api.upsertEvent).mock.calls[0][1] as EventDescriptor;
    expect(saved.name).toBe("alarm");
    expect(saved.payloadSchema.name).toBe("alarmPayload");
  });

  it("keeps an existing event payload schema name", async () => {
    const user = userEvent.setup();
    const initial: EventDescriptor = {
      name: "alarm",
      description: "Raised alarm",
      payloadSchema: {
        name: "customPayload",
        fields: [{ name: "message", type: "STRING", description: "Message", nullable: true }],
      },
      level: "INFO",
      invokeRoles: [],
    };
    renderWithInspector(
      <QueryClientProvider client={queryClient}>
        <EditDescriptorDialog
          objectPath="root.platform.devices.lab"
          kind="event"
          initial={initial}
          onClose={vi.fn()}
          onSaved={vi.fn()}
        />
      </QueryClientProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.upsertEvent).toHaveBeenCalledTimes(1));
    const saved = vi.mocked(api.upsertEvent).mock.calls[0][1] as EventDescriptor;
    expect(saved.payloadSchema.name).toBe("customPayload");
  });

  it("keeps data source path inactive until the script contains SQL", async () => {
    const user = userEvent.setup();
    const sqlBody = JSON.stringify({
      steps: [
        { type: "selectOne", var: "row", sql: "SELECT 1", params: [] },
        { type: "return", fields: { ok: true } },
      ],
    });
    renderDialog({
      ...javaFunction,
      sourceType: "script",
      sourceBody: sqlBody,
      dataSourcePath: "root.platform.data-sources.app_myapp",
    });

    const path = await screen.findByRole("combobox", { name: "Data source path" });
    expect(path).toBeEnabled();
    expect(await screen.findByText("App (root.platform.data-sources.app_myapp)")).toBeInTheDocument();

    fireEvent.change(screen.getByRole("textbox", { name: "Steps source" }), {
      target: { value: JSON.stringify({ steps: [{ type: "return", fields: { ok: true } }] }) },
    });
    expect(path).toBeDisabled();
    expect(screen.getByText("App (root.platform.data-sources.app_myapp)")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(api.upsertFunction).toHaveBeenCalled());
    expect(vi.mocked(api.upsertFunction).mock.calls.at(-1)?.[1].dataSourcePath).toBe(
      "root.platform.data-sources.app_myapp",
    );
  });

  it("hides data source path for a Java function", () => {
    renderDialog();
    expect(screen.queryByRole("combobox", { name: "Data source path" })).not.toBeInTheDocument();
  });

  it("marks an empty name and an empty script body", () => {
    renderDialog({
      ...javaFunction,
      name: "",
      sourceType: "script",
      sourceBody: "",
    });

    expect(screen.getByText("Enter a name.")).toBeInTheDocument();
    expect(screen.getByText("Enter the function body.")).toBeInTheDocument();
  });

  it("preserves independent Java and steps drafts when switching source type", async () => {
    const user = userEvent.setup();
    renderDialog();

    const javaEditor = await screen.findByRole("textbox", { name: "Java source" });
    fireEvent.change(javaEditor, { target: { value: "custom Java body" } });

    await selectSourceType(user, "Script (JSON steps)");
    const stepsEditor = screen.getByRole("textbox", { name: "Steps source" });
    fireEvent.change(stepsEditor, { target: { value: '{"steps":[{"type":"return"}]}' } });

    await selectSourceType(user, "Java (compile on save)");
    expect(await screen.findByRole("textbox", { name: "Java source" })).toHaveValue("custom Java body");
    await selectSourceType(user, "Script (JSON steps)");
    expect(screen.getByRole("textbox", { name: "Steps source" })).toHaveValue(
      '{"steps":[{"type":"return"}]}',
    );
  });

  it("keeps an agent-created descriptor payload through advanced JSON round-trip", async () => {
    const user = userEvent.setup();
    const sourceBody = '{"steps":[{"type":"agent_extension","config":{"keep":true}}]}';
    const initial: FunctionDescriptor = { ...javaFunction, sourceType: "script", sourceBody };
    renderDialog(initial);

    const advanced = screen.getByRole("switch", { name: "Edit as JSON (advanced)" });
    await user.click(advanced);
    const advancedEditor = document.querySelector("textarea.json-editor") as HTMLTextAreaElement;
    expect(JSON.parse(advancedEditor.value).sourceBody).toBe(sourceBody);
    await user.click(advanced);
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.upsertFunction).toHaveBeenCalledTimes(1));
    expect(api.upsertFunction).toHaveBeenCalledWith("root.platform.functions", {
      ...initial,
      invokeRoles: [],
    });
  });

  it("does not leave advanced mode while its JSON is invalid", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByRole("switch", { name: "Edit as JSON (advanced)" }));
    const advancedEditor = document.querySelector("textarea.json-editor") as HTMLTextAreaElement;
    fireEvent.change(advancedEditor, { target: { value: "{invalid" } });
    // Re-query after re-render; a stale switch node will not fire onChange.
    await user.click(screen.getByRole("switch", { name: "Edit as JSON (advanced)" }));

    await waitFor(() => {
      expect(screen.getByText("Invalid schema JSON")).toBeInTheDocument();
    });
    expect(screen.getByRole("switch", { name: "Edit as JSON (advanced)" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getByDisplayValue("{invalid")).toBeInTheDocument();
  });
});
