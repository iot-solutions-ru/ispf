import { getAuthHeaders } from "../auth/session";

export interface PlatformMetricSection {
  id: string;
  title: string;
  values: Record<string, unknown>;
}

export interface PlatformMetricsResponse {
  timestamp: string;
  sections: PlatformMetricSection[];
}

export function fetchPlatformMetrics(): Promise<PlatformMetricsResponse> {
  return fetch("/api/v1/platform/metrics", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Request failed: ${response.status}`);
    }
    return response.json();
  });
}
