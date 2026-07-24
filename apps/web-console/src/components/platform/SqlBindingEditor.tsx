import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Checkbox, Form, Input, Select } from "antd";
import { fetchVariables } from "../../api";
import { fetchSqlBinding, refreshSqlBinding, updateSqlBinding } from "../../api/platformSql";
import PlatformSqlEditorShell from "./PlatformSqlEditorShell";
import { useDataSourceOptions } from "./useDataSourceOptions";

const REFRESH_MODES = ["manual", "on_schedule", "on_function_success"] as const;
const { TextArea } = Input;

interface SqlBindingEditorProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
}

export default function SqlBindingEditor({ path, onClose, onOpenProperties }: SqlBindingEditorProps) {
  const { t } = useTranslation(["platform", "common"]);
  const queryClient = useQueryClient();
  const bindingQuery = useQuery({
    queryKey: ["sql-binding", path],
    queryFn: () => fetchSqlBinding(path),
  });
  const dataSourcesQuery = useDataSourceOptions();

  const [targetObjectPath, setTargetObjectPath] = useState("");
  const [variable, setVariable] = useState("value");
  const [dataSourcePath, setDataSourcePath] = useState("");
  const [query, setQuery] = useState("");
  const [valueField, setValueField] = useState("value");
  const [refresh, setRefresh] = useState<string>("manual");
  const [refreshIntervalMs, setRefreshIntervalMs] = useState(30_000);
  const [triggerObjectPath, setTriggerObjectPath] = useState("");
  const [triggerFunctionName, setTriggerFunctionName] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [refreshError, setRefreshError] = useState<string | null>(null);

  useEffect(() => {
    if (!bindingQuery.data) {
      return;
    }
    const data = bindingQuery.data;
    setTargetObjectPath(data.targetObjectPath);
    setVariable(data.variable);
    setDataSourcePath(data.dataSourcePath);
    setQuery(data.query);
    setValueField(data.valueField);
    setRefresh(data.refresh);
    setRefreshIntervalMs(data.refreshIntervalMs);
    setTriggerObjectPath(data.triggerObjectPath);
    setTriggerFunctionName(data.triggerFunctionName);
    setEnabled(data.enabled);
  }, [bindingQuery.data]);

  const targetVarsQuery = useQuery({
    queryKey: ["variables", targetObjectPath],
    queryFn: () => fetchVariables(targetObjectPath),
    enabled: Boolean(targetObjectPath.trim()),
  });

  const targetPreview = targetVarsQuery.data?.find((v) => v.name === variable);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateSqlBinding(path, {
        targetObjectPath: targetObjectPath.trim(),
        variable: variable.trim(),
        dataSourcePath: dataSourcePath.trim(),
        query,
        valueField: valueField.trim() || "value",
        refresh,
        refreshIntervalMs,
        triggerObjectPath: triggerObjectPath.trim(),
        triggerFunctionName: triggerFunctionName.trim(),
        enabled,
      }),
    onSuccess: async () => {
      setSaveError(null);
      await queryClient.invalidateQueries({ queryKey: ["sql-binding", path] });
      if (targetObjectPath.trim()) {
        await queryClient.invalidateQueries({ queryKey: ["variables", targetObjectPath] });
      }
    },
    onError: (error: Error) => setSaveError(error.message),
  });

  const refreshMutation = useMutation({
    mutationFn: () => refreshSqlBinding(path),
    onSuccess: async () => {
      setRefreshError(null);
      await queryClient.invalidateQueries({ queryKey: ["sql-binding", path] });
      if (targetObjectPath.trim()) {
        await queryClient.invalidateQueries({ queryKey: ["variables", targetObjectPath] });
      }
    },
    onError: (error: Error) => setRefreshError(error.message),
  });

  if (bindingQuery.isLoading) {
    return <p className="hint">{t("platform:sqlBinding.loading")}</p>;
  }

  if (bindingQuery.error) {
    return <Alert type="error" showIcon message={String(bindingQuery.error)} />;
  }

  return (
    <PlatformSqlEditorShell
      title={bindingQuery.data?.bindingId ?? t("platform:sqlBinding.title")}
      subtitle={t("platform:sqlBinding.subtitle")}
      path={path}
      onClose={onClose}
      onOpenProperties={onOpenProperties}
      toolbar={
        <>
          <Button
            type="primary"
            disabled={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t("common:action.saving") : t("common:action.save")}
          </Button>
          <Button
            disabled={refreshMutation.isPending || !query.trim() || !dataSourcePath.trim()}
            onClick={() => refreshMutation.mutate()}
          >
            {refreshMutation.isPending ? t("platform:sqlBinding.refreshing") : t("platform:sqlBinding.refreshNow")}
          </Button>
        </>
      }
    >
      <Form
        className="report-builder-form"
        layout="vertical"
        onFinish={() => {
          saveMutation.mutate();
        }}
      >
        <label className="full">
          Target object path *
          <Input
            value={targetObjectPath}
            onChange={(e) => setTargetObjectPath(e.target.value)}
            placeholder="root.platform.devices.demo-sensor-01"
            required
          />
        </label>
        <label>
          Variable *
          <Input value={variable} onChange={(e) => setVariable(e.target.value)} required />
        </label>
        <label>
          Value field
          <Input value={valueField} onChange={(e) => setValueField(e.target.value)} />
        </label>
        <label className="full">
          Data source *
          <Select
            value={dataSourcePath}
            onChange={setDataSourcePath}
            options={[
              { value: "", label: t("platform:sqlBinding.selectPlaceholder") },
              ...(dataSourcesQuery.data ?? []).map((ds) => ({
                value: ds.path,
                label: `${ds.displayName} (${ds.path})`,
              })),
            ]}
          />
        </label>
        <label className="full">
          SQL query (SELECT) *
          <TextArea
            className="mono"
            rows={8}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            spellCheck={false}
            required
          />
        </label>
        <label>
          Refresh mode
          <Select
            value={refresh}
            onChange={setRefresh}
            options={REFRESH_MODES.map((mode) => ({ value: mode, label: mode }))}
          />
        </label>
        <label>
          Refresh interval (ms)
          <Input
            type="number"
            min={1000}
            step={1000}
            value={refreshIntervalMs}
            onChange={(e) => setRefreshIntervalMs(Number(e.target.value) || 30_000)}
          />
        </label>
        <label className="full">
          Trigger object path
          <Input
            value={triggerObjectPath}
            onChange={(e) => setTriggerObjectPath(e.target.value)}
            placeholder={t("platform:sqlBinding.triggerPlaceholder")}
          />
        </label>
        <label className="full">
          Trigger function name
          <Input
            value={triggerFunctionName}
            onChange={(e) => setTriggerFunctionName(e.target.value)}
            placeholder={t("platform:sqlBinding.triggerPlaceholder")}
          />
        </label>
        <label className="full">
          <Checkbox checked={enabled} onChange={(e) => setEnabled(e.target.checked)}>
            Enabled
          </Checkbox>
        </label>
        {bindingQuery.data?.lastRefreshedAt && (
          <p className="hint full">{t("platform:sqlBinding.lastRefreshed", { time: bindingQuery.data.lastRefreshedAt })}</p>
        )}
        {targetPreview?.value?.rows?.[0] && (
          <p className="hint full mono small">
            {t("platform:sqlBinding.currentValue", {
              variable,
              value: JSON.stringify(targetPreview.value.rows[0]),
            })}
          </p>
        )}
        {saveError && <Alert className="full" type="error" showIcon message={saveError} />}
        {refreshError && <Alert className="full" type="error" showIcon message={refreshError} />}
        {saveMutation.isSuccess && <Alert className="full" type="success" showIcon message={t("common:action.saved")} />}
        {refreshMutation.isSuccess && (
          <Alert className="full" type="success" showIcon message={t("platform:sqlBinding.valueUpdated")} />
        )}
      </Form>
    </PlatformSqlEditorShell>
  );
}
