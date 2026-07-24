import { Form, InputNumber, Select, Typography } from "antd";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import type { DashboardLayout, DashboardLayoutPreset } from "../../types/dashboard";
import { DASHBOARD_COLUMNS, DASHBOARD_ROW_HEIGHT } from "../../types/dashboard";
import { fetchDashboardLayoutTemplates } from "../../api/dashboardsCore";
import { applyLayoutPreset, clearLayoutPreset } from "./dashboardLayoutPresets";

interface DashboardSettingsPanelProps {
  layout: DashboardLayout;
  refreshIntervalMs: number;
  dashboardPath: string;
  onLayoutChange: (patch: Partial<DashboardLayout>) => void;
  onRefreshIntervalChange: (ms: number) => void;
  onApplyPreset?: (preset: DashboardLayoutPreset) => void;
  onApplyLayoutTemplate?: (template: string) => void;
  applyLayoutTemplatePending?: boolean;
}

const THEME_OPTIONS: Array<{ id: string; labelKey?: string; label?: string }> = [
  { id: "", labelKey: "settings.themeDefault" },
  { id: "btop", label: "BTOP" },
];

export default function DashboardSettingsPanel({
  layout,
  refreshIntervalMs,
  onLayoutChange,
  onRefreshIntervalChange,
  onApplyPreset,
  onApplyLayoutTemplate,
  applyLayoutTemplatePending,
}: DashboardSettingsPanelProps) {
  const { t } = useTranslation("dashboard");
  const templatesQuery = useQuery({
    queryKey: ["dashboard-layout-templates"],
    queryFn: fetchDashboardLayoutTemplates,
    staleTime: 60_000,
    enabled: Boolean(onApplyLayoutTemplate),
  });

  return (
    <aside className="dashboard-sidebar">
      <header className="dashboard-sidebar-head">
        <Typography.Title level={5} style={{ margin: 0 }}>
          {t("settings.title")}
        </Typography.Title>
      </header>
      <Form layout="vertical" size="small" style={{ padding: 12 }}>
        <Form.Item label={t("settings.layoutPreset")} extra={t("settings.layoutPresetHint")}>
          <Select
            value={layout.layoutPreset ?? ""}
            onChange={(value) => {
              const next = value as DashboardLayoutPreset | "";
              if (!next) {
                onLayoutChange(clearLayoutPreset(layout));
                return;
              }
              if (onApplyPreset) {
                onApplyPreset(next);
              } else {
                onLayoutChange(applyLayoutPreset(next, layout));
              }
            }}
            options={[
              { value: "", label: t("settings.layoutPresetDefault") },
              { value: "video-wall-2x2", label: t("settings.layoutPresetVideoWall2x2") },
              { value: "video-wall-3x3", label: t("settings.layoutPresetVideoWall3x3") },
              { value: "video-wall-4x4", label: t("settings.layoutPresetVideoWall4x4") },
            ]}
          />
        </Form.Item>

        {onApplyLayoutTemplate && (
          <Form.Item label={t("settings.layoutTemplate")} extra={t("settings.layoutTemplateHint")}>
            <Select
              value=""
              disabled={applyLayoutTemplatePending || templatesQuery.isLoading}
              onChange={(template) => {
                if (!template) return;
                if (!window.confirm(t("settings.layoutTemplateConfirm", { template }))) {
                  return;
                }
                onApplyLayoutTemplate(template);
              }}
              options={[
                { value: "", label: t("settings.layoutTemplatePlaceholder") },
                ...(templatesQuery.data ?? []).map((template) => ({
                  value: template,
                  label: template,
                })),
              ]}
            />
          </Form.Item>
        )}

        <Form.Item label={t("settings.refreshInterval")} extra={t("settings.refreshIntervalHint")}>
          <InputNumber
            style={{ width: "100%" }}
            min={500}
            step={500}
            value={refreshIntervalMs}
            onChange={(value) => onRefreshIntervalChange(Number(value ?? refreshIntervalMs))}
          />
        </Form.Item>

        <Form.Item label={t("settings.theme")}>
          <Select
            value={layout.theme ?? ""}
            onChange={(value) => onLayoutChange({ theme: value || undefined })}
            options={THEME_OPTIONS.map((item) => ({
              value: item.id,
              label: item.labelKey ? t(item.labelKey) : item.label,
            }))}
          />
        </Form.Item>

        <Form.Item label={t("settings.gridColumns")}>
          <InputNumber
            style={{ width: "100%" }}
            min={DASHBOARD_COLUMNS}
            max={DASHBOARD_COLUMNS * 2}
            value={layout.columns}
            onChange={(value) => onLayoutChange({ columns: Number(value ?? layout.columns) })}
          />
        </Form.Item>

        <Form.Item label={t("settings.rowHeight")}>
          <InputNumber
            style={{ width: "100%" }}
            min={DASHBOARD_ROW_HEIGHT}
            max={200}
            value={layout.rowHeight}
            onChange={(value) => onLayoutChange({ rowHeight: Number(value ?? layout.rowHeight) })}
          />
        </Form.Item>
      </Form>
    </aside>
  );
}
