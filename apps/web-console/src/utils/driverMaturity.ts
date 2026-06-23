import type { DriverMaturity } from "../types/drivers";
import type { TFunction } from "i18next";

export function normalizeDriverMaturity(raw: string | undefined): DriverMaturity {
  if (raw === "BETA" || raw === "STUB") {
    return raw;
  }
  return "PRODUCTION";
}

export function driverMaturityLabel(maturity: DriverMaturity): string {
  return maturity === "PRODUCTION" ? "Production" : maturity === "BETA" ? "Beta" : "Stub";
}

export function driverMaturityHint(maturity: DriverMaturity, t?: TFunction): string {
  if (t) {
    return t(`inspector:driverMaturity.${maturity}.hint`);
  }
  const fallbacks: Record<DriverMaturity, string> = {
    PRODUCTION: "Supported for typical scenarios",
    BETA: "Works with limitations — see the driver description",
    STUB: "Stub or connectivity check only — not for production",
  };
  return fallbacks[maturity];
}

export function driverMaturityClass(maturity: DriverMaturity): string {
  return `driver-maturity-badge ${maturity.toLowerCase()}`;
}
