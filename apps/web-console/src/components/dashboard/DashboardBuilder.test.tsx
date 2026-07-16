import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { I18nextProvider } from "react-i18next";
import DashboardBuilder from "./DashboardBuilder";
import * as api from "../../api";
import { emptyLayout, layoutToJson, type DashboardView } from "../../types/dashboard";
import { testI18n } from "../../test/renderWithDashboard";

vi.mock("../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../api")>();
  return {
    ...actual,
    fetchDashboard: vi.fn(),
    fetchObjects: vi.fn(),
    saveDashboardLayout: vi.fn(),
    saveDashboardTitle: vi.fn(),
    saveDashboardRefreshInterval: vi.fn(),
  };
});

vi.mock("./DashboardGrid", () => ({
  default: () => <div data-testid="grid-stub" />,
}));
vi.mock("./WidgetPalette", () => ({ default: () => null }));
vi.mock("./DashboardSettingsPanel", () => ({ default: () => null }));
vi.mock("./DashboardRulesPanel", () => ({ default: () => null }));
vi.mock("./WidgetEditorPanel", () => ({ default: () => null }));
vi.mock("./HaystackBindDialog", () => ({ default: () => null }));
vi.mock("../../hooks/usePublishAdminFocus", () => ({
  usePublishAdminFocus: () => {},
}));
vi.mock("../../hooks/useMobileLayout", () => ({
  useMobileLayout: () => false,
}));
vi.mock("../../hooks/useDashboardContextSync", () => ({
  useDashboardContextSync: () => {},
}));

const PATH = "root.platform.dashboards.demo";

function stubDashboard(title = "Demo Board"): DashboardView {
  const layout = emptyLayout();
  return {
    path: PATH,
    title,
    refreshIntervalMs: 5000,
    layout,
    layoutJson: layoutToJson(layout),
  };
}

describe("DashboardBuilder", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    vi.mocked(api.fetchDashboard).mockResolvedValue(stubDashboard());
    vi.mocked(api.fetchObjects).mockResolvedValue([]);
    vi.mocked(api.saveDashboardLayout).mockResolvedValue(stubDashboard("Renamed"));
    vi.mocked(api.saveDashboardTitle).mockResolvedValue(stubDashboard("Renamed"));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  function renderBuilder() {
    return (
      <I18nextProvider i18n={testI18n}>
        <QueryClientProvider client={queryClient}>
          <DashboardBuilder path={PATH} />
        </QueryClientProvider>
      </I18nextProvider>
    );
  }

  it("loads dashboard by path", async () => {
    const { render } = await import("@testing-library/react");
    render(renderBuilder());

    await waitFor(() => expect(api.fetchDashboard).toHaveBeenCalledWith(PATH));
    expect(await screen.findByText("Demo Board")).toBeInTheDocument();
    expect(screen.getByText(PATH)).toBeInTheDocument();
    expect(screen.getByTestId("grid-stub")).toBeInTheDocument();
  });

  it("saves title and layout after edit", async () => {
    const user = userEvent.setup();
    const { render } = await import("@testing-library/react");
    render(renderBuilder());

    await screen.findByText("Demo Board");
    await user.click(screen.getByRole("button", { name: "Editor" }));

    const titleInput = screen.getByDisplayValue("Demo Board");
    await user.clear(titleInput);
    await user.type(titleInput, "Renamed");

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.saveDashboardTitle).toHaveBeenCalled());
    expect(api.saveDashboardTitle).toHaveBeenCalledWith(PATH, "Renamed");
    expect(api.saveDashboardLayout).toHaveBeenCalledWith(PATH, expect.any(String));
  });
});
