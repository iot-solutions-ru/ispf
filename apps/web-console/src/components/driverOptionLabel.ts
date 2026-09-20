// Extracted from DriverMaturityBadge.tsx so that the component module only exports components
// (react-refresh/only-export-components).
import { driverMaturityLabel, normalizeDriverMaturity } from "../utils/driverMaturity";

export function formatDriverOptionLabel(
  driverId: string,
  name: string,
  maturity?: string,
): string {
  const level = normalizeDriverMaturity(maturity);
  return `${driverId} — ${name} [${driverMaturityLabel(level)}]`;
}
