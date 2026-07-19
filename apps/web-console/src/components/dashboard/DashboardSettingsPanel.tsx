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
        <h4>{t("settings.title")}</h4>
      </header>
      <div className="form-grid compact">
        <label>
          {t("settings.layoutPreset")}
          <select
            value={layout.layoutPreset ?? ""}
            onChange={(e) => {
              const value = e.target.value as DashboardLayoutPreset | "";
              if (!value) {
                onLayoutChange(clearLayoutPreset(layout));
                return;
              }
              if (onApplyPreset) {
                onApplyPreset(value);
              } else {
                onLayoutChange(applyLayoutPreset(value, layout));
              }
            }}
          >
            <option value="">{t("settings.layoutPresetDefault")}</option>
            <option value="video-wall-2x2">{t("settings.layoutPresetVideoWall2x2")}</option>
            <option value="video-wall-3x3">{t("settings.layoutPresetVideoWall3x3")}</option>
            <option value="video-wall-4x4">{t("settings.layoutPresetVideoWall4x4")}</option>
          </select>
          <span className="hint">{t("settings.layoutPresetHint")}</span>
        </label>
        {onApplyLayoutTemplate && (
          <label>
            {t("settings.layoutTemplate")}
            <select
              defaultValue=""
              disabled={applyLayoutTemplatePending || templatesQuery.isLoading}
              onChange={(e) => {
                const template = e.target.value;
                e.target.value = "";
                if (!template) return;
                if (!window.confirm(t("settings.layoutTemplateConfirm", { template }))) {
                  return;
                }
                onApplyLayoutTemplate(template);
              }}
            >
              <option value="">{t("settings.layoutTemplatePlaceholder")}</option>
              {(templatesQuery.data ?? []).map((template) => (
                <option key={template} value={template}>
                  {template}
                </option>
              ))}
            </select>
            <span className="hint">{t("settings.layoutTemplateHint")}</span>
          </label>
        )}
        <label>
          {t("settings.refreshInterval")}
          <input
            type="number"
            min={500}
            step={500}
            value={refreshIntervalMs}
            onChange={(e) => onRefreshIntervalChange(Number(e.target.value))}
          />
          <span className="hint">{t("settings.refreshIntervalHint")}</span>
        </label>
        <label>
          {t("settings.theme")}
          <select
            value={layout.theme ?? ""}
            onChange={(e) =>
              onLayoutChange({ theme: e.target.value || undefined })
            }
          >
            {THEME_OPTIONS.map((item) => (
              <option key={item.id || "default"} value={item.id}>
                {item.labelKey ? t(item.labelKey) : item.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t("settings.gridColumns")}
          <input
            type="number"
            min={DASHBOARD_COLUMNS}
            max={DASHBOARD_COLUMNS * 2}
            value={layout.columns}
            onChange={(e) => onLayoutChange({ columns: Number(e.target.value) })}
          />
        </label>
        <label>
          {t("settings.rowHeight")}
          <input
            type="number"
            min={DASHBOARD_ROW_HEIGHT}
            max={200}
            value={layout.rowHeight}
            onChange={(e) => onLayoutChange({ rowHeight: Number(e.target.value) })}
          />
        </label>
      </div>
    </aside>
  );
}
