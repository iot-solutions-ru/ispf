import { describe, expect, it, afterEach } from "vitest";
import { cleanup, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import FunctionWidgetView from "./FunctionWidgetView";
import { newWidget } from "../../../types/dashboard";
import { renderWithDashboard } from "../../../test/renderWithDashboard";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
});

function withQueryClient(ui: ReactElement) {
  return <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>;
}

const BUTTONS = JSON.stringify([
  {
    label: "Запустить",
    functionName: "job_start",
    inputJson: "{\"jobNo\": \"${param:jobNo}\"}",
    enabledWhenJson: "{\"paramKey\": \"dispatchStatus\", \"equals\": [\"ALLOWED\"]}",
  },
  {
    label: "Пауза",
    functionName: "job_pause",
    inputJson: "{\"jobNo\": \"${param:jobNo}\"}",
    enabledWhenJson: "{\"paramKey\": \"dispatchStatus\", \"equals\": [\"RUNNING\"]}",
  },
  {
    label: "Справка",
    functionName: "job_info",
  },
]);

function actionWidget() {
  return {
    ...newWidget("function", 0),
    title: "Действия",
    objectPath: "root.demo.hub",
    buttonsJson: BUTTONS,
  };
}

describe("FunctionWidgetView", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders legacy single button", () => {
    const widget = {
      ...newWidget("function", 0),
      title: "Run",
      objectPath: "root.demo.hub",
      functionName: "do_thing",
      buttonLabel: "Выполнить",
    };

    renderWithDashboard(withQueryClient(<FunctionWidgetView widget={widget} />));

    const btn = screen.getByRole("button", { name: "Выполнить" });
    expect(btn).toBeInTheDocument();
    expect(btn).not.toBeDisabled();
  });

  it("enables buttons by session param status", () => {
    renderWithDashboard(withQueryClient(<FunctionWidgetView widget={actionWidget()} />), {
      session: { params: { jobNo: "JO-1", dispatchStatus: "RUNNING" } },
    });

    expect(screen.getByRole("button", { name: "Запустить" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Пауза" })).not.toBeDisabled();
    expect(screen.getByRole("button", { name: "Справка" })).not.toBeDisabled();
  });

  it("disables conditional buttons when param is missing", () => {
    renderWithDashboard(withQueryClient(<FunctionWidgetView widget={actionWidget()} />));

    expect(screen.getByRole("button", { name: "Запустить" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Пауза" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Справка" })).not.toBeDisabled();
  });

  it("flips enabled set when selected row changes status", () => {
    renderWithDashboard(withQueryClient(<FunctionWidgetView widget={actionWidget()} />), {
      session: { params: { jobNo: "JO-2", dispatchStatus: "ALLOWED" } },
    });

    expect(screen.getByRole("button", { name: "Запустить" })).not.toBeDisabled();
    expect(screen.getByRole("button", { name: "Пауза" })).toBeDisabled();
  });
});
