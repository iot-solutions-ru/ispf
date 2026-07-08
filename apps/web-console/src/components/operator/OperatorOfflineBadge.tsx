import { useTranslation } from "react-i18next";

interface OperatorOfflineBadgeProps {
  visible: boolean;
}

export default function OperatorOfflineBadge({ visible }: OperatorOfflineBadgeProps) {
  const { t } = useTranslation("operator");

  if (!visible) {
    return null;
  }

  return (
    <span
      className="operator-offline-badge"
      data-testid="operator-offline-badge"
      role="status"
      title={t("offline.badgeTitle")}
    >
      {t("offline.badge")}
    </span>
  );
}
