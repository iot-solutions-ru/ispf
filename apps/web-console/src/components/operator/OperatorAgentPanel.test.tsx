import { afterEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import i18n from "i18next";
import { I18nextProvider, initReactI18next } from "react-i18next";
import enOperator from "../../locales/en/operator.json";
import OperatorAgentPanel from "./OperatorAgentPanel";

vi.mock("../../api/operatorAgent", () => ({
  fetchOperatorAgentStatus: vi.fn(async () => ({
    appId: "line",
    title: "Line",
    pathPrefixes: [],
    provider: { available: true },
  })),
  createOperatorAgentSession: vi.fn(async () => ({ sessionId: "s1", title: "Line" })),
  sendOperatorAgentMessage: vi.fn(async () => ({
    turnId: "t1",
    summary: "all quiet",
    steps: [],
    status: "DONE",
    result: {},
  })),
  cancelOperatorAgentRun: vi.fn(async () => undefined),
  subscribeOperatorAgentProgress: vi.fn(() => () => undefined),
}));

const testI18n = i18n.createInstance();
void testI18n.use(initReactI18next).init({
  lng: "en",
  resources: { en: { operator: enOperator } },
  interpolation: { escapeValue: false },
});

function panel(open: boolean) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={client}>
      <I18nextProvider i18n={testI18n}>
        <OperatorAgentPanel appId="line" open={open} onClose={() => undefined} />
      </I18nextProvider>
    </QueryClientProvider>
  );
}

describe("OperatorAgentPanel", () => {
  afterEach(() => {
    cleanup();
  });

  it("keeps the thread when closed and clears it from New chat", async () => {
    Element.prototype.scrollIntoView = () => undefined;
    const user = userEvent.setup();
    const view = render(panel(true));

    const input = await screen.findByPlaceholderText(/summarize alarms/i);
    await waitFor(() => expect(input).toBeEnabled());
    expect(screen.getByRole("button", { name: "New chat" })).toBeDisabled();

    await user.type(input, "status");
    await user.click(screen.getByRole("button", { name: "Send" }));
    expect(await screen.findByText("all quiet")).toBeTruthy();

    view.rerender(panel(false));
    view.rerender(panel(true));
    expect(screen.getByText("all quiet")).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "New chat" }));
    expect(screen.queryByText("all quiet")).toBeNull();
    expect(screen.getByRole("button", { name: "New chat" })).toBeDisabled();
  });
});
