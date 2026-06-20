import type { DriverMaturity } from "../types/drivers";
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
  const level = normalizeDriverMaturity(maturity);
  if (level === "PRODUCTION" && compact) {
    return null;
  }
  return (
    <span
      className={driverMaturityClass(level)}
      title={driverMaturityHint(level)}
    >
      {driverMaturityLabel(level)}
    </span>
  );
}

export function formatDriverOptionLabel(
  driverId: string,
  name: string,
  maturity?: DriverMaturity | string
): string {
  const level = normalizeDriverMaturity(maturity);
  if (level === "PRODUCTION") {
    return `${driverId} — ${name}`;
  }
  return `${driverId} — ${name} [${driverMaturityLabel(level)}]`;
}
