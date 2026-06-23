import { getAuthHeaders } from "../auth/session";

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
      ...init?.headers,
    },
    ...init,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export interface ScheduleDefinition {
  path: string;
  scheduleId: string;
  displayName: string;
  description: string;
  enabled: boolean;
  intervalMs: number;
  actionType: string;
  objectPath: string;
  functionName: string;
  actionJson: string;
  lastTickAt: string | null;
  lastError: string | null;
}

export function fetchSchedule(path: string): Promise<ScheduleDefinition> {
  return request(`/api/v1/platform-schedules/by-path?path=${encodeURIComponent(path)}`);
}

export function createSchedule(payload: {
  scheduleId: string;
  displayName?: string;
  description?: string;
  enabled?: boolean;
  intervalMs?: number;
  objectPath?: string;
  functionName?: string;
}): Promise<ScheduleDefinition> {
  return request("/api/v1/platform-schedules", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateSchedule(
  path: string,
  payload: {
    displayName?: string;
    description?: string;
    enabled?: boolean;
    intervalMs?: number;
    objectPath?: string;
    functionName?: string;
  },
): Promise<ScheduleDefinition> {
  return request(`/api/v1/platform-schedules/by-path?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}
