import type { FederationPeerHealthLevel } from "../../api/federation";
import { Tag } from "antd";
import { useTranslation } from "react-i18next";

interface FederationPeerHealthBadgeProps {
  level: FederationPeerHealthLevel;
  summary?: string;
  compact?: boolean;
}

export function federationPeerHealthBadgeClass(level: FederationPeerHealthLevel | undefined): string {
  if (level === "GREEN") return "success";
  if (level === "YELLOW") return "warning";
  return "error";
}

export default function FederationPeerHealthBadge({
  level,
  summary,
  compact = false,
}: FederationPeerHealthBadgeProps) {
  const { t } = useTranslation("federation");
  return (
    <Tag
      color={federationPeerHealthBadgeClass(level)}
      className={`federation-peer-health-badge${compact ? " is-compact" : ""}`}
      title={summary ?? t("peers.healthUnknown")}
      data-health={level}
    >
      {t(`peers.health.${level.toLowerCase()}`)}
    </Tag>
  );
}
