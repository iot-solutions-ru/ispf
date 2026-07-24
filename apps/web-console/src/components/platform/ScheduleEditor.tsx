import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Checkbox, Form, Input } from "antd";
import { fetchSchedule, updateSchedule } from "../../api/platformSchedules";
import PlatformSqlEditorShell from "./PlatformSqlEditorShell";

const { TextArea } = Input;

interface ScheduleEditorProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
}

export default function ScheduleEditor({ path, onClose, onOpenProperties }: ScheduleEditorProps) {
  const { t } = useTranslation(["platform", "common"]);
  const queryClient = useQueryClient();
  const scheduleQuery = useQuery({
    queryKey: ["platform-schedule", path],
    queryFn: () => fetchSchedule(path),
  });

  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [intervalMs, setIntervalMs] = useState(60_000);
  const [objectPath, setObjectPath] = useState("");
  const [functionName, setFunctionName] = useState("");
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    if (!scheduleQuery.data) {
      return;
    }
    const data = scheduleQuery.data;
    setDisplayName(data.displayName);
    setDescription(data.description ?? "");
    setEnabled(data.enabled);
    setIntervalMs(data.intervalMs);
    setObjectPath(data.objectPath);
    setFunctionName(data.functionName);
  }, [scheduleQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateSchedule(path, {
        displayName: displayName.trim(),
        description,
        enabled,
        intervalMs,
        objectPath: objectPath.trim(),
        functionName: functionName.trim(),
      }),
    onSuccess: async () => {
      setSaveError(null);
      await queryClient.invalidateQueries({ queryKey: ["platform-schedule", path] });
      await queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
    onError: (error: Error) => setSaveError(error.message),
  });

  if (scheduleQuery.isLoading) {
    return <p className="hint">{t("platform:schedule.loading")}</p>;
  }

  if (scheduleQuery.error) {
    return <Alert type="error" showIcon message={String(scheduleQuery.error)} />;
  }

  const lastTick = scheduleQuery.data?.lastTickAt;
  const lastError = scheduleQuery.data?.lastError;

  return (
    <PlatformSqlEditorShell
      title={displayName || scheduleQuery.data?.scheduleId || t("platform:schedule.title")}
      subtitle={t("platform:schedule.subtitle")}
      path={path}
      onClose={onClose}
      onOpenProperties={onOpenProperties}
      toolbar={
        <Button
          type="primary"
          disabled={saveMutation.isPending || !objectPath.trim() || !functionName.trim()}
          onClick={() => saveMutation.mutate()}
        >
          {saveMutation.isPending ? t("common:action.saving") : t("common:action.save")}
        </Button>
      }
    >
      <Form
        className="report-builder-form"
        layout="vertical"
        onFinish={() => {
          saveMutation.mutate();
        }}
      >
        <label>
          {t("common:field.displayName")}
          <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </label>
        <label>
          {t("platform:schedule.intervalMs")}
          <Input
            type="number"
            min={1000}
            step={1000}
            value={intervalMs}
            onChange={(e) => setIntervalMs(Number(e.target.value) || 60_000)}
            required
          />
        </label>
        <label className="full">
          {t("platform:schedule.objectPath")}
          <Input
            value={objectPath}
            onChange={(e) => setObjectPath(e.target.value)}
            placeholder="root.platform.devices.demo-sensor-01"
            required
          />
        </label>
        <label className="full">
          {t("platform:schedule.functionName")}
          <Input
            value={functionName}
            onChange={(e) => setFunctionName(e.target.value)}
            placeholder="refresh"
            required
          />
        </label>
        <label className="full">
          <Checkbox checked={enabled} onChange={(e) => setEnabled(e.target.checked)}>
            {t("platform:schedule.enabled")}
          </Checkbox>
        </label>
        <label className="full">
          {t("common:field.description")}
          <TextArea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        {lastTick && (
          <p className="hint full">{t("platform:schedule.lastTick", { time: lastTick })}</p>
        )}
        {lastError && (
          <p className="hint error full">{t("platform:schedule.lastError", { error: lastError })}</p>
        )}
        {saveError && <Alert className="full" type="error" showIcon message={saveError} />}
        {saveMutation.isSuccess && <Alert className="full" type="success" showIcon message={t("common:action.saved")} />}
      </Form>
    </PlatformSqlEditorShell>
  );
}
