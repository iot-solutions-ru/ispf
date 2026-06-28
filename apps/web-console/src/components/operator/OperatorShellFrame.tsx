import { useTranslation } from "react-i18next";
import type { ReactNode } from "react";

interface OperatorShellFrameProps {
  main: ReactNode;
  sidebar: ReactNode;
  sidebarOpen: boolean;
  onSidebarClose: () => void;
  layoutClassName?: string;
  mainClassName?: string;
}

export default function OperatorShellFrame({
  main,
  sidebar,
  sidebarOpen,
  onSidebarClose,
  layoutClassName = "",
  mainClassName = "operator-dashboard",
}: OperatorShellFrameProps) {
  const { t } = useTranslation("operator");

  return (
    <>
      {sidebarOpen ? (
        <button
          type="button"
          className="operator-sidebar-backdrop"
          aria-label={t("sidebar.close")}
          onClick={onSidebarClose}
        />
      ) : null}
      <div
        className={`operator-layout${sidebarOpen ? " operator-layout--sidebar-open" : ""}${
          layoutClassName ? ` ${layoutClassName}` : ""
        }`}
      >
        <main className={mainClassName}>{main}</main>
        <aside id="operator-sidebar-panel" className="operator-sidebar" aria-label={t("sidebar.panel")}>
          <div className="operator-sidebar-mobile-head">
            <strong>{t("sidebar.panel")}</strong>
            <button type="button" className="btn small" onClick={onSidebarClose}>
              {t("sidebar.close")}
            </button>
          </div>
          {sidebar}
        </aside>
      </div>
    </>
  );
}
