import type { DriverMaturity } from "../types/drivers";

const LABELS: Record<DriverMaturity, string> = {
  PRODUCTION: "Production",
  BETA: "Beta",
  STUB: "Stub",
};

const HINTS: Record<DriverMaturity, string> = {
  PRODUCTION: "Поддерживается для типовых сценариев",
  BETA: "Работает с ограничениями — см. описание драйвера",
  STUB: "Заглушка или только проверка связности — не для production",
};

export function normalizeDriverMaturity(raw: string | undefined): DriverMaturity {
  if (raw === "BETA" || raw === "STUB") {
    return raw;
  }
  return "PRODUCTION";
}

export function driverMaturityLabel(maturity: DriverMaturity): string {
  return LABELS[maturity];
}

export function driverMaturityHint(maturity: DriverMaturity): string {
  return HINTS[maturity];
}

export function driverMaturityClass(maturity: DriverMaturity): string {
  return `driver-maturity-badge ${maturity.toLowerCase()}`;
}
