import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchVariables } from "../api";
import { pollDriver, writeDriverPoint } from "../api/drivers";
import { parseDriverPointMappings, parseDriverWriteValue } from "../utils/driverPointMappings";
import { variableString } from "../utils/variableFieldValue";

interface DriverWriteFormProps {
  devicePath: string;
  canManage: boolean;
  supportsWrite?: boolean;
  showPoll?: boolean;
  onSuccess?: () => void;
}

export default function DriverWriteForm({
  devicePath,
  canManage,
  supportsWrite = true,
  showPoll = true,
  onSuccess,
}: DriverWriteFormProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const [pointId, setPointId] = useState("");
  const [valueText, setValueText] = useState("");
  const [showJson, setShowJson] = useState(false);
  const [valueJson, setValueJson] = useState('{\n  "rows": [{ "value": 0 }]\n}');
  const [formError, setFormError] = useState<string | null>(null);

  const variablesQuery = useQuery({
    queryKey: ["variables", devicePath],
    queryFn: () => fetchVariables(devicePath),
  });

  const mappings = useMemo(
    () => parseDriverPointMappings(variableString(variablesQuery.data ?? [], "driverPointMappingsJson")),
    [variablesQuery.data],
  );

  const mappingEntries = useMemo(
    () => Object.entries(mappings).sort(([a], [b]) => a.localeCompare(b)),
    [mappings],
  );

  useEffect(() => {
    if (!pointId && mappingEntries.length > 0) {
      setPointId(mappingEntries[0][0]);
    }
  }, [mappingEntries, pointId]);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["driver-status", devicePath] });
    queryClient.invalidateQueries({ queryKey: ["variables", devicePath] });
    onSuccess?.();
  };

  const pollMutation = useMutation({
    mutationFn: () => pollDriver(devicePath),
    onSuccess: invalidate,
  });

  const writeMutation = useMutation({
    mutationFn: () => {
      setFormError(null);
      if (!pointId.trim()) {
        throw new Error(t("inspector:driver.write.pointRequired"));
      }
      let payload: { rows: Array<Record<string, unknown>> };
      if (showJson) {
        try {
          const parsed = JSON.parse(valueJson) as { rows?: Array<Record<string, unknown>> };
          if (!parsed.rows || parsed.rows.length === 0) {
            throw new Error(t("inspector:driver.write.valueRequired"));
          }
          payload = { rows: parsed.rows };
        } catch (error) {
          if (error instanceof Error && error.message === t("inspector:driver.write.valueRequired")) {
            throw error;
          }
          throw new Error(t("common:error.invalidJsonInput"));
        }
      } else {
        if (!valueText.trim()) {
          throw new Error(t("inspector:driver.write.valueRequired"));
        }
        payload = { rows: [parseDriverWriteValue(valueText)] };
      }
      return writeDriverPoint(devicePath, pointId.trim(), payload);
    },
    onSuccess: invalidate,
    onError: (error: Error) => setFormError(error.message),
  });

  const selectedMapping = mappings[pointId];
  const isBusy = pollMutation.isPending || writeMutation.isPending;
  const actionError =
    pollMutation.error
    ?? (writeMutation.error && !formError ? writeMutation.error : null);

  if (variablesQuery.isLoading) {
    return <p className="hint">{t("inspector:driver.write.loading")}</p>;
  }

  if (!canManage) {
    return <p className="hint">{t("common:hint.adminOnly")}</p>;
  }

  if (!supportsWrite) {
    return <p className="hint warning driver-hint-box">{t("inspector:driver.write.readOnlyDriver")}</p>;
  }

  return (
    <section className="driver-write-form">
      <header className="driver-write-head">
        <div>
          <h4>{t("inspector:driver.write.title")}</h4>
          <p className="hint">{t("inspector:driver.write.subtitle")}</p>
        </div>
        {showPoll && (
          <button
            type="button"
            className="btn"
            disabled={isBusy}
            onClick={() => pollMutation.mutate()}
          >
            {pollMutation.isPending ? t("inspector:driver.write.polling") : t("inspector:driver.write.pollNow")}
          </button>
        )}
      </header>

      {mappingEntries.length === 0 ? (
        <p className="hint warning driver-hint-box">{t("inspector:driver.write.noMappings")}</p>
      ) : (
        <form
          className="driver-write-fields"
          onSubmit={(event) => {
            event.preventDefault();
            writeMutation.mutate();
          }}
        >
          <div className="form-grid">
            <label>
              {t("inspector:driver.write.pointLabel")}
              <select value={pointId} onChange={(event) => setPointId(event.target.value)}>
                {mappingEntries.map(([variableName]) => (
                  <option key={variableName} value={variableName}>
                    {variableName}
                  </option>
                ))}
              </select>
            </label>
            {selectedMapping && (
              <p className="hint full">
                {t("inspector:driver.write.mappingHint", { mapping: selectedMapping })}
              </p>
            )}
            <div className="full section-inline-tools">
              <span className="field-label">{t("inspector:driver.write.valueLabel")}</span>
              <button type="button" className="btn tiny" onClick={() => setShowJson((value) => !value)}>
                JSON
              </button>
            </div>
            {showJson ? (
              <label className="full">
                {t("inspector:driver.write.valueJsonLabel")}
                <textarea
                  className="mono"
                  rows={6}
                  value={valueJson}
                  onChange={(event) => setValueJson(event.target.value)}
                  spellCheck={false}
                />
              </label>
            ) : (
              <label className="full">
                {t("inspector:driver.write.valueLabel")}
                <input
                  type="text"
                  value={valueText}
                  onChange={(event) => setValueText(event.target.value)}
                  placeholder={t("inspector:driver.write.valuePlaceholder")}
                />
              </label>
            )}
          </div>
          <div className="form-actions">
            <button type="submit" className="btn primary" disabled={isBusy}>
              {writeMutation.isPending ? t("inspector:driver.write.writing") : t("inspector:driver.write.submit")}
            </button>
          </div>
          {formError && <p className="hint error">{formError}</p>}
        </form>
      )}

      {actionError && (
        <p className="hint error">{(actionError as Error).message}</p>
      )}
    </section>
  );
}
