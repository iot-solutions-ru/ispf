import type { DriverMaturity } from "../types/drivers";
import type { TFunction } from "i18next";

const KNOWN: ReadonlySet<string> = new Set(["CERTIFIED", "FIELD", "PRODUCTION", "BETA", "STUB"]);

export function normalizeDriverMaturity(raw: string | undefined): DriverMaturity {
  if (raw && KNOWN.has(raw)) {
    return raw as DriverMaturity;
  }
  return "PRODUCTION";
}

export function driverMaturityLabel(maturity: DriverMaturity): string {
  switch (maturity) {
    case "CERTIFIED":
      return "Certified";
    case "FIELD":
      return "Field";
    case "PRODUCTION":
      return "Production";
    case "BETA":
      return "Beta";
    case "STUB":
      return "Stub";
  }
}

export function driverMaturityHint(maturity: DriverMaturity, t?: TFunction): string {
  if (t) {
    return t(`inspector:driverMaturity.${maturity}.hint`);
  }
  const fallbacks: Record<DriverMaturity, string> = {
    CERTIFIED: "Field-proven plus protocol conformance / formal cert evidence",
    FIELD: "Named-site soak and OT sign-off (above lab Production)",
    PRODUCTION: "Supported for typical scenarios",
    BETA: "Works with limitations — see the driver description",
    STUB: "Stub or connectivity check only — not for production",
  };
  return fallbacks[maturity];
}

export function driverMaturityClass(maturity: DriverMaturity): string {
  return `driver-maturity-badge ${maturity.toLowerCase()}`;
}
