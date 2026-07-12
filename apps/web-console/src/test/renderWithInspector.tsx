import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderOptions } from "@testing-library/react";
import { I18nextProvider } from "react-i18next";
import type { ReactElement, ReactNode } from "react";
import enInspector from "../locales/en/inspector.json";
import enCommon from "../locales/en/common.json";

const testI18n = i18n.createInstance();
const testQueryClient = new QueryClient({
  defaultOptions: { queries: { retry: false } },
});

void testI18n.use(initReactI18next).init({
  lng: "en",
  fallbackLng: "en",
  resources: {
    en: {
      inspector: enInspector,
      common: enCommon,
    },
  },
  interpolation: { escapeValue: false },
});

export function renderWithInspector(ui: ReactElement, options?: Omit<RenderOptions, "wrapper">) {
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={testQueryClient}>
        <I18nextProvider i18n={testI18n}>{children}</I18nextProvider>
      </QueryClientProvider>
    );
  }
  return render(ui, { wrapper: Wrapper, ...options });
}

export { testI18n };
