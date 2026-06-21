import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { downloadReportExportByPath, fetchReport, runReportByPath } from "../../../api/reports";
import type { ReportWidget } from "../../../types/dashboard";
import BffDataTable from "../../operator/BffDataTable";

interface ReportWidgetViewProps {
  widget: ReportWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ReportWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ReportWidgetViewProps) {
  const reportMetaQuery = useQuery({
    queryKey: ["report", widget.reportPath],
    queryFn: () => fetchReport(widget.reportPath),
    enabled: Boolean(widget.reportPath?.trim()) && !editable,
  });

  const runQuery = useQuery({
    queryKey: ["report-widget", widget.reportPath],
    queryFn: () => runReportByPath(widget.reportPath, {}),
    enabled: Boolean(widget.reportPath?.trim()) && !editable,
    refetchInterval: refreshIntervalMs,
  });

  const labels = useMemo(() => {
    const map: Record<string, string> = {};
    for (const col of runQuery.data?.columns ?? []) {
      map[col.field] = col.label;
    }
    return map;
  }, [runQuery.data?.columns]);

  return (
    <div className="dash-widget dash-report-widget">
      <div className="dash-widget-title">
        {widget.title}
        {reportMetaQuery.data?.hasTemplate && !editable && (
          <button
            type="button"
            className="btn btn-sm"
            style={{ marginLeft: "0.5rem" }}
            onClick={() => void downloadReportExportByPath(widget.reportPath, "pdf")}
          >
            PDF
          </button>
        )}
      </div>
      <div className="dash-widget-body">
        {!widget.reportPath?.trim() && <p className="hint">Укажите reportPath</p>}
        {runQuery.isLoading && <p className="hint">Загрузка…</p>}
        {runQuery.error && <p className="hint error">{(runQuery.error as Error).message}</p>}
        {runQuery.data && (
          <BffDataTable
            rows={runQuery.data.rows}
            labels={labels}
            emptyMessage={widget.emptyMessage ?? "Нет строк"}
          />
        )}
      </div>
    </div>
  );
}
