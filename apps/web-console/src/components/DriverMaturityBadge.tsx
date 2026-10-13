import { useTranslation } from "react-i18next";
import {
  driverMaturityClass,
  driverMaturityHint,
  driverMaturityLabel,
  normalizeDriverMaturity,
} from "../utils/driverMaturity";

interface DriverMaturityBadgeProps {
  maturity?: string;
  compact?: boolean;
}

export default function DriverMaturityBadge({ maturity, compact = false }: DriverMaturityBadgeProps) {
  const { t } = useTranslation("inspector");
  const level = normalizeDriverMaturity(maturity);
  // Compact lists hide lab Production only; FIELD/CERTIFIED stay visible.
  if (level === "PRODUCTION" && compact) {
    return null;
  }
  return (
    <span
      className={driverMaturityClass(level)}
      title={driverMaturityHint(level, t)}
    >
      {driverMaturityLabel(level)}
    </span>
  );
}

