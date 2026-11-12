import { useTranslation } from "react-i18next";
import type { DriverMetadata } from "../types/drivers";
import DriverMaturityBadge from "./DriverMaturityBadge";

interface DriverHonestyBadgesProps {
  driver?: DriverMetadata;
  compact?: boolean;
}

export default function DriverHonestyBadges({ driver, compact = false }: DriverHonestyBadgesProps) {
  const { t } = useTranslation("inspector");
  if (!driver) {
    return null;
  }
  const readOnly =
    driver.readOnly ??
    (Array.isArray(driver.capabilities) && !driver.capabilities.includes("write"));
  return (
    <>
      <DriverMaturityBadge maturity={driver.maturity} compact={compact} />
      {driver.evidence && (
        <span
          className={`driver-maturity-badge ${driver.evidence === "FIELD" ? "field" : "loopback"}`}
          title={t(`driverEvidence.${driver.evidence}.hint`)}
        >
          {t(`driverEvidence.${driver.evidence}.label`)}
        </span>
      )}
      {readOnly && (
        <span className="driver-maturity-badge readonly" title={t("driverEvidence.readOnly.hint")}>
          {t("driverEvidence.readOnly.label")}
        </span>
      )}
    </>
  );
}
