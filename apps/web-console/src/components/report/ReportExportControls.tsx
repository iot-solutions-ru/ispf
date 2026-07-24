import { Button, Select, Space } from "antd";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import type { ReportExportFormat } from "../../api/reports";
import { REPORT_EXPORT_OPTIONS, type ReportExportOption } from "./reportExportOptions";

interface ReportExportControlsProps {
  options?: ReportExportOption[];
  disabled?: boolean;
  busy?: boolean;
  className?: string;
  size?: "default" | "sm";
  onExport: (format: ReportExportFormat) => void | Promise<void>;
}

export default function ReportExportControls({
  options = REPORT_EXPORT_OPTIONS,
  disabled = false,
  busy = false,
  className,
  size = "default",
  onExport,
}: ReportExportControlsProps) {
  const { t } = useTranslation("report");
  const [format, setFormat] = useState<ReportExportFormat>(options[0]?.value ?? "xlsx");

  useEffect(() => {
    if (!options.some((option) => option.value === format)) {
      setFormat(options[0]?.value ?? "xlsx");
    }
  }, [options, format]);

  if (options.length === 0) {
    return null;
  }

  const selected = options.find((option) => option.value === format) ?? options[0];
  const controlSize = size === "sm" ? "small" : "middle";

  return (
    <Space
      className={["report-export-controls", size === "sm" ? "report-export-controls-sm" : "", className ?? ""]
        .filter(Boolean)
        .join(" ")}
      wrap
    >
      <Select
        className="report-export-format"
        size={controlSize}
        value={format}
        disabled={disabled || busy}
        aria-label={t("export.formatLabel")}
        title={selected.title}
        onChange={(value) => setFormat(value)}
        options={options.map((option) => ({
          value: option.value,
          label: option.label,
          title: option.title,
        }))}
        popupMatchSelectWidth={false}
      />
      <Button
        size={controlSize}
        disabled={disabled || busy}
        loading={busy}
        title={selected.title}
        onClick={() => void onExport(format)}
      >
        {busy ? t("export.exporting") : t("export.export")}
      </Button>
    </Space>
  );
}
