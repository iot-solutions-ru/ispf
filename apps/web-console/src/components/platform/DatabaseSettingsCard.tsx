import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { Button, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { fetchPlatformInfo } from "../../api";
import type { PlatformRuntimeSetting, PlatformRuntimeSettingsSection } from "../../api/platformRuntimeSettings";

const PRIMARY_DB_PREFIX = "database.";
const METADATA_KIND_ID = "metadata.db.kind";
const DATABASE_URL_ID = "database.url";
const EVENT_JOURNAL_STORE_ID = "event-journal.store";
const VARIABLE_HISTORY_STORE_ID = "variable-history.store";

const PRIMARY_SETTING_ORDER = [
  METADATA_KIND_ID,
  DATABASE_URL_ID,
  "database.user",
  "database.password",
  "database.pool-size",
  "database.pool-min-idle",
] as const;

const METADATA_KIND_ALL = ["", "postgresql", "h2", "mssql", "mysql", "oracle"] as const;
/** Prod: certified PG + enterprise POC targets (ADR-0037). H2 is edge/dev only. */
const METADATA_KIND_PROD = ["", "postgresql", "mssql", "mysql", "oracle"] as const;

const EVENT_JOURNAL_CLICKHOUSE_IDS = [
  "event-journal.clickhouse.url",
  "event-journal.clickhouse.database",
  "event-journal.clickhouse.table",
  "event-journal.clickhouse.username",
  "event-journal.clickhouse.password",
] as const;

const EVENT_JOURNAL_CASSANDRA_IDS = [
  "event-journal.cassandra.contact-points",
  "event-journal.cassandra.port",
  "event-journal.cassandra.local-datacenter",
  "event-journal.cassandra.keyspace",
  "event-journal.cassandra.table",
  "event-journal.cassandra.username",
  "event-journal.cassandra.password",
] as const;

const VARIABLE_HISTORY_CLICKHOUSE_IDS = [
  "variable-history.clickhouse.url",
  "variable-history.clickhouse.database",
  "variable-history.clickhouse.table",
  "variable-history.clickhouse.username",
  "variable-history.clickhouse.password",
] as const;

const VARIABLE_HISTORY_CASSANDRA_IDS = [
  "variable-history.cassandra.contact-points",
  "variable-history.cassandra.port",
  "variable-history.cassandra.local-datacenter",
  "variable-history.cassandra.keyspace",
  "variable-history.cassandra.table",
  "variable-history.cassandra.username",
  "variable-history.cassandra.password",
] as const;

const STORE_SELECT_OPTIONS: Record<string, readonly string[]> = {
  [METADATA_KIND_ID]: METADATA_KIND_ALL,
  [EVENT_JOURNAL_STORE_ID]: ["jdbc", "clickhouse", "cassandra", "scylla"],
  [VARIABLE_HISTORY_STORE_ID]: ["jdbc", "jpa", "clickhouse", "cassandra", "scylla"],
};

function metadataKindOptions(environment: string | undefined): readonly string[] {
  return environment === "prod" ? METADATA_KIND_PROD : METADATA_KIND_ALL;
}

function sortPrimarySettings(settings: PlatformRuntimeSetting[]): PlatformRuntimeSetting[] {
  const order = new Map(PRIMARY_SETTING_ORDER.map((id, index) => [id, index]));
  return [...settings].sort((a, b) => {
    const ai = order.get(a.id as (typeof PRIMARY_SETTING_ORDER)[number]) ?? 999;
    const bi = order.get(b.id as (typeof PRIMARY_SETTING_ORDER)[number]) ?? 999;
    return ai - bi;
  });
}

function isEnterpriseMetadataKind(kind: string): boolean {
  return kind === "mssql" || kind === "mysql" || kind === "oracle";
}

function isEdgeMetadataKind(kind: string): boolean {
  return kind === "h2";
}

function settingsById(section: PlatformRuntimeSettingsSection): Map<string, PlatformRuntimeSetting> {
  return new Map(section.settings.map((setting) => [setting.id, setting]));
}

function isJdbcStore(store: string): boolean {
  return store === "jdbc" || store === "jpa";
}

function isClickHouseStore(store: string): boolean {
  return store === "clickhouse";
}

function isCassandraStore(store: string): boolean {
  return store === "cassandra" || store === "scylla";
}

function pickSettings(
  byId: Map<string, PlatformRuntimeSetting>,
  ids: readonly string[],
): PlatformRuntimeSetting[] {
  return ids
    .map((id) => byId.get(id))
    .filter((setting): setting is PlatformRuntimeSetting => setting != null);
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

function StorageSubsection({
  title,
  hint,
  notice,
  settings,
  drafts,
  onDraftChange,
  selectOptionsById = STORE_SELECT_OPTIONS,
}: {
  title: string;
  hint?: string;
  notice?: string;
  settings: PlatformRuntimeSetting[];
  drafts: Record<string, string>;
  onDraftChange: (id: string, value: string) => void;
  selectOptionsById?: Record<string, readonly string[]>;
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
          selectOptions={selectOptionsById[setting.id]}
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
      render: (value: string | null | undefined) => (
        <Typography.Text code>{value || t("common:empty.dash")}</Typography.Text>
      ),
    },
  ];

  if (settings.length === 0) {
    return null;
  }

  return (
    <div className="system-settings-database-subsection">
      <Typography.Title level={4} className="system-settings-database-subtitle">{title}</Typography.Title>
      {hint && <Typography.Paragraph type="secondary" className="system-settings-section-hint">{hint}</Typography.Paragraph>}
      {notice && <Typography.Paragraph type="secondary" className="system-settings-database-notice">{notice}</Typography.Paragraph>}
      <Table
        className="system-settings-table"
        size="small"
        pagination={false}
        rowKey="id"
        rowClassName={(setting) => setting.editable ? "" : "system-settings-row-locked"}
        columns={columns}
        dataSource={settings}
      />
    </div>
  );
}

export function DatabaseSettingsCard({
  section,
  drafts,
  onDraftChange,
}: {
  section: PlatformRuntimeSettingsSection;
  drafts: Record<string, string>;
  onDraftChange: (id: string, value: string) => void;
}) {
  const { t } = useTranslation("system");
  const infoQuery = useQuery({
    queryKey: ["platform-info"],
    queryFn: fetchPlatformInfo,
    staleTime: 60_000,
  });

  const byId = useMemo(() => settingsById(section), [section]);

  const selectOptionsById = useMemo(
    () => ({
      ...STORE_SELECT_OPTIONS,
      [METADATA_KIND_ID]: metadataKindOptions(infoQuery.data?.environment),
    }),
    [infoQuery.data?.environment],
  );

  const primarySettings = useMemo(
    () => sortPrimarySettings(
      section.settings.filter((setting) =>
        setting.id.startsWith(PRIMARY_DB_PREFIX) || setting.id === METADATA_KIND_ID),
    ),
    [section.settings],
  );

  const metadataKind =
    drafts[METADATA_KIND_ID] ?? byId.get(METADATA_KIND_ID)?.value ?? "";

  const primaryHint =
    infoQuery.data?.environment === "prod"
      ? t("settings.database.metadataKindProdHint")
      : t("settings.database.metadataKindHint");
  const primaryNotice = useMemo(() => {
    if (!metadataKind || metadataKind === "postgresql") {
      return undefined;
    }
    if (isEnterpriseMetadataKind(metadataKind)) {
      return t("settings.database.metadataKindEnterpriseHint");
    }
    if (isEdgeMetadataKind(metadataKind)) {
      return t("settings.database.metadataKindEdgeHint");
    }
    return t("settings.database.metadataKindChangeHint");
  }, [metadataKind, t]);

  const eventStore = drafts[EVENT_JOURNAL_STORE_ID] ?? byId.get(EVENT_JOURNAL_STORE_ID)?.value ?? "jdbc";
  const variableStore =
    drafts[VARIABLE_HISTORY_STORE_ID] ?? byId.get(VARIABLE_HISTORY_STORE_ID)?.value ?? "jdbc";

  const eventJournalSettings = useMemo(() => {
    const storeSetting = byId.get(EVENT_JOURNAL_STORE_ID);
    if (!storeSetting) {
      return [];
    }
    const connectionSettings = isClickHouseStore(eventStore)
      ? pickSettings(byId, EVENT_JOURNAL_CLICKHOUSE_IDS)
      : isCassandraStore(eventStore)
        ? pickSettings(byId, EVENT_JOURNAL_CASSANDRA_IDS)
        : [];
    return [storeSetting, ...connectionSettings];
  }, [byId, eventStore]);

  const variableHistorySettings = useMemo(() => {
    const storeSetting = byId.get(VARIABLE_HISTORY_STORE_ID);
    if (!storeSetting) {
      return [];
    }
    const connectionSettings = isClickHouseStore(variableStore)
      ? pickSettings(byId, VARIABLE_HISTORY_CLICKHOUSE_IDS)
      : isCassandraStore(variableStore)
        ? pickSettings(byId, VARIABLE_HISTORY_CASSANDRA_IDS)
        : [];
    return [storeSetting, ...connectionSettings];
  }, [byId, variableStore]);

  const eventJournalHint = isJdbcStore(eventStore) ? t("settings.database.usesPrimaryPostgres") : undefined;
  const variableHistoryHint = isJdbcStore(variableStore) ? t("settings.database.usesPrimaryPostgres") : undefined;

  const applySingleDbPreset = () => {
    onDraftChange(EVENT_JOURNAL_STORE_ID, "jdbc");
    onDraftChange(VARIABLE_HISTORY_STORE_ID, "jdbc");
  };

  const singleDbActive =
    (drafts[EVENT_JOURNAL_STORE_ID] ?? byId.get(EVENT_JOURNAL_STORE_ID)?.value ?? "jdbc") === "jdbc"
    && (drafts[VARIABLE_HISTORY_STORE_ID] ?? byId.get(VARIABLE_HISTORY_STORE_ID)?.value ?? "jdbc") === "jdbc";

  return (
    <section className="system-metrics-card system-settings-card system-settings-database-card">
      <Typography.Title level={3}>{t(`settings.sections.${section.id}`, section.title)}</Typography.Title>
      <Typography.Paragraph type="secondary" className="system-settings-section-hint">
        {t("settings.sections.databaseHint")}
      </Typography.Paragraph>
      <Space className="system-settings-database-actions">
        <Button
          type={singleDbActive ? "primary" : "default"}
          onClick={applySingleDbPreset}
        >
          {t("settings.database.singleDbPreset")}
        </Button>
        <Typography.Text type="secondary">{t("settings.database.singleDbPresetHint")}</Typography.Text>
      </Space>

      <StorageSubsection
        title={t("settings.database.subsections.primary")}
        hint={primaryHint}
        notice={primaryNotice}
        settings={primarySettings}
        drafts={drafts}
        onDraftChange={onDraftChange}
        selectOptionsById={selectOptionsById}
      />
      <Typography.Paragraph type="secondary" className="system-settings-database-notice">
        {t("settings.database.externalDataSourcesHint")}
      </Typography.Paragraph>

      <StorageSubsection
        title={t("settings.database.subsections.eventJournal")}
        hint={eventJournalHint}
        settings={eventJournalSettings}
        drafts={drafts}
        onDraftChange={onDraftChange}
        selectOptionsById={selectOptionsById}
      />

      <StorageSubsection
        title={t("settings.database.subsections.variableHistory")}
        hint={variableHistoryHint}
        settings={variableHistorySettings}
        drafts={drafts}
        onDraftChange={onDraftChange}
        selectOptionsById={selectOptionsById}
      />
    </section>
  );
}
