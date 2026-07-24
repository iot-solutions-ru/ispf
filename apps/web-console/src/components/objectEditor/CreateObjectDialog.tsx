import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Typography } from "antd";
import { createObject, createEventFilter, upsertFunction } from "../../api";
import { buildObjectQueryRunFunction } from "../../utils/object/objectQueryDefaults";
import { fetchInstanceTypes, instantiateBlueprint } from "../../api/blueprints";
import { registerApplication } from "../../api/applications";
import { saveReportDefinition } from "../../api/reports";
import { createOperatorApp } from "../../api/operatorApps";
import { installVirtClusterPreset } from "../../api/platformPresets";
import {
  applyDashboardLayoutTemplate,
  fetchDashboardLayoutTemplates,
} from "../../api/dashboardsCore";
import { useDataSourceOptions } from "../platform/useDataSourceOptions";
import {
  createDataSource,
  createMigration,
  createSqlBinding,
} from "../../api/platformSql";
import DataSourceConnectionFields, {
  isDataSourceConnectionValid,
  type DataSourceConnectionMode,
} from "../platform/DataSourceConnectionFields";
import { createSchedule } from "../../api/platformSchedules";
import { fetchDrivers } from "../../api/drivers";
import { formatDriverConfigJson } from "../../utils/driverDefaults";
import DriverMaturityBadge, { formatDriverOptionLabel } from "../DriverMaturityBadge";
import {
  applicationObjectPath,
  defaultObjectTypeForParent,
  instanceTypeFilterForParent,
  operatorAppObjectPath,
  resolveCreateDialogMode,
} from "../../utils/object/createObjectMode";
import { DATA_SOURCES_ROOT } from "../../utils/platform/platformSqlPath";
import { ObjectPathField } from "../../ui/index";
import type { BlueprintDto } from "../../types/blueprints";
import type { ObjectType } from "../../types";
import { isTechnicalIdentifier } from "../../utils/ui/technicalIdentifier";

const INSTANCE_TYPE_PREFIX = "instance:";

const OBJECT_TYPES: ObjectType[] = [
  "CUSTOM",
  "VISUAL_GROUP",
  "DEVICE",
  "BLUEPRINT",
  "DASHBOARD",
  "REPORT",
  "WORKFLOW",
  "ALERT",
  "AGENT",
  "USER",
  "TENANT",
  "DRIVER",
];

const DEFAULT_POLL_INTERVAL_MS = 5000;

interface CreateObjectDialogProps {
  parentPath: string;
  presetType?: ObjectType;
  onClose: () => void;
  onCreated: (path: string) => void;
}

export default function CreateObjectDialog({
  parentPath,
  presetType,
  onClose,
  onCreated,
}: CreateObjectDialogProps) {
  const { t } = useTranslation(["explorer", "common", "platform"]);
  const mode = resolveCreateDialogMode(parentPath);
  const isMimicCatalog = parentPath.endsWith(".mimics");
  const [name, setName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [type, setType] = useState<ObjectType>(() => defaultObjectTypeForParent(parentPath));
  const [typeSelection, setTypeSelection] = useState<string>(() => defaultObjectTypeForParent(parentPath));

  useEffect(() => {
    const defaultType = presetType ?? defaultObjectTypeForParent(parentPath);
    setType(defaultType);
    setTypeSelection(defaultType);
  }, [parentPath, presetType]);
  const [driverId, setDriverId] = useState("virtual");
  const [applyVirtualLab, setApplyVirtualLab] = useState(true);
  const [pollIntervalMs, setPollIntervalMs] = useState(DEFAULT_POLL_INTERVAL_MS);
  const [configPreview, setConfigPreview] = useState("{}");
  const [reportDataSourcePath, setReportDataSourcePath] = useState("root.platform.data-sources.demo");
  const [schemaName, setSchemaName] = useState("");
  const [dataSourceConnectionMode, setDataSourceConnectionMode] = useState<DataSourceConnectionMode>("internal");
  const [dataSourceJdbcUrl, setDataSourceJdbcUrl] = useState("");
  const [dataSourceJdbcDriverClass, setDataSourceJdbcDriverClass] = useState("");
  const [dataSourceJdbcUsername, setDataSourceJdbcUsername] = useState("");
  const [dataSourceJdbcPassword, setDataSourceJdbcPassword] = useState("");
  const [dataSourcePoolSize, setDataSourcePoolSize] = useState(5);
  const [scriptId, setScriptId] = useState("");
  const [migrationVersion, setMigrationVersion] = useState("1.0.0");
  const [migrationDataSourcePath, setMigrationDataSourcePath] = useState("");
  const [migrationSql, setMigrationSql] = useState("");
  const [bindingTargetPath, setBindingTargetPath] = useState("");
  const [bindingVariable, setBindingVariable] = useState("value");
  const [bindingDataSourcePath, setBindingDataSourcePath] = useState("");
  const [bindingQuery, setBindingQuery] = useState("SELECT 1 AS cnt");
  const [scheduleIntervalMs, setScheduleIntervalMs] = useState(60_000);
  const [scheduleObjectPath, setScheduleObjectPath] = useState("root.platform.devices.demo-sensor-01");
  const [scheduleFunctionName, setScheduleFunctionName] = useState("");
  const [dashboardLayoutTemplate, setDashboardLayoutTemplate] = useState("");
  const nameValid = isTechnicalIdentifier(name, "pathSegment");

  const dataSourcesQuery = useDataSourceOptions();
  const dashboardTemplatesQuery = useQuery({
    queryKey: ["dashboard-layout-templates"],
    queryFn: fetchDashboardLayoutTemplates,
    staleTime: 60_000,
    enabled: mode === "object" && type === "DASHBOARD",
  });

  const dialogTitle = useMemo(() => {
    switch (mode) {
      case "application":
        return t("dialog.newDeployApp");
      case "operator-app":
        return t("dialog.newOperatorApp");
      case "alert-rule":
        return t("dialog.newAlertRule");
      case "correlator":
        return t("dialog.newCorrelator");
      case "report":
        return t("dialog.newReport");
      case "data-source":
        return t("dialog.newDataSource");
      case "migration":
        return t("dialog.newMigration");
      case "sql-binding":
        return t("dialog.newSqlBinding");
      case "schedule":
        return t("dialog.newSchedule");
      case "query":
        return t("dialog.newQuery");
      case "event-filter":
        return t("dialog.newEventFilter");
      case "process-program":
        return t("dialog.newProcessProgram");
      default:
        return presetType === "VISUAL_GROUP"
          ? t("dialog.newVisualGroup")
          : isMimicCatalog
            ? t("dialog.newMimic")
            : t("dialog.newObject");
    }
  }, [isMimicCatalog, mode, presetType, t]);

  const driversQuery = useQuery({
    queryKey: ["drivers"],
    queryFn: fetchDrivers,
    enabled: mode === "object",
  });

  const instanceTypeFilter = useMemo(
    () => instanceTypeFilterForParent(parentPath),
    [parentPath],
  );

  const instanceTypesQuery = useQuery({
    queryKey: ["instance-types", parentPath, instanceTypeFilter],
    queryFn: () => fetchInstanceTypes(instanceTypeFilter, parentPath),
    enabled: mode === "object",
  });

  useEffect(() => {
    if (mode !== "object" || !instanceTypeFilter || instanceTypesQuery.isLoading) {
      return;
    }
    const models = instanceTypesQuery.data ?? [];
    if (models.length !== 1) {
      return;
    }
    const only = models[0];
    const selection = `${INSTANCE_TYPE_PREFIX}${only.id}`;
    setTypeSelection(selection);
    if (only.targetObjectType) {
      setType(only.targetObjectType);
    }
  }, [mode, instanceTypeFilter, instanceTypesQuery.data, instanceTypesQuery.isLoading]);

  const selectedInstanceModel = useMemo(() => {
    if (!typeSelection.startsWith(INSTANCE_TYPE_PREFIX)) {
      return null;
    }
    const modelId = typeSelection.slice(INSTANCE_TYPE_PREFIX.length);
    return instanceTypesQuery.data?.find((model) => model.id === modelId) ?? null;
  }, [typeSelection, instanceTypesQuery.data]);

  const selectedDriver = useMemo(
    () => driversQuery.data?.find((driver) => driver.id === driverId),
    [driversQuery.data, driverId]
  );

  useEffect(() => {
    if (type !== "DEVICE") {
      return;
    }
    setConfigPreview(formatDriverConfigJson(selectedDriver));
  }, [type, selectedDriver]);

  const mutation = useMutation({
    mutationFn: async () => {
      if (mode === "application") {
        await registerApplication({
          appId: name,
          displayName: displayName || name,
          schemaName: schemaName.trim() || undefined,
        });
        return applicationObjectPath(name);
      }
      if (mode === "operator-app") {
        await createOperatorApp(name, displayName || name);
        return operatorAppObjectPath(name);
      }
      if (mode === "alert-rule") {
        const obj = await createObject({
          parentPath,
          name,
          type: "ALERT",
          displayName: displayName || name,
          description,
          templateId: "alert-rule-v1",
        });
        return obj.path;
      }
      if (mode === "correlator") {
        const obj = await createObject({
          parentPath,
          name,
          type: "CORRELATOR",
          displayName: displayName || name,
          description,
          templateId: "correlator-v1",
        });
        return obj.path;
      }
      if (mode === "report") {
        const obj = await createObject({
          parentPath,
          name,
          type: "REPORT",
          displayName: displayName || name,
          description,
          templateId: "report-v1",
        });
        const dataSourcePath = reportDataSourcePath.trim();
        if (dataSourcePath) {
          await saveReportDefinition(obj.path, {
            title: displayName || name,
            dataSourcePath,
            query: "SELECT 1 AS value",
            parameters: [],
            columns: [{ field: "value", label: "Value" }],
          });
        }
        return obj.path;
      }
      if (mode === "data-source") {
        const created = await createDataSource({
          name,
          displayName: displayName || name,
          connectionMode: dataSourceConnectionMode,
          schemaName: schemaName.trim(),
          jdbcUrl: dataSourceJdbcUrl.trim(),
          jdbcDriverClass: dataSourceJdbcDriverClass.trim(),
          jdbcUsername: dataSourceJdbcUsername.trim(),
          jdbcPassword: dataSourceJdbcPassword.trim() || undefined,
          poolSize: dataSourcePoolSize,
          description,
        });
        return created.path;
      }
      if (mode === "migration") {
        const created = await createMigration({
          scriptId: scriptId.trim() || name,
          version: migrationVersion.trim() || "1.0.0",
          dataSourcePath: migrationDataSourcePath.trim(),
          sql: migrationSql,
        });
        return created.path;
      }
      if (mode === "sql-binding") {
        const created = await createSqlBinding({
          bindingId: name,
          targetObjectPath: bindingTargetPath.trim(),
          variable: bindingVariable.trim() || "value",
          dataSourcePath: bindingDataSourcePath.trim(),
          query: bindingQuery,
          refresh: "manual",
          enabled: true,
        });
        return created.path;
      }
      if (mode === "schedule") {
        const created = await createSchedule({
          scheduleId: name,
          displayName: displayName || name,
          description,
          enabled: true,
          intervalMs: scheduleIntervalMs,
          objectPath: scheduleObjectPath.trim(),
          functionName: scheduleFunctionName.trim(),
        });
        return created.path;
      }
      if (mode === "query") {
        const obj = await createObject({
          parentPath,
          name,
          type: "CUSTOM",
          displayName: displayName || name,
          description,
        });
        await upsertFunction(obj.path, buildObjectQueryRunFunction());
        return obj.path;
      }
      if (mode === "event-filter") {
        const created = await createEventFilter({
          filterId: name,
          displayName: displayName || name,
          description,
          eventNamePattern: "*",
          sourceObjectPathPattern: "root.platform.**",
          minSeverity: 0,
          maxSeverity: 100,
          timeWindowMs: 0,
          filterExpression: "",
          enabled: true,
        });
        return created.path;
      }
      if (mode === "process-program") {
        const obj = await createObject({
          parentPath,
          name,
          type: "PROCESS_PROGRAM",
          displayName: displayName || name,
          description,
        });
        return obj.path;
      }
      if (selectedInstanceModel) {
        const obj = await instantiateBlueprint(selectedInstanceModel.id, parentPath, name, {});
        return obj.path;
      }
      const obj = await createObject({
        parentPath,
        name,
        type,
        displayName: displayName || name,
        description,
        templateId:
          type === "DASHBOARD"
            ? "dashboard-v1"
            : type === "MIMIC"
              ? "mimic-v1"
              : type === "REPORT"
                ? "report-v1"
                : type === "WORKFLOW"
                  ? "workflow-v1"
                  : type === "DEVICE" && driverId === "virtual" && applyVirtualLab
                    ? "virtual-lab-v1"
                    : undefined,
        driverId: type === "DEVICE" ? driverId : undefined,
        driverPollIntervalMs: type === "DEVICE" ? pollIntervalMs : undefined,
        autoStartDriver: true,
      });
      if (type === "DASHBOARD" && dashboardLayoutTemplate.trim()) {
        await applyDashboardLayoutTemplate(obj.path, dashboardLayoutTemplate.trim());
      }
      return obj.path;
    },
    onSuccess: (path) => onCreated(path),
  });

  const virtClusterMutation = useMutation({
    mutationFn: () => installVirtClusterPreset({ wireOperatorApp: true, operatorAppId: "platform" }),
    onSuccess: (result) => onCreated(result.overviewDashboard || result.folder),
  });

  const handleDriverChange = (nextDriverId: string) => {
    setDriverId(nextDriverId);
    const driver = driversQuery.data?.find((item) => item.id === nextDriverId);
    setConfigPreview(formatDriverConfigJson(driver));
    if (nextDriverId !== "virtual") {
      setApplyVirtualLab(false);
    } else {
      setApplyVirtualLab(true);
    }
  };

  const showVirtClusterPreset = mode === "object" && parentPath === "root.platform.devices";

  return (
    <Modal
      title={dialogTitle}
      open
      onCancel={onClose}
      destroyOnHidden
      width={820}
      className="modal-create-object"
      footer={null}
    >
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t("dialog.parent")} <Typography.Text code>{parentPath}</Typography.Text>
          </Typography.Paragraph>
          {showVirtClusterPreset && (
            <Space orientation="vertical" size="small" style={{ width: "100%" }}>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {t("dialog.virtClusterHint")}
              </Typography.Paragraph>
              <Button
                disabled={virtClusterMutation.isPending || mutation.isPending}
                loading={virtClusterMutation.isPending}
                onClick={() => virtClusterMutation.mutate()}
              >
                {virtClusterMutation.isPending
                  ? t("dialog.virtClusterInstalling")
                  : t("dialog.virtClusterInstall")}
              </Button>
              {virtClusterMutation.error && (
                <Alert type="error" showIcon message={(virtClusterMutation.error as Error).message} />
              )}
            </Space>
          )}
          {mode === "application" && (
            <>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t("dialog.deployAppHint")}</Typography.Paragraph>
              <Form layout="vertical">
                <Form.Item label="PostgreSQL schema" style={{ marginBottom: 0 }}>
                <Input
                  value={schemaName}
                  onChange={(e) => setSchemaName(e.target.value)}
                  placeholder={`app_${name || "myapp"}`}
                />
                </Form.Item>
              </Form>
            </>
          )}
          {mode === "operator-app" && (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t("dialog.operatorAppHint")}</Typography.Paragraph>
          )}
          {(mode === "alert-rule" || mode === "correlator") && (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t("dialog.alertCorrelatorHint")}</Typography.Paragraph>
          )}
          {mode === "report" && (
            <>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t("dialog.reportHint", { path: parentPath })}</Typography.Paragraph>
              <Form layout="vertical">
                <Form.Item label="Data source *" required style={{ marginBottom: 0 }}>
                <Select
                  value={reportDataSourcePath}
                  onChange={setReportDataSourcePath}
                  options={[
                    { value: "", label: t("platform:sqlBinding.selectPlaceholder") },
                    ...(dataSourcesQuery.data ?? []).map((source) => ({
                      value: source.path,
                      label: `${source.displayName} (${source.path})`,
                    })),
                  ]}
                />
                </Form.Item>
              </Form>
            </>
          )}
          {mode === "object" && isMimicCatalog && (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t("dialog.mimicHint", { path: parentPath })}</Typography.Paragraph>
          )}
          {mode === "data-source" && (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t("dialog.dataSourceHint", { path: parentPath })}</Typography.Paragraph>
          )}
          {mode === "migration" && (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t("dialog.migrationHint", { path: parentPath })}</Typography.Paragraph>
          )}
          {mode === "sql-binding" && (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t("dialog.sqlBindingHint")}</Typography.Paragraph>
          )}
          {mode === "schedule" && (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t("dialog.scheduleHint")}</Typography.Paragraph>
          )}
          {(mode === "query" || mode === "event-filter" || mode === "process-program") && (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>{t(`dialog.${mode}Hint`, { path: parentPath })}</Typography.Paragraph>
          )}
          <Form
            layout="vertical"
            className="antd-control-grid"
            onFinish={() => {
              if (!nameValid) return;
              mutation.mutate();
            }}
          >
            <Form.Item
              label={mode === "object" ? t("dialog.namePathSegment") : t("dialog.nameOrId")}
              validateStatus={name && !nameValid ? "error" : undefined}
              help={name && !nameValid ? t("common:error.invalidPathSegment") : undefined}
              required
            >
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                pattern="[a-zA-Z0-9_-]+"
                aria-invalid={Boolean(name) && !nameValid}
              />
            </Form.Item>
            <Form.Item label={t("common:field.displayName")}>
              <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            </Form.Item>

            {mode === "data-source" && (
              <DataSourceConnectionFields
                connectionMode={dataSourceConnectionMode}
                schemaName={schemaName}
                jdbcUrl={dataSourceJdbcUrl}
                jdbcDriverClass={dataSourceJdbcDriverClass}
                jdbcUsername={dataSourceJdbcUsername}
                jdbcPassword={dataSourceJdbcPassword}
                poolSize={dataSourcePoolSize}
                onConnectionModeChange={setDataSourceConnectionMode}
                onSchemaNameChange={setSchemaName}
                onJdbcUrlChange={setDataSourceJdbcUrl}
                onJdbcDriverClassChange={setDataSourceJdbcDriverClass}
                onJdbcUsernameChange={setDataSourceJdbcUsername}
                onJdbcPasswordChange={setDataSourceJdbcPassword}
                onPoolSizeChange={setDataSourcePoolSize}
              />
            )}

            {mode === "migration" && (
              <>
                <Form.Item label="Script ID">
                  <Input
                    value={scriptId}
                    onChange={(e) => setScriptId(e.target.value)}
                    placeholder={t("dialog.scriptIdPlaceholder")}
                  />
                </Form.Item>
                <Form.Item label="Version">
                  <Input value={migrationVersion} onChange={(e) => setMigrationVersion(e.target.value)} />
                </Form.Item>
                <Form.Item label="Data source path" className="full">
                  <Input
                    value={migrationDataSourcePath}
                    onChange={(e) => setMigrationDataSourcePath(e.target.value)}
                    placeholder="root.platform.data-sources.myapp"
                  />
                </Form.Item>
                <Form.Item label={t("dialog.sqlOptional")} className="full">
                  <Input.TextArea
                    className="mono"
                    rows={4}
                    value={migrationSql}
                    onChange={(e) => setMigrationSql(e.target.value)}
                    spellCheck={false}
                  />
                </Form.Item>
              </>
            )}

            {mode === "sql-binding" && (
              <>
                <ObjectPathField
                  className="full"
                  label={t("dialog.sqlBindingTargetPath")}
                  pickerTitle={t("dialog.sqlBindingPickTarget")}
                  value={bindingTargetPath}
                  onChange={setBindingTargetPath}
                  placeholder="root.platform.devices.demo-sensor-01"
                />
                <Form.Item label={t("dialog.sqlBindingVariable")}>
                  <Input value={bindingVariable} onChange={(e) => setBindingVariable(e.target.value)} />
                </Form.Item>
                <ObjectPathField
                  className="full"
                  label={t("dialog.sqlBindingDataSourcePath")}
                  pickerTitle={t("dialog.sqlBindingPickDataSource")}
                  value={bindingDataSourcePath}
                  onChange={setBindingDataSourcePath}
                  filterTypes={["DATA_SOURCE"]}
                  rootPath={DATA_SOURCES_ROOT}
                  placeholder="root.platform.data-sources.myapp"
                />
                <Form.Item label={t("dialog.sqlBindingQuery")} className="full">
                  <Input.TextArea
                    className="mono"
                    rows={3}
                    value={bindingQuery}
                    onChange={(e) => setBindingQuery(e.target.value)}
                    spellCheck={false}
                  />
                </Form.Item>
              </>
            )}

            {mode === "schedule" && (
              <>
                <Form.Item label={t("platform:schedule.intervalMs")}>
                  <InputNumber
                    min={1000}
                    step={1000}
                    value={scheduleIntervalMs}
                    onChange={(value) => setScheduleIntervalMs(Number(value) || 60_000)}
                    required
                    style={{ width: "100%" }}
                  />
                </Form.Item>
                <Form.Item label={t("platform:schedule.objectPath")} className="full">
                  <Input
                    value={scheduleObjectPath}
                    onChange={(e) => setScheduleObjectPath(e.target.value)}
                    placeholder="root.platform.devices.demo-sensor-01"
                    required
                  />
                </Form.Item>
                <Form.Item label={t("platform:schedule.functionName")} className="full">
                  <Input
                    value={scheduleFunctionName}
                    onChange={(e) => setScheduleFunctionName(e.target.value)}
                    required
                  />
                </Form.Item>
              </>
            )}

            {mode === "object" && !isMimicCatalog && (
              <Form.Item label={t("dialog.type")}>
                <Select
                  value={typeSelection}
                  onChange={(next) => {
                    setTypeSelection(next);
                    if (!next.startsWith(INSTANCE_TYPE_PREFIX)) {
                      setType(next as ObjectType);
                    } else {
                      const model = instanceTypesQuery.data?.find(
                        (item) => item.id === next.slice(INSTANCE_TYPE_PREFIX.length)
                      );
                      if (model?.targetObjectType) {
                        setType(model.targetObjectType);
                      }
                    }
                  }}
                  options={[
                    {
                      label: "Platform types",
                      options: OBJECT_TYPES.map((objectType) => ({
                        value: objectType,
                        label: objectType === "VISUAL_GROUP" ? t("dialog.typeVisualGroup") : objectType,
                      })),
                    },
                    ...(instanceTypesQuery.data?.length
                      ? [{
                          label: t("dialog.instanceTypes"),
                          options: (instanceTypesQuery.data ?? []).map((model: BlueprintDto) => ({
                            value: `${INSTANCE_TYPE_PREFIX}${model.id}`,
                            label: `${model.name}${model.targetObjectType ? ` (${model.targetObjectType})` : ""}`,
                          })),
                        }]
                      : []),
                  ]}
                />
              </Form.Item>
            )}

            {mode === "object" && type === "DASHBOARD" && (
              <Form.Item
                label={t("dialog.dashboardLayoutTemplate")}
                className="full"
                extra={t("dialog.dashboardLayoutTemplateHint")}
              >
                <Select
                  value={dashboardLayoutTemplate}
                  onChange={setDashboardLayoutTemplate}
                  disabled={dashboardTemplatesQuery.isLoading}
                  options={[
                    { value: "", label: t("dialog.dashboardLayoutTemplateNone") },
                    ...(dashboardTemplatesQuery.data ?? []).map((template) => ({
                      value: template,
                      label: template,
                    })),
                  ]}
                />
              </Form.Item>
            )}

            {mode === "object" && type === "DEVICE" && !selectedInstanceModel && (
              <>
                <Form.Item label={t("dialog.driver")}>
                  <Space.Compact style={{ width: "100%" }}>
                    <Select
                      value={driverId}
                      onChange={handleDriverChange}
                      disabled={driversQuery.isLoading}
                      options={(driversQuery.data ?? []).map((driver) => ({
                        value: driver.id,
                        label: formatDriverOptionLabel(driver.id, driver.name, driver.maturity),
                      }))}
                    />
                    {selectedDriver && <DriverMaturityBadge maturity={selectedDriver.maturity} />}
                  </Space.Compact>
                </Form.Item>
                <Form.Item label={t("dialog.pollIntervalMs")}>
                  <InputNumber
                    min={500}
                    step={500}
                    value={pollIntervalMs}
                    onChange={(value) =>
                      setPollIntervalMs(Number(value) || DEFAULT_POLL_INTERVAL_MS)
                    }
                    style={{ width: "100%" }}
                  />
                </Form.Item>
                {driverId === "virtual" && (
                  <Space className="full">
                    <Switch
                      checked={applyVirtualLab}
                      onChange={setApplyVirtualLab}
                    />
                    <Typography.Text>{t("dialog.applyVirtualLab")}</Typography.Text>
                  </Space>
                )}
                {selectedDriver?.description && (
                  <Typography.Paragraph type="secondary" className="full">{selectedDriver.description}</Typography.Paragraph>
                )}
                <Form.Item label={t("dialog.defaultDriverConfig")} className="full">
                  <Input.TextArea
                    className="mono readonly"
                    rows={4}
                    value={configPreview}
                    readOnly
                    spellCheck={false}
                  />
                </Form.Item>
                <Typography.Paragraph type="secondary" className="full">
                  {driverId === "virtual" && applyVirtualLab
                    ? t("dialog.virtualLabHint")
                    : t("dialog.deviceDriverHint")}
                </Typography.Paragraph>
              </>
            )}

            {(mode === "object" || mode === "data-source" || mode === "migration" || mode === "sql-binding"
              || mode === "query" || mode === "event-filter" || mode === "process-program") && (
              <Form.Item label={t("common:field.description")} className="full">
                <Input.TextArea
                  rows={2}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </Form.Item>
            )}
            {mutation.error && (
              <Alert className="full" type="error" showIcon message={(mutation.error as Error).message} />
            )}
            <Space className="full">
              <Button onClick={onClose}>
                {t("common:action.cancel")}
              </Button>
              <Button
                htmlType="submit"
                type="primary"
                disabled={
                  mutation.isPending
                  || !nameValid
                  || (mode === "data-source"
                    && !isDataSourceConnectionValid({
                      connectionMode: dataSourceConnectionMode,
                      schemaName,
                      jdbcUrl: dataSourceJdbcUrl,
                    }))
                  || (mode === "sql-binding"
                    && (!bindingTargetPath.trim() || !bindingDataSourcePath.trim()))
                }
                loading={mutation.isPending}
              >
                {t("common:action.create")}
              </Button>
            </Space>
          </Form>
      </Space>
    </Modal>
  );
}
