import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { I18nextProvider } from "react-i18next";
import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import type { ReactNode } from "react";
import PlatformLicenseCard from "./PlatformLicenseCard";
import * as platformLicenseApi from "../../api/platformLicense";
import enSystem from "../../locales/en/system.json";
import enCommon from "../../locales/en/common.json";

vi.mock("../../api/platformLicense", () => ({
  fetchPlatformLicense: vi.fn(),
}));

const testI18n = i18n.createInstance();

void testI18n.use(initReactI18next).init({
  lng: "en",
  fallbackLng: "en",
  resources: {
    en: {
      system: enSystem,
      common: enCommon,
    },
  },
  interpolation: { escapeValue: false },
});

describe("PlatformLicenseCard", () => {
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

  function renderCard() {
    function Wrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={queryClient}>
          <I18nextProvider i18n={testI18n}>{children}</I18nextProvider>
        </QueryClientProvider>
      );
    }
    return render(<PlatformLicenseCard />, { wrapper: Wrapper });
  }

  it("shows loading then license details", async () => {
    vi.mocked(platformLicenseApi.fetchPlatformLicense).mockResolvedValue({
      installationId: "inst-abc-123",
      enforce: false,
      mode: "community",
      tier: null,
      expiresAt: null,
      valid: true,
      message: "AGPL community mode",
    });

    renderCard();

    expect(screen.getByText(/Loading license status/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("inst-abc-123")).toBeInTheDocument());
    expect(screen.getByText("community")).toBeInTheDocument();
    expect(screen.getByText("Valid")).toBeInTheDocument();
  });

  it("shows error state when fetch fails", async () => {
    vi.mocked(platformLicenseApi.fetchPlatformLicense).mockRejectedValue(new Error("403"));

    renderCard();

    await waitFor(() =>
      expect(screen.getByText(/Failed to load license status/i)).toBeInTheDocument(),
    );
  });

  it("copies installation id to clipboard", async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("navigator", {
      ...navigator,
      clipboard: { writeText },
    });

    vi.mocked(platformLicenseApi.fetchPlatformLicense).mockResolvedValue({
      installationId: "inst-copy-me",
      enforce: true,
      mode: "enterprise",
      tier: "standard",
      expiresAt: "2027-01-01T00:00:00.000Z",
      valid: false,
      message: "Signature invalid",
    });

    renderCard();
    await waitFor(() => expect(screen.getByText("inst-copy-me")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "Copy" }));
    expect(writeText).toHaveBeenCalledWith("inst-copy-me");
    expect(screen.getByText(/Enforcement is enabled but the platform license is invalid/i)).toBeInTheDocument();
  });
});
