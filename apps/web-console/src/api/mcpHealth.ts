import { getAuthHeaders } from "../auth/session";

export interface McpHealth {
  enabled: boolean;
  stdioEnabled: boolean;
  serverName: string;
  protocolVersion: string;
  toolCount: number;
  httpEndpoint: string | null;
}

export function fetchMcpHealth(): Promise<McpHealth> {
  return fetch("/api/v1/platform/mcp/health", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await response.text() || `Request failed: ${response.status}`);
    }
    return response.json();
  });
}
