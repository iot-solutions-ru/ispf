import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import HaystackMetadataPanel from "./HaystackMetadataPanel";
import { renderWithInspector, testI18n } from "../../test/renderWithInspector";
import { I18nextProvider } from "react-i18next";
import * as api from "../../api";
import type { VariableDto } from "../../types";

vi.mock("../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../api")>();
  return {
    ...actual,
    fetchVariables: vi.fn(),
    setVariable: vi.fn(),
  };
});

const DEVICE_PATH = "root.platform.devices.lab-userA-01";

function haystackVariables(tags: string): VariableDto[] {
  return [
    {
      name: "haystackTags",
      readable: true,
      writable: true,
      historyEnabled: false,
      value: {
        schema: { name: "stringValue", fields: [{ name: "value", type: "STRING" }] },
        rows: [{ value: tags }],
      },
    },
    {
      name: "haystackRef",
      readable: true,
      writable: true,
      historyEnabled: false,
      value: {
        schema: { name: "stringValue", fields: [{ name: "value", type: "STRING" }] },
        rows: [{ value: "@demo.lab.equip1" }],
      },
    },
    {
      name: "haystackKind",
      readable: true,
      writable: true,
      historyEnabled: false,
      value: {
        schema: { name: "stringValue", fields: [{ name: "value", type: "STRING" }] },
        rows: [{ value: "equip" }],
      },
    },
  ];
}

describe("HaystackMetadataPanel", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
  });

  afterEach(() => {
    cleanup();
    vi.resetAllMocks();
  });

  function renderPanel(canManage = true) {
    function Wrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={queryClient}>
          <I18nextProvider i18n={testI18n}>{children}</I18nextProvider>
        </QueryClientProvider>
      );
    }
    return renderWithInspector(
      <HaystackMetadataPanel devicePath={DEVICE_PATH} canManage={canManage} />,
      { wrapper: Wrapper }
    );
  }

  it("shows mixin hint when haystack variables are absent", async () => {
    vi.mocked(api.fetchVariables).mockResolvedValue([
      {
        name: "status",
        readable: true,
        writable: true,
        historyEnabled: false,
        value: {
          schema: { name: "stringValue", fields: [{ name: "value", type: "STRING" }] },
          rows: [{ value: "ok" }],
        },
      },
    ]);

    renderPanel();

    expect(await screen.findByText(/Apply the haystack-metadata-v1/i)).toBeInTheDocument();
  });

  it("renders marker checkboxes and saves selected tags", async () => {
    vi.mocked(api.fetchVariables).mockResolvedValue(haystackVariables('["equip","lab"]'));
    vi.mocked(api.setVariable).mockResolvedValue(haystackVariables('["equip","lab","sensor"]')[0]);

    renderPanel();
    const user = userEvent.setup();

    expect(await screen.findByLabelText(/^equip$/i)).toBeChecked();
    expect(screen.getByLabelText(/^lab$/i)).toBeChecked();

    await user.click(screen.getByLabelText(/^sensor$/i));
    await user.click(screen.getByRole("button", { name: /Save Haystack metadata/i }));

    await waitFor(() => {
      expect(api.setVariable).toHaveBeenCalledWith(
        DEVICE_PATH,
        "haystackTags",
        expect.objectContaining({
          rows: [{ value: '["equip","lab","sensor"]' }],
        })
      );
    });
  });
});
