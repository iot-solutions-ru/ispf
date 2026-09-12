import { getAuthHeaders } from "../auth/session";

export interface PlatformRuntimeSetting {
  id: string;
  envVar: string;
  propertyKey: string;
  type: "boolean" | "integer" | "string" | "duration";
  value: string;
  defaultValue: string;
  source: "environment" | "file" | "default" | "override";
  environmentValue?: string | null;
  overridesEnvironment?: boolean;
  sensitive: boolean;
  editable: boolean;
  hotReloadable: boolean;
  restartRequired: boolean;
}

export interface PlatformRuntimeSettingsSection {
  id: string;
  title: string;
  settings: PlatformRuntimeSetting[];
}

export interface PlatformRuntimeSettingsResponse {
  settingsFile: string;
  sections: PlatformRuntimeSettingsSection[];
}

export interface PlatformRuntimeSettingsPatchResult {
  restartRequired: boolean;
  appliedLive: string[];
  skippedEnvLocked: string[];
  errors: string[];
}

export interface PlatformRestartAccepted {
  accepted: boolean;
  delayMs: number;
  mode: string;
  message: string;
}

const SENSITIVE_MASK = "********";

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchPlatformRuntimeSettings(): Promise<PlatformRuntimeSettingsResponse> {
  return fetch("/api/v1/platform/runtime-settings", {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<PlatformRuntimeSettingsResponse>(response));
}

export function patchPlatformRuntimeSettings(
  values: Record<string, string>,
): Promise<PlatformRuntimeSettingsPatchResult> {
  return fetch("/api/v1/platform/runtime-settings", {
    method: "PATCH",
    headers: {
      ...getAuthHeaders(),
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ values }),
  }).then((response) => parseJson<PlatformRuntimeSettingsPatchResult>(response));
}

export function restartPlatformServer(): Promise<PlatformRestartAccepted> {
  return fetch("/api/v1/platform/runtime-settings/restart", {
    method: "POST",
    headers: getAuthHeaders(),
  }).then((response) => parseJson<PlatformRestartAccepted>(response));
}

export function isSensitiveUnchanged(setting: PlatformRuntimeSetting, draft: string | undefined): boolean {
  if (!setting.sensitive) {
    return false;
  }
  const value = draft ?? setting.value;
  return value === SENSITIVE_MASK || value === setting.value;
}

export async function waitForPlatformReady(timeoutMs = 120_000, intervalMs = 2_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  await new Promise((resolve) => setTimeout(resolve, 3_000));
  while (Date.now() < deadline) {
    try {
      const response = await fetch("/api/v1/info", { cache: "no-store" });
      if (response.ok) {
        return;
      }
    } catch {
      // server still down
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error("Server did not become ready after restart");
}
