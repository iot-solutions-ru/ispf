export type DriverMaturity = "PRODUCTION" | "BETA" | "STUB";

export interface DriverMetadata {
  id: string;
  name: string;
  version: string;
  description: string;
  vendor: string;
  configurationSchema: Record<string, string>;
  maturity?: DriverMaturity;
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
  pointMappings: Record<string, string>;
  autoStart?: boolean;
}
