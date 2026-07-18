import { useTranslation } from "react-i18next";
import { formatUserDateTime } from "../../utils/ui/formatDateTime";

interface OperatorOfflineBannerProps {
  visible: boolean;
  cachedAt?: string | null;
  reconnecting?: boolean;
}

export default function OperatorOfflineBanner({
  visible,
  cachedAt,
  reconnecting,
}: OperatorOfflineBannerProps) {
  const { t } = useTranslation("operator");

  if (!visible && !reconnecting) {
    return null;
  }

  return (
    <div
      className="operator-offline-banner op-alert op-alert-info"
      data-testid="operator-offline-banner"
      role="status"
    >
      {reconnecting
        ? t("offline.reconnecting")
        : t("offline.staleBanner", {
            time: cachedAt ? formatUserDateTime(cachedAt) : t("offline.unknownTime"),
          })}
    </div>
  );
}
