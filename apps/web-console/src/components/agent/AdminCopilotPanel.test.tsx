import { afterEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, render, screen } from "@testing-library/react";
import i18n from "i18next";
import { I18nextProvider, initReactI18next } from "react-i18next";
import enAi from "../../locales/en/ai.json";
import enOperator from "../../locales/en/operator.json";
import {
  AdminCopilotChatContext,
  type AdminCopilotChatContextValue,
} from "../../context/useAdminCopilotChat";
import AdminCopilotPanel from "./AdminCopilotPanel";

const testI18n = i18n.createInstance();
void testI18n.use(initReactI18next).init({
  lng: "en",
  resources: { en: { ai: enAi, operator: enOperator } },
  interpolation: { escapeValue: false },
});

function chatValue(startNewChat: () => void): AdminCopilotChatContextValue {
  return {
    provider: { available: true } as AdminCopilotChatContextValue["provider"],
    providerLoading: false,
    agentApiReady: true,
    messages: [{ id: "1", role: "user", text: "why is the pump offline" }],
    isPending: false,
    liveSteps: [],
    sendMessage: vi.fn(),
    cancelRun: vi.fn(),
    startNewChat,
    interactionMode: "ask",
    setInteractionMode: vi.fn(),
  };
}

function renderPanel(open: boolean, startNewChat: () => void) {
  return render(
    <I18nextProvider i18n={testI18n}>
      <AdminCopilotChatContext.Provider value={chatValue(startNewChat)}>
        <AdminCopilotPanel open={open} onClose={vi.fn()} />
      </AdminCopilotChatContext.Provider>
    </I18nextProvider>,
  );
}

describe("AdminCopilotPanel", () => {
  Element.prototype.scrollIntoView = () => undefined;

  afterEach(() => {
    cleanup();
  });

  it("keeps the thread when the drawer opens again", () => {
    const startNewChat = vi.fn();
    const view = renderPanel(false, startNewChat);
    view.rerender(
      <I18nextProvider i18n={testI18n}>
        <AdminCopilotChatContext.Provider value={chatValue(startNewChat)}>
          <AdminCopilotPanel open onClose={vi.fn()} />
        </AdminCopilotChatContext.Provider>
      </I18nextProvider>,
    );

    expect(startNewChat).not.toHaveBeenCalled();
    expect(screen.getByText("why is the pump offline")).toBeInTheDocument();
  });

  it("clears the thread only from New chat", async () => {
    const startNewChat = vi.fn();
    const user = userEvent.setup();
    renderPanel(true, startNewChat);

    expect(startNewChat).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Start a new Copilot chat" }));
    expect(startNewChat).toHaveBeenCalledOnce();
  });
});
