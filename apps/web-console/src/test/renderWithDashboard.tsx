import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { render, type RenderOptions } from "@testing-library/react";
import { I18nextProvider } from "react-i18next";
import type { ReactElement, ReactNode } from "react";
import { DashboardProvider, type DashboardSession } from "../components/dashboard/DashboardContext";
import enWidgets from "../locales/en/widgets.json";
import enDashboard from "../locales/en/dashboard.json";
import enCommon from "../locales/en/common.json";

const testI18n = i18n.createInstance();

void testI18n.use(initReactI18next).init({
  lng: "en",
  fallbackLng: "en",
  resources: {
    en: {
      widgets: enWidgets,
      dashboard: enDashboard,
      common: enCommon,
    },
  },
  interpolation: { escapeValue: false },
});

interface RenderWithDashboardOptions extends Omit<RenderOptions, "wrapper"> {
  session?: Partial<DashboardSession>;
}

export function renderWithDashboard(
  ui: ReactElement,
  options: RenderWithDashboardOptions = {},
) {
  const { session, ...renderOptions } = options;

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <I18nextProvider i18n={testI18n}>
        <DashboardProvider
          session={{
            selection: session?.selection ?? {},
            params: session?.params ?? {},
            widgets: session?.widgets ?? {},
          }}
        >
          {children}
        </DashboardProvider>
      </I18nextProvider>
    );
  }

  return render(ui, { wrapper: Wrapper, ...renderOptions });
}

export { testI18n };
