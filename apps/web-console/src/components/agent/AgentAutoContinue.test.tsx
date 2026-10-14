import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import i18n from "i18next";
import { I18nextProvider, initReactI18next } from "react-i18next";
import enAi from "../../locales/en/ai.json";
import { AgentAutoContinue } from "./AgentAutoContinue";

const testI18n = i18n.createInstance();
void testI18n.use(initReactI18next).init({
  lng: "en",
  resources: { en: { ai: enAi } },
  interpolation: { escapeValue: false },
});

const suggestions = [
  { label: "Create demo", message: "create demo with virtual device", primary: true },
  { label: "SNMP", message: "connect snmp" },
];

describe("AgentAutoContinue", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("renders nothing when disabled (Copilot)", () => {
    const onSuggest = vi.fn();
    const { container } = render(
      <I18nextProvider i18n={testI18n}>
        <AgentAutoContinue
          enabled={false}
          suggestions={suggestions}
          result={{ autoContinueSeconds: 5 }}
          onSuggestMessage={onSuggest}
        />
      </I18nextProvider>,
    );
    expect(container).toBeEmptyDOMElement();
    expect(onSuggest).not.toHaveBeenCalled();
  });

  it("shows the wait control when Autopilot auto-continue is enabled", () => {
    render(
      <I18nextProvider i18n={testI18n}>
        <AgentAutoContinue
          enabled
          suggestions={suggestions}
          result={{ autoContinueSeconds: 45 }}
          onSuggestMessage={vi.fn()}
        />
      </I18nextProvider>,
    );
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Wait for my answer" })).toBeInTheDocument();
  });

  it("cancel stops the countdown", async () => {
    vi.useFakeTimers();
    const onSuggest = vi.fn();
    render(
      <I18nextProvider i18n={testI18n}>
        <AgentAutoContinue
          enabled
          suggestions={suggestions}
          result={{ autoContinueSeconds: 5 }}
          onSuggestMessage={onSuggest}
        />
      </I18nextProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Wait for my answer" }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });
    expect(onSuggest).not.toHaveBeenCalled();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
