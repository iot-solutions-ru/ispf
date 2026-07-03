import { useTranslation } from "react-i18next";

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
            time: cachedAt ? new Date(cachedAt).toLocaleString() : t("offline.unknownTime"),
          })}
    </div>
  );
}
