import { useEffect, useState, type ReactNode } from "react";
import { Button, Drawer, Typography } from "antd";
import { useTranslation } from "react-i18next";
import { shouldLockBodyForOperatorSidebar } from "../../utils/operator/operatorShellLayout";

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
  const [compact, setCompact] = useState(() =>
    typeof window !== "undefined" ? shouldLockBodyForOperatorSidebar(window.innerWidth) : false
  );

  useEffect(() => {
    const onResize = () => setCompact(shouldLockBodyForOperatorSidebar(window.innerWidth));
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  // Backdrop only on compact viewports — on desktop it stole clicks from dashboard widgets.
  const showBackdrop = sidebarOpen && compact;

  return (
    <>
      <div
        className={`operator-layout${sidebarOpen ? " operator-layout--sidebar-open" : ""}${
          layoutClassName ? ` ${layoutClassName}` : ""
        }`}
      >
        <main className={mainClassName}>{main}</main>
        {compact ? (
          <Drawer
            title={t("sidebar.panel")}
            placement="right"
            open={sidebarOpen}
            onClose={onSidebarClose}
            width="min(100vw - 2.5rem, 360px)"
            className="operator-sidebar-drawer"
            rootClassName="operator-sidebar-drawer-root"
            mask={showBackdrop}
            destroyOnHidden={false}
          >
            <div id="operator-sidebar-panel" className="operator-sidebar-drawer-body">
              {sidebar}
            </div>
          </Drawer>
        ) : (
          <aside id="operator-sidebar-panel" className="operator-sidebar" aria-label={t("sidebar.panel")}>
            <div className="operator-sidebar-mobile-head">
              <Typography.Text strong>{t("sidebar.panel")}</Typography.Text>
              <Button size="small" onClick={onSidebarClose}>
                {t("sidebar.close")}
              </Button>
            </div>
            {sidebar}
          </aside>
        )}
      </div>
    </>
  );
}
