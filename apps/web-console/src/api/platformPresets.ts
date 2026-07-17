import { request } from "./httpClient";

export interface VirtClusterPresetResult {
  status: string;
  folder: string;
  devices: string[];
  hub: string;
  alert: string;
  overviewDashboard: string;
  detailDashboard: string;
  operatorAppId?: string;
  operatorUrlHint?: string;
}

export function installVirtClusterPreset(options?: {
  wireOperatorApp?: boolean;
  operatorAppId?: string;
}): Promise<VirtClusterPresetResult> {
  return request("/api/v1/platform/presets/virt-cluster", {
    method: "POST",
    body: JSON.stringify({
      wireOperatorApp: options?.wireOperatorApp ?? true,
      operatorAppId: options?.operatorAppId ?? "platform",
    }),
  });
}
