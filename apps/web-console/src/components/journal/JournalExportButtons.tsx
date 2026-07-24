import { useTranslation } from "react-i18next";
import { Button, Space } from "antd";
import {
  downloadJournalExport,
  type JournalExportFormat,
  type JournalExportRow,
} from "../../utils/journal/journalExport";

interface JournalExportButtonsProps {
  filenameBase: string;
  rows: JournalExportRow[];
  disabled?: boolean;
}

export default function JournalExportButtons({
  filenameBase,
  rows,
  disabled = false,
}: JournalExportButtonsProps) {
  const { t } = useTranslation("journal");

  if (rows.length === 0) {
    return null;
  }

  const exportRows = (format: JournalExportFormat) => {
    downloadJournalExport(filenameBase, format, rows);
  };

  return (
    <Space.Compact className="journal-export-actions" role="group" aria-label={t("export.label")}>
      <Button
        size="small"
        disabled={disabled}
        onClick={() => exportRows("csv")}
      >
        {t("export.csv")}
      </Button>
      <Button
        size="small"
        disabled={disabled}
        onClick={() => exportRows("json")}
      >
        {t("export.json")}
      </Button>
    </Space.Compact>
  );
}
