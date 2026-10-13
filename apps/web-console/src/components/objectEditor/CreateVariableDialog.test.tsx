import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CreateVariableDialog from "./CreateVariableDialog";
import * as api from "../../api";
import { renderWithInspector } from "../../test/renderWithInspector";

vi.mock("../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../api")>();
  return {
    ...actual,
    createVariable: vi.fn(),
  };
});

describe("CreateVariableDialog", () => {
  const onClose = vi.fn();
  const onSaved = vi.fn();
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { mutations: { retry: false } },
    });
    vi.mocked(api.createVariable).mockResolvedValue({
      name: "temperature",
      value: { schema: { name: "temperature", fields: [] }, rows: [] },
      readable: true,
      writable: false,
      updatedAt: "2026-06-30T00:00:00.000Z",
      historyEnabled: false,
      historyRetentionDays: null,
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  function renderDialog() {
    return renderWithInspector(
      <QueryClientProvider client={queryClient}>
        <CreateVariableDialog
          objectPath="root.platform.devices.lab"
          onClose={onClose}
          onSaved={onSaved}
        />
      </QueryClientProvider>,
    );
  }

  it("keeps the name field focused while typing", async () => {
    const user = userEvent.setup();
    renderDialog();

    const input = screen.getByPlaceholderText("myVariable");
    await user.click(input);
    await user.keyboard("temp");

    expect(input).toHaveValue("temp");
    expect(input).toHaveFocus();
    expect(screen.queryByText("Schema name")).not.toBeInTheDocument();
  });

  it("creates a variable with entered name", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByPlaceholderText("myVariable"), "temperature");
    await user.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => expect(api.createVariable).toHaveBeenCalled());
    expect(api.createVariable).toHaveBeenCalledWith(
      "root.platform.devices.lab",
      expect.objectContaining({
        name: "temperature",
        readable: true,
        writable: false,
        schema: expect.objectContaining({
          name: "temperature",
          fields: [expect.objectContaining({ name: "value", type: "DOUBLE" })],
        }),
      }),
    );
    expect(onSaved).toHaveBeenCalled();
  });

  it("uses DataSchemaEditor only (no schema preset shortcuts)", () => {
    renderDialog();

    expect(document.querySelector(".data-schema-editor")).toBeTruthy();
    expect(screen.getByRole("button", { name: "+ Field" })).toBeInTheDocument();
    expect(
      screen.getByText(/Default: one field value \(DOUBLE\)/i),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Integer" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Boolean" })).not.toBeInTheDocument();
  });

  it("calls onClose from cancel button", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onClose).toHaveBeenCalled();
    expect(api.createVariable).not.toHaveBeenCalled();
  });

  it("creates a variable with an added field", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByRole("button", { name: "+ Field" }));
    await user.type(screen.getByPlaceholderText("myVariable"), "reading");
    await user.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => expect(api.createVariable).toHaveBeenCalled());
    expect(api.createVariable).toHaveBeenCalledWith(
      "root.platform.devices.lab",
      expect.objectContaining({
        schema: expect.objectContaining({
          fields: [
            expect.objectContaining({ name: "value", type: "DOUBLE" }),
            expect.objectContaining({ name: "field2", type: "STRING" }),
          ],
        }),
      }),
    );
  });

  it("keeps a renamed field when creating", async () => {
    const user = userEvent.setup();
    renderDialog();

    const fieldName = screen.getAllByDisplayValue("value").find((el) => el.classList.contains("mono"));
    expect(fieldName).toBeTruthy();
    await user.clear(fieldName!);
    await user.type(fieldName!, "exists");
    await user.type(screen.getByPlaceholderText("myVariable"), "fileMeta");
    await user.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => expect(api.createVariable).toHaveBeenCalled());
    expect(api.createVariable).toHaveBeenCalledWith(
      "root.platform.devices.lab",
      expect.objectContaining({
        schema: expect.objectContaining({
          fields: [expect.objectContaining({ name: "exists", type: "DOUBLE" })],
        }),
      }),
    );
  });

  it("does not submit a Cyrillic technical name", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByPlaceholderText("myVariable"), "температура");

    expect(screen.getByRole("button", { name: "Create" })).toBeDisabled();
    expect(screen.getByText(/Latin letter or underscore first/i)).toBeInTheDocument();
    expect(api.createVariable).not.toHaveBeenCalled();
  });
});
