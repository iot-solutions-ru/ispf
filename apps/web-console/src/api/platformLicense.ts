import { getAuthHeaders } from "../auth/session";

export interface PlatformLicenseInfo {
  installationId: string;
  enforce: boolean;
  mode: string;
  tier: string | null;
  expiresAt: string | null;
  valid: boolean;
  message: string;
}

export function fetchPlatformLicense(): Promise<PlatformLicenseInfo> {
  return fetch("/api/v1/platform/license", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await response.text() || `Request failed: ${response.status}`);
    }
    return response.json();
  });
}
