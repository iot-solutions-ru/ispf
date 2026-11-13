import { afterEach, describe, expect, it, vi } from "vitest";
import i18n from "i18next";
import { initReactI18next, I18nextProvider } from "react-i18next";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import enScada from "../../locales/en/scada.json";
import enCommon from "../../locales/en/common.json";
import * as api from "../../api";
import MimicEditorPanel from "./MimicEditorPanel";

vi.mock("../../api", () => ({
  fetchMimic: vi.fn(),
  saveMimicDiagram: vi.fn(),
  saveMimicTitle: vi.fn(),
}));

const testI18n = i18n.createInstance();
void testI18n.use(initReactI18next).init({
  lng: "en",
  fallbackLng: "en",
  resources: { en: { scada: enScada, common: enCommon } },
  interpolation: { escapeValue: false },
});

function renderPanel(onClose = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <I18nextProvider i18n={testI18n}>
        <MimicEditorPanel path="root.plant.mimic" title="Pump" onClose={onClose} />
      </I18nextProvider>
    </QueryClientProvider>,
  );
  return onClose;
}

describe("MimicEditorPanel save", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("keeps the editor open after a successful save", async () => {
    const user = userEvent.setup();
    vi.mocked(api.fetchMimic).mockResolvedValue({
      path: "root.plant.mimic",
      title: "Pump",
      refreshIntervalMs: 5000,
      diagramJson: "{}",
    });
    vi.mocked(api.saveMimicDiagram).mockResolvedValue({
      path: "root.plant.mimic",
      title: "Pump",
      refreshIntervalMs: 5000,
      diagramJson: "{}",
    });
    const onClose = renderPanel();

    const save = await screen.findByRole("button", { name: "Save" });
    await user.click(save);

    await waitFor(() => expect(api.saveMimicDiagram).toHaveBeenCalled());
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
  });

  it("shows the save error and stays open", async () => {
    const user = userEvent.setup();
    vi.mocked(api.fetchMimic).mockResolvedValue({
      path: "root.plant.mimic",
      title: "Pump",
      refreshIntervalMs: 5000,
      diagramJson: "{}",
    });
    vi.mocked(api.saveMimicDiagram).mockRejectedValue(new Error("disk full"));
    const onClose = renderPanel();

    await user.click(await screen.findByRole("button", { name: "Save" }));

    expect(await screen.findByText("disk full")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
  });
});
