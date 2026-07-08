import type { FederationPeerHealthLevel } from "../../api/federation";
import { useTranslation } from "react-i18next";

interface FederationPeerHealthBadgeProps {
  level: FederationPeerHealthLevel;
  summary?: string;
  compact?: boolean;
}

export function federationPeerHealthBadgeClass(level: FederationPeerHealthLevel | undefined): string {
  if (level === "GREEN") return "badge ok";
  if (level === "YELLOW") return "badge warn";
  return "badge danger";
}

export default function FederationPeerHealthBadge({
  level,
  summary,
  compact = false,
}: FederationPeerHealthBadgeProps) {
  const { t } = useTranslation("federation");
  return (
    <span
      className={`federation-peer-health-badge ${federationPeerHealthBadgeClass(level)}${compact ? " is-compact" : ""}`}
      title={summary ?? t("peers.healthUnknown")}
      data-health={level}
    >
      {t(`peers.health.${level.toLowerCase()}`)}
    </span>
  );
}
