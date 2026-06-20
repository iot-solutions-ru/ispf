import { getAuthHeaders } from "../auth/session";

export interface ReportColumn {
  field: string;
  label: string;
}

export interface ReportRunResult {
  reportId: string;
  title: string;
  columns: ReportColumn[];
  rows: Array<Record<string, unknown>>;
  rowCount: number;
  truncated: boolean;
}

export async function runReport(
  appId: string,
  reportId: string,
  parameters?: Record<string, unknown>
): Promise<ReportRunResult> {
  const response = await fetch(`/api/v1/applications/${appId}/reports/${reportId}/run`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ parameters: parameters ?? {} }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Report run failed: ${response.status}`);
  }
  return response.json();
}

export async function downloadReportCsv(
  appId: string,
  reportId: string,
  parameters?: Record<string, string>
): Promise<void> {
  const params = new URLSearchParams(parameters ?? {});
  const response = await fetch(
    `/api/v1/applications/${appId}/reports/${reportId}/export?${params.toString()}`,
    {
      headers: getAuthHeaders(),
    }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Report export failed: ${response.status}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `${reportId}.csv`;
  anchor.click();
  URL.revokeObjectURL(url);
}
