import { useTranslation } from "react-i18next";
import type { SheetImportReport } from "../sheet/sheetXlsx";

const IMPORT_FN_PREVIEW = 12;

export type SpreadsheetImportNoticeState =
  | { kind: "success"; sheetCount: number; firstName: string }
  | { kind: "warning"; sheetCount: number; report: SheetImportReport }
  | { kind: "error" };

export function hasSpreadsheetImportIssues(report: SheetImportReport): boolean {
  return report.unsupportedFunctions.length > 0 || report.truncations.length > 0;
}

interface SpreadsheetImportNoticeProps {
  notice: SpreadsheetImportNoticeState;
  onDismiss: () => void;
}

export default function SpreadsheetImportNotice({
  notice,
  onDismiss,
}: SpreadsheetImportNoticeProps) {
  const { t } = useTranslation("widgets");

  if (notice.kind === "warning") {
    return (
      <div className="dash-sheet-import-notice dash-sheet-import-warning" role="alert">
        <div className="dash-sheet-import-warning-header">
          <strong>{t("spreadsheet.importWarningTitle", { count: notice.sheetCount })}</strong>
          <button
            type="button"
            className="dash-sheet-import-dismiss"
            aria-label={t("spreadsheet.importDismiss")}
            onClick={onDismiss}
          >
            ×
          </button>
        </div>
        {notice.report.unsupportedFunctions.length > 0 ? (
          <>
            <p>
              {t("spreadsheet.importUnsupportedFunctions", {
                count: notice.report.unsupportedFunctions.length,
              })}
            </p>
            <ul className="dash-sheet-import-fn-list">
              {notice.report.unsupportedFunctions.slice(0, IMPORT_FN_PREVIEW).map((fn) => (
                <li key={fn}>
                  <code>{fn}</code>
                </li>
              ))}
            </ul>
            {notice.report.unsupportedFunctions.length > IMPORT_FN_PREVIEW ? (
              <p className="dash-sheet-import-more">
                {t("spreadsheet.importMoreFunctions", {
                  count: notice.report.unsupportedFunctions.length - IMPORT_FN_PREVIEW,
                })}
              </p>
            ) : null}
            <p className="dash-sheet-import-hint">{t("spreadsheet.importNameHint")}</p>
          </>
        ) : null}
        {notice.report.truncations.length > 0 ? (
          <ul className="dash-sheet-import-truncations">
            {notice.report.truncations.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        ) : null}
      </div>
    );
  }

  return (
    <div
      className={`dash-sheet-import-notice${
        notice.kind === "error" ? " dash-sheet-import-error" : " dash-sheet-import-success"
      }`}
      role="status"
    >
      {notice.kind === "error"
        ? t("spreadsheet.importXlsxError")
        : t("spreadsheet.importXlsxWorkbookSuccess", {
            count: notice.sheetCount,
            name: notice.firstName,
          })}
    </div>
  );
}
