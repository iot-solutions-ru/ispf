import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchSchedule, updateSchedule } from "../../api/platformSchedules";
import PlatformSqlEditorShell from "./PlatformSqlEditorShell";

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
    return <div className="op-alert op-alert-error">{String(scheduleQuery.error)}</div>;
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
        <button
          type="button"
          className="btn primary"
          disabled={saveMutation.isPending || !objectPath.trim() || !functionName.trim()}
          onClick={() => saveMutation.mutate()}
        >
          {saveMutation.isPending ? t("common:action.saving") : t("common:action.save")}
        </button>
      }
    >
      <form
        className="form-grid report-builder-form"
        onSubmit={(e) => {
          e.preventDefault();
          saveMutation.mutate();
        }}
      >
        <label>
          {t("common:field.displayName")}
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </label>
        <label>
          {t("platform:schedule.intervalMs")}
          <input
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
          <input
            value={objectPath}
            onChange={(e) => setObjectPath(e.target.value)}
            placeholder="root.platform.devices.demo-sensor-01"
            required
          />
        </label>
        <label className="full">
          {t("platform:schedule.functionName")}
          <input
            value={functionName}
            onChange={(e) => setFunctionName(e.target.value)}
            placeholder="refresh"
            required
          />
        </label>
        <label className="full">
          <span className="checkbox-row">
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
            />
            {t("platform:schedule.enabled")}
          </span>
        </label>
        <label className="full">
          {t("common:field.description")}
          <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        {lastTick && (
          <p className="hint full">{t("platform:schedule.lastTick", { time: lastTick })}</p>
        )}
        {lastError && (
          <p className="hint error full">{t("platform:schedule.lastError", { error: lastError })}</p>
        )}
        {saveError && <p className="hint error full">{saveError}</p>}
        {saveMutation.isSuccess && <p className="hint full">{t("common:action.saved")}</p>}
      </form>
    </PlatformSqlEditorShell>
  );
}
