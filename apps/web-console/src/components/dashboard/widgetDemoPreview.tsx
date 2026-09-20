import { useTranslation } from "react-i18next";

export function WidgetDemoBadge() {
  const { t } = useTranslation("widgets");
  return <span className="dash-widget-demo-badge">{t("view.demoBadge")}</span>;
}
