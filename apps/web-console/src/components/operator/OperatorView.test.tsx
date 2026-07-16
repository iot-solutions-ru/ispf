import { describe, expect, it, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { I18nextProvider, initReactI18next } from "react-i18next";
import i18n from "i18next";
import OperatorView from "./OperatorView";
import enOperator from "../../locales/en/operator.json";
import enCommon from "../../locales/en/common.json";

vi.mock("../../hooks/useOperatorUi", () => ({
  useOperatorUi: () => ({ isLoading: false, data: null, error: null }),
}));

vi.mock("./OperatorManifestView", () => ({
  default: ({ appId }: { appId: string }) => (
    <div data-testid="operator-shell">manifest:{appId}</div>
  ),
}));

vi.mock("./OperatorAppLauncher", () => ({
  default: () => <div data-testid="operator-launcher">launcher</div>,
}));

vi.mock("./OperatorDashboardApp", () => ({
  default: () => <div data-testid="operator-dashboard-app">dashboard-ui</div>,
}));

const testI18n = i18n.createInstance();
void testI18n.use(initReactI18next).init({
  lng: "en",
  fallbackLng: "en",
  resources: {
    en: { operator: enOperator, common: enCommon },
  },
  interpolation: { escapeValue: false },
});

function renderView(appId?: string | null) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <I18nextProvider i18n={testI18n}>
      <QueryClientProvider client={client}>
        <OperatorView appId={appId} />
      </QueryClientProvider>
    </I18nextProvider>
  );
}

describe("OperatorView", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows launcher when appId is missing", () => {
    renderView(null);
    expect(screen.getByTestId("operator-launcher")).toBeInTheDocument();
  });

  it("routes to manifest shell when operator UI is absent", () => {
    renderView("demo-app");
    expect(screen.getByTestId("operator-shell")).toHaveTextContent("manifest:demo-app");
    expect(screen.queryByTestId("operator-dashboard-app")).not.toBeInTheDocument();
  });
});
