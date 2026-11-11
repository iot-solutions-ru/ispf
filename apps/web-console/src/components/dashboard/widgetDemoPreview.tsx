import { useTranslation } from "react-i18next";

export function WidgetDemoBadge() {
  const { t } = useTranslation("widgets");
  return (
    <span className="dash-widget-demo-badge" data-testid="widget-demo-preview">
      {t("view.demoBadge")}
    </span>
  );
}
