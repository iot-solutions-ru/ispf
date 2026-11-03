export type DriverMaturity = "PRODUCTION" | "BETA" | "STUB";

export interface DriverMetadata {
  id: string;
  name: string;
  version: string;
  description: string;
  vendor: string;
  configurationSchema: Record<string, string>;
  maturity?: DriverMaturity;
  capabilities?: string[];
}

export function driverSupportsWrite(driver: DriverMetadata | undefined): boolean {
  return Boolean(driver?.capabilities?.includes("write"));
}

export interface DriverRuntimeStatus {
  devicePath: string;
  driverId: string;
  status: "STOPPED" | "RUNNING" | "ERROR" | string;
  connected: boolean;
  pollIntervalMs: number;
  lastError: string | null;
}

export interface ConfigureDriverPayload {
  driverId: string;
  pollIntervalMs: number;
  configuration: Record<string, string>;
  /** Legacy flat map (variable → point id). Runtime poll uses these ids. */
  pointMappings: Record<string, string>;
  /** Full driverPointMappingsJson body (BL-59 extended objects with haystackTags). */
  pointMappingsJson?: string;
  autoStart?: boolean;
}
