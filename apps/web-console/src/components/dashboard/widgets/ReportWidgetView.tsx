import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import {
  downloadReportExportByPath,
  fetchReport,
  runReportByPath,
  type ReportExportFormat,
} from "../../../api/reports";
import type { ReportWidget } from "../../../types/dashboard";
import { parseJsonArray, parseJsonObject } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import { useWidgetStyles } from "../widgetStyles";
import BffDataTable from "../../operator/BffDataTable";
import ReportExportControls from "../../report/ReportExportControls";
import { filterReportExportOptions } from "../../report/reportExportOptions";
import { useOptionalUserTimeZone } from "../../../context/UserTimeZoneContext";
import { enrichReportRunParameters } from "../../../utils/reportRunParameters";

interface ReportWidgetViewProps {
  widget: ReportWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

function mergeReportRunParameters(
  widget: ReportWidget,
  sessionParams: Record<string, unknown>,
  defaultParameters?: Record<string, unknown>,
  declaredParameters?: string[]
): Record<string, unknown> {
  const merged: Record<string, unknown> = { ...(defaultParameters ?? {}) };
  const staticParams = parseJsonObject(widget.parametersJson);
  if (staticParams) {
    Object.assign(merged, staticParams);
  }
  const contextMap = parseJsonObject(widget.contextParamsJson);
  if (contextMap) {
    for (const [reportParam, sessionKeyRaw] of Object.entries(contextMap)) {
      const sessionKey = String(sessionKeyRaw).trim();
      if (!sessionKey) continue;
      const value = sessionParams[sessionKey];
      if (value !== undefined && value !== null && String(value).trim() !== "") {
        merged[reportParam] = value;
      }
    }
    for (const reportParam of Object.keys(contextMap)) {
      if (!(reportParam in merged)) {
        merged[reportParam] = "";
      }
    }
  }
  for (const name of declaredParameters ?? []) {
    if (!(name in merged)) {
      merged[name] = "";
    }
  }
  return merged;
}

function exportEnabled(widget: ReportWidget, format: ReportExportFormat): boolean {
  if (format === "csv") return widget.showCsv !== false;
  if (format === "html") return widget.showHtml !== false;
  if (format === "xlsx") return widget.showXlsx !== false;
  if (format === "pdf") return widget.showPdf !== false;
  return false;
}

export default function ReportWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ReportWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const { params: sessionParams, setParams } = useDashboardContext();
  const userTimeZone = useOptionalUserTimeZone();
  const styles = useWidgetStyles(widget.stylesJson);
  const [exportBusy, setExportBusy] = useState(false);

  const statusDotColumns = useMemo(
    () => parseJsonArray<string>(widget.statusDotColumnsJson, []),
    [widget.statusDotColumnsJson]
  );

  const filterColumns = useMemo(
    () => parseJsonArray<string>(widget.columnFiltersJson, []),
    [widget.columnFiltersJson]
  );

  const rowParamsFromRow = useMemo(
    () => parseJsonObject(widget.rowParamsFromRowJson) ?? {},
    [widget.rowParamsFromRowJson]
  );
  const selectionColumn = widget.rowSelectionKey ?? "id";
  const selectionParamKey =
    Object.keys(rowParamsFromRow)[0] ?? widget.rowSelectionKey ?? "id";
  const selectedKey =
    sessionParams[selectionParamKey] != null &&
    String(sessionParams[selectionParamKey]).trim() !== ""
      ? String(sessionParams[selectionParamKey])
      : null;

  function applyRowSelection(row: Record<string, unknown> | null) {
    if (Object.keys(rowParamsFromRow).length === 0) {
      if (!row) {
        setParams({ [selectionParamKey]: "" });
        return;
      }
      setParams({ [selectionParamKey]: String(row[selectionColumn] ?? "") });
      return;
    }
    const patch: Record<string, unknown> = {};
    for (const [paramKey, columnRaw] of Object.entries(rowParamsFromRow)) {
      const column = String(columnRaw).trim();
      patch[paramKey] = row && column ? row[column] ?? "" : "";
    }
    setParams(patch);
  }

  const reportMetaQuery = useQuery({
    queryKey: ["report", widget.reportPath],
    queryFn: () => fetchReport(widget.reportPath),
    enabled: Boolean(widget.reportPath?.trim()),
  });

  const runParameters = useMemo(
    () =>
      enrichReportRunParameters(
        mergeReportRunParameters(
          widget,
          sessionParams,
          reportMetaQuery.data?.defaultParameters,
          reportMetaQuery.data?.parameters
        ),
        userTimeZone?.timeZone
      ),
    [
      widget,
      sessionParams,
      reportMetaQuery.data?.defaultParameters,
      reportMetaQuery.data?.parameters,
      userTimeZone?.timeZone,
    ]
  );

  const parametersReady = reportMetaQuery.isSuccess;

  const runQuery = useQuery({
    queryKey: ["report-widget", widget.reportPath, runParameters],
    queryFn: () => runReportByPath(widget.reportPath, runParameters),
    enabled: Boolean(widget.reportPath?.trim()) && parametersReady,
    refetchInterval: editable ? false : refreshIntervalMs,
  });

  useEffect(() => {
    if (!widget.selectable || editable || !runQuery.data?.rows?.length) {
      return;
    }
    if (selectedKey) {
      return;
    }
    if (widget.autoSelectFirstRow === true) {
      applyRowSelection(runQuery.data.rows[0]);
    }
  }, [
    widget.selectable,
    widget.autoSelectFirstRow,
    editable,
    runQuery.data?.rows,
    selectedKey,
  ]);

  const labels = useMemo(() => {
    const map: Record<string, string> = {};
    for (const col of runQuery.data?.columns ?? []) {
      map[col.field] = col.label;
    }
    return map;
  }, [runQuery.data?.columns]);

  const exportOptions = useMemo(
    () => filterReportExportOptions((format) => exportEnabled(widget, format)),
    [widget]
  );

  async function handleExport(format: ReportExportFormat) {
    if (!parametersReady) return;
    const params = Object.fromEntries(
      Object.entries(runParameters).map(([key, value]) => [key, String(value)])
    );
    setExportBusy(true);
    try {
      await downloadReportExportByPath(widget.reportPath, format, params);
    } finally {
      setExportBusy(false);
    }
  }

  return (
    <div className="dash-widget dash-report-widget" style={styles.card}>
      <div className="dash-widget-title" style={styles.title}>
        {widget.title}
        {exportOptions.length > 0 && !editable && (
          <span className="dash-report-toolbar">
            <ReportExportControls
              options={exportOptions}
              size="sm"
              busy={exportBusy}
              disabled={!widget.reportPath?.trim() || !parametersReady || runQuery.isLoading}
              onExport={handleExport}
            />
          </span>
        )}
      </div>
      <div className="dash-widget-body">
        {!widget.reportPath?.trim() && <p className="hint">{t("view.specifyReportPath")}</p>}
        {editable && widget.reportPath?.trim() && (
          <p className="hint">{t("view.reportPreview")}</p>
        )}
        {runQuery.isLoading && <p className="hint">{t("common:action.loading")}</p>}
        {runQuery.error && <p className="hint error">{(runQuery.error as Error).message}</p>}
        {runQuery.data && (
          <>
            {runQuery.data.truncated && widget.showTruncatedWarning !== false && (
              <div className="banner warning report-truncated-banner">
                {t("view.reportTruncated", { count: runQuery.data.rowCount })}
              </div>
            )}
            <BffDataTable
              rows={runQuery.data.rows}
              labels={labels}
              emptyMessage={widget.emptyMessage ?? t("empty.noRows")}
              selectable={Boolean(widget.selectable) && !editable}
              selectionKey={selectionColumn}
              selectedKey={selectedKey}
              onSelect={applyRowSelection}
              statusColumns={statusDotColumns}
              filterable={Boolean(widget.filterable) && !editable}
              filterColumns={filterColumns}
            />
          </>
        )}
      </div>
    </div>
  );
}

