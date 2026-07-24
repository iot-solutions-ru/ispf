import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  fetchPlatformRuntimeSettings,
  patchPlatformRuntimeSettings,
  type PlatformRuntimeSetting,
  type PlatformRuntimeSettingsSection,
} from "../../api/platformRuntimeSettings";
import { DatabaseSettingsCard } from "./DatabaseSettingsCard";
import { usePersistentTab } from "../../hooks/usePersistentTab";
import { usePublishAdminFocus } from "../../hooks/usePublishAdminFocus";
import type { AdminClientFocus } from "../../context/AdminFocusContext";

const INTEGRATIONS_TAB = "integrations" as const;

const QUICK_BOOLEAN_IDS = [
  "redis.enabled",
  "nats.enabled",
  "ai.enabled",
  "mcp.enabled",
] as const;

const QUICK_SELECT_SETTINGS: Record<string, readonly string[]> = {
  "ai.provider": ["noop", "openai-compatible", "ollama", "custom-url"],
  "tenant.isolation-mode": ["logical", "hard"],
};

const SECTION_SELECT_OPTIONS: Record<string, readonly string[]> = {
  "tenant.isolation-mode": ["logical", "hard"],
};

function findSetting(
  sections: PlatformRuntimeSettingsSection[],
  id: string,
): PlatformRuntimeSetting | undefined {
  for (const section of sections) {
    const match = section.settings.find((setting) => setting.id === id);
    if (match) {
      return match;
    }
  }
  return undefined;
}

function SettingValueInput({
  setting,
  draftValue,
  selectOptions,
  onChange,
}: {
  setting: PlatformRuntimeSetting;
  draftValue: string;
  selectOptions?: readonly string[];
  onChange: (value: string) => void;
}) {
  const { t } = useTranslation(["system", "common"]);

  if (setting.type === "boolean") {
    return (
      <Select
        className="system-settings-input"
        value={draftValue}
        disabled={!setting.editable}
        onChange={onChange}
        options={[
          { value: "true", label: t("common:action.yes") },
          { value: "false", label: t("common:action.no") },
        ]}
      />
    );
  }

  if (selectOptions) {
    return (
      <Select
        className="system-settings-input"
        value={draftValue}
        disabled={!setting.editable}
        onChange={onChange}
        options={selectOptions.map((option) => ({
          value: option,
          label: option === ""
            ? t(`settings.options.${setting.id}.auto`, "Auto")
            : t(`settings.options.${setting.id}.${option}`, option),
        }))}
      />
    );
  }

  return (
    <Input
      className="system-settings-input"
      type={setting.type === "integer" ? "number" : setting.sensitive ? "password" : "text"}
      value={draftValue}
      disabled={!setting.editable}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

function IntegrationQuickToggles({
  sections,
  drafts,
  onDraftChange,
}: {
  sections: PlatformRuntimeSettingsSection[];
  drafts: Record<string, string>;
  onDraftChange: (id: string, value: string) => void;
}) {
  const { t } = useTranslation(["system", "common"]);

  const quickSettings = useMemo(() => {
    const ids = [...QUICK_BOOLEAN_IDS, ...Object.keys(QUICK_SELECT_SETTINGS)];
    return ids
      .map((id) => findSetting(sections, id))
      .filter((setting): setting is PlatformRuntimeSetting => setting != null);
  }, [sections]);

  if (quickSettings.length === 0) {
    return null;
  }

  return (
    <section className="system-metrics-card system-settings-quick">
      <h3>{t("settings.quickToggles.title")}</h3>
      <p className="hint system-settings-quick-hint">{t("settings.quickToggles.hint")}</p>
      <div className="system-settings-quick-grid">
        {quickSettings.map((setting) => {
          const draftValue = drafts[setting.id] ?? setting.value;
          const isBoolean = setting.type === "boolean";
          const selectOptions = QUICK_SELECT_SETTINGS[setting.id];

          return (
            <label
              key={setting.id}
              className={`system-settings-quick-item${setting.editable ? "" : " system-settings-quick-item-locked"}`}
            >
              <span className="system-settings-quick-label">
                {t(`settings.keys.${setting.id}`, setting.id)}
              </span>
              <SettingValueInput
                setting={setting}
                draftValue={draftValue}
                selectOptions={isBoolean ? undefined : selectOptions ?? [draftValue]}
                onChange={(value) => onDraftChange(setting.id, value)}
              />
              <span className="hint system-settings-quick-meta">
                <code>{setting.envVar}</code>
                {setting.overridesEnvironment && setting.environmentValue != null && (
                  <> · {t("settings.envOverrideHint", { value: setting.environmentValue })}</>
                )}
                {setting.restartRequired && <> · {t("settings.quickToggles.restartRequired")}</>}
              </span>
            </label>
          );
        })}
      </div>
    </section>
  );
}

function SettingsSectionCard({
  section,
  drafts,
  onDraftChange,
  sectionHint,
}: {
  section: PlatformRuntimeSettingsSection;
  drafts: Record<string, string>;
  onDraftChange: (id: string, value: string) => void;
  sectionHint?: string;
}) {
  const { t } = useTranslation(["system", "common"]);
  const columns: TableColumnsType<PlatformRuntimeSetting> = [
    {
      title: t("settings.column.setting"),
      key: "setting",
      render: (_, setting) => (
        <>
          <div className="system-settings-label">{t(`settings.keys.${setting.id}`, setting.id)}</div>
          <Typography.Text code className="system-settings-env">{setting.envVar}</Typography.Text>
        </>
      ),
    },
    {
      title: t("settings.column.value"),
      key: "value",
      render: (_, setting) => (
        <SettingValueInput
          setting={setting}
          draftValue={drafts[setting.id] ?? setting.value}
          selectOptions={SECTION_SELECT_OPTIONS[setting.id]}
          onChange={(value) => onDraftChange(setting.id, value)}
        />
      ),
    },
    {
      title: t("settings.column.source"),
      key: "source",
      render: (_, setting) => (
        <Space orientation="vertical">
          <Tag className={`system-settings-source system-settings-source-${setting.source}`}>
            {t(`settings.source.${setting.source}`)}
          </Tag>
          {setting.hotReloadable && <Tag>{t("settings.hotReload")}</Tag>}
          {setting.restartRequired && <Tag>{t("settings.quickToggles.restartRequired")}</Tag>}
          {setting.overridesEnvironment && setting.environmentValue != null && (
            <Typography.Text type="secondary">
              {t("settings.envOverrideHint", { value: setting.environmentValue })}
            </Typography.Text>
          )}
        </Space>
      ),
    },
    {
      title: t("settings.column.default"),
      dataIndex: "defaultValue",
      key: "defaultValue",
      render: (value: string | null | undefined) => <Typography.Text code>{value || t("common:empty.dash")}</Typography.Text>,
    },
  ];

  return (
    <section className="system-metrics-card system-settings-card">
      <Typography.Title level={3}>{t(`settings.sections.${section.id}`, section.title)}</Typography.Title>
      {sectionHint && <Typography.Paragraph type="secondary" className="system-settings-section-hint">{sectionHint}</Typography.Paragraph>}
      {section.id === "elastic" && (
        <Typography.Paragraph type="secondary" className="system-settings-section-hint">
          {t("settings.sections.elasticHint")}
        </Typography.Paragraph>
      )}
      <Table
        className="system-settings-table"
        size="small"
        pagination={false}
        rowKey="id"
        rowClassName={(setting) => setting.editable ? "" : "system-settings-row-locked"}
        columns={columns}
        dataSource={section.settings}
      />
    </section>
  );
}

export default function SystemSettingsView() {
  const { t } = useTranslation(["system", "common"]);
  const queryClient = useQueryClient();
  const settingsQuery = useQuery({
    queryKey: ["platform-runtime-settings"],
    queryFn: fetchPlatformRuntimeSettings,
  });
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [feedback, setFeedback] = useState<string | null>(null);

  const sectionTabs = useMemo(
    () => settingsQuery.data?.sections.map((section) => section.id) ?? [],
    [settingsQuery.data],
  );
  const allTabs = useMemo(() => [INTEGRATIONS_TAB, ...sectionTabs], [sectionTabs]);
  const [activeTab, setActiveTab] = usePersistentTab(
    "system-settings",
    INTEGRATIONS_TAB,
    allTabs,
  );

  const mergedDrafts = useMemo(() => {
    const base: Record<string, string> = {};
    for (const section of settingsQuery.data?.sections ?? []) {
      for (const setting of section.settings) {
        base[setting.id] = drafts[setting.id] ?? setting.value;
      }
    }
    return base;
  }, [drafts, settingsQuery.data]);

  const patchMutation = useMutation({
    mutationFn: patchPlatformRuntimeSettings,
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["platform-runtime-settings"] });
      setDrafts({});
      const parts: string[] = [];
      if (result.appliedLive.length > 0) {
        parts.push(t("settings.feedback.appliedLive", { count: result.appliedLive.length }));
      }
      if (result.restartRequired) {
        parts.push(t("settings.feedback.restartRequired"));
      }
      if (result.skippedEnvLocked.length > 0) {
        parts.push(t("settings.feedback.skippedEnv", { count: result.skippedEnvLocked.length }));
      }
      if (result.errors.length > 0) {
        parts.push(result.errors.join("; "));
      }
      setFeedback(parts.join(" "));
    },
    onError: (error: Error) => setFeedback(error.message),
  });

  const dirtyValues = useMemo(() => {
    const values: Record<string, string> = {};
    for (const section of settingsQuery.data?.sections ?? []) {
      for (const setting of section.settings) {
        if (!setting.editable || setting.sensitive) {
          continue;
        }
        const draft = mergedDrafts[setting.id];
        if (draft != null && draft !== setting.value) {
          values[setting.id] = draft;
        }
      }
    }
    return values;
  }, [mergedDrafts, settingsQuery.data]);

  const activeSection = settingsQuery.data?.sections.find((section) => section.id === activeTab);

  const tabLabel = (tabId: string) =>
    tabId === INTEGRATIONS_TAB
      ? t("settings.tabs.integrations")
      : t(`settings.sections.${tabId}`, tabId);

  const settingsFocus = useMemo((): AdminClientFocus => {
    const sectionIds = settingsQuery.data?.sections.map((section) => section.id) ?? [];
    const visibleSettingIds =
      activeTab === INTEGRATIONS_TAB
        ? [...QUICK_BOOLEAN_IDS, ...Object.keys(QUICK_SELECT_SETTINGS)]
        : (activeSection?.settings ?? []).slice(0, 24).map((setting) => setting.id);
    return {
      surface: "system",
      priority: 70,
      detail: {
        screenTitle: "System › Settings (Система › Настройки)",
        systemTab: "settings",
        settingsTab: activeTab,
        settingsTabLabel: tabLabel(activeTab),
        availableSettingsTabs: allTabs,
        availableSectionIds: sectionIds,
        visibleSettingIds,
        actions: ["refresh", "saveChanges"],
        screenHint:
          activeTab === INTEGRATIONS_TAB
            ? "Integrations quick toggles: Redis, NATS, AI, MCP, AI provider, tenant isolation"
            : `Settings section «${activeTab}» — runtime platform config rows`,
      },
    };
  }, [activeTab, activeSection, allTabs, settingsQuery.data, t]);
  usePublishAdminFocus("system-settings", settingsFocus, Boolean(settingsQuery.data));

  return (
    <div className="system-settings-view">
      <div className="system-embedded-toolbar">
        <Button
          disabled={settingsQuery.isFetching}
          onClick={() => settingsQuery.refetch()}
        >
          {t("settings.refresh")}
        </Button>
        <Button
          type="primary"
          disabled={patchMutation.isPending || Object.keys(dirtyValues).length === 0}
          onClick={() => patchMutation.mutate(dirtyValues)}
        >
          {t("settings.saveChanges")}
        </Button>
      </div>

      <Typography.Paragraph type="secondary" className="system-settings-intro">
        {t("settings.intro")}
      </Typography.Paragraph>
      {settingsQuery.data?.settingsFile && (
        <Typography.Paragraph type="secondary" className="system-settings-file">
          {t("settings.overrideFile")}: <code>{settingsQuery.data.settingsFile}</code>
        </Typography.Paragraph>
      )}

      {feedback && <Alert showIcon message={feedback} />}
      {settingsQuery.error && (
        <Alert type="error" showIcon message={String(settingsQuery.error)} />
      )}
      {settingsQuery.isLoading && <Typography.Text type="secondary">{t("settings.loading")}</Typography.Text>}

      {settingsQuery.data && allTabs.length > 0 && (
        <>
          <div className="tabs-scroll system-settings-tabs-scroll">
            <Space className="system-settings-tabs" aria-label={t("settings.tabsAria")}>
              {allTabs.map((tabId) => (
                <Button
                  key={tabId}
                  type={activeTab === tabId ? "primary" : "default"}
                  onClick={() => setActiveTab(tabId)}
                >
                  {tabLabel(tabId)}
                </Button>
              ))}
            </Space>
          </div>

          {activeTab === INTEGRATIONS_TAB && (
            <IntegrationQuickToggles
              sections={settingsQuery.data.sections}
              drafts={mergedDrafts}
              onDraftChange={(id, value) => setDrafts((prev) => ({ ...prev, [id]: value }))}
            />
          )}

          {activeSection && activeSection.id === "database" && (
            <DatabaseSettingsCard
              section={activeSection}
              drafts={mergedDrafts}
              onDraftChange={(id, value) => setDrafts((prev) => ({ ...prev, [id]: value }))}
            />
          )}

          {activeSection && activeSection.id === "tenant" && (
            <SettingsSectionCard
              section={activeSection}
              drafts={mergedDrafts}
              onDraftChange={(id, value) => setDrafts((prev) => ({ ...prev, [id]: value }))}
              sectionHint={t("settings.sections.tenantHint")}
            />
          )}

          {activeSection && activeSection.id !== "database" && activeSection.id !== "tenant" && (
            <SettingsSectionCard
              section={activeSection}
              drafts={mergedDrafts}
              onDraftChange={(id, value) => setDrafts((prev) => ({ ...prev, [id]: value }))}
            />
          )}
        </>
      )}
    </div>
  );
}
