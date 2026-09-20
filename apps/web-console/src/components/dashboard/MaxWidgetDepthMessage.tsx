// Extracted from renderDashboardWidget.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import { useTranslation } from "react-i18next";

export function MaxWidgetDepthMessage() {
  const { t } = useTranslation("widgets");
  return <div className="hint">{t("error.maxDepth")}</div>;
}
