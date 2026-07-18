import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { setVariable, validateExpression } from "../../api";
import { inspectorQueryLoading, useInspectorVariables } from "../../hooks/useInspectorQueries";
import { variableBoolean, variableNumber, variableString } from "../../utils/object/variableFieldValue";
import { cloneRecord, setFieldValue } from "../../utils/ui/record";
import ObjectFederationBindSection from "../federation/ObjectFederationBindSection";

interface ProcessProgramInspectorProps {
  path: string;
  canManage?: boolean;
}

export default function ProcessProgramInspector({ path, canManage = false }: ProcessProgramInspectorProps) {
  const { t } = useTranslation(["automation", "common"]);
  const queryClient = useQueryClient();
  const variablesQuery = useInspectorVariables(path);

  const variables = variablesQuery.data ?? [];
  const form = {
    programId: variableString(variables, "programId"),
    cycleIntervalMs: variableNumber(variables, "cycleIntervalMs", 1000),
    controlExpression: variableString(variables, "controlExpression"),
    enabled: variableBoolean(variables, "enabled", false),
    lastCycleAt: variableString(variables, "lastCycleAt"),
    lastError: variableString(variables, "lastError"),
  };

  const saveMutation = useMutation({
    mutationFn: async (payload: {
      programId: string;
      cycleIntervalMs: number;
      controlExpression: string;
      enabled: boolean;
    }) => {
      const byName = new Map(variables.map((variable) => [variable.name, variable]));
      const writeField = async (name: string, field: string, value: unknown) => {
        const variable = byName.get(name);
        if (!variable?.value) {
          throw new Error(`Variable not loaded: ${name}`);
        }
        await setVariable(path, name, setFieldValue(cloneRecord(variable.value), field, value));
      };
      await writeField("programId", "value", payload.programId);
      await writeField("cycleIntervalMs", "value", payload.cycleIntervalMs);
      await writeField("controlExpression", "value", payload.controlExpression);
      await writeField("enabled", "value", payload.enabled);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const validateMutation = useMutation({
    mutationFn: (expression: string) => validateExpression(expression),
  });

  if (inspectorQueryLoading(variablesQuery)) {
    return <p className="hint">{t("automation:processProgram.loading")}</p>;
  }

  return (
    <section className="automation-inspector">
      <header className="automation-panel-head">
        <div>
          <h3>{t("automation:processProgram.title")}</h3>
          <p className="hint">{t("automation:processProgram.subtitle")}</p>
          <p className="hint">
            <code>{path}</code>
          </p>
        </div>
      </header>
      <form
        key={path}
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          if (!canManage) {
            return;
          }
          const data = new FormData(e.currentTarget);
          saveMutation.mutate({
            programId: String(data.get("programId") ?? ""),
            cycleIntervalMs: Number(data.get("cycleIntervalMs") ?? 1000),
            controlExpression: String(data.get("controlExpression") ?? ""),
            enabled: data.get("enabled") === "on",
          });
        }}
      >
        <label>
          {t("automation:processProgram.programId")}
          <input name="programId" defaultValue={form.programId} required readOnly={!canManage} />
        </label>
        <label>
          {t("automation:processProgram.cycleIntervalMs")}
          <input
            name="cycleIntervalMs"
            type="number"
            min={100}
            step={100}
            defaultValue={form.cycleIntervalMs}
            readOnly={!canManage}
          />
        </label>
        <label className="full">
          {t("automation:processProgram.controlExpression")}
          <textarea
            name="controlExpression"
            defaultValue={form.controlExpression}
            rows={4}
            readOnly={!canManage}
            placeholder={t("automation:processProgram.controlExpressionPlaceholder")}
          />
        </label>
        <label className="checkbox">
          <input type="checkbox" name="enabled" defaultChecked={form.enabled} disabled={!canManage} />
          {t("automation:processProgram.enabled")}
        </label>
        {(form.lastCycleAt || form.lastError) && (
          <div className="full runtime-meta">
            {form.lastCycleAt && (
              <p className="hint">
                {t("automation:processProgram.lastCycleAt")}: <code>{form.lastCycleAt}</code>
              </p>
            )}
            {form.lastError && (
              <p className="hint error">
                {t("automation:processProgram.lastError")}: {form.lastError}
              </p>
            )}
          </div>
        )}
        {canManage && (
          <div className="form-actions full">
            <button
              type="button"
              className="btn"
              onClick={() => {
                const expression = (
                  document.querySelector(
                    'textarea[name="controlExpression"]'
                  ) as HTMLTextAreaElement | null
                )?.value;
                if (expression) {
                  validateMutation.mutate(expression);
                }
              }}
            >
              {t("automation:processProgram.validateCel")}
            </button>
            <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
              {t("common:action.save")}
            </button>
          </div>
        )}
        {validateMutation.data && (
          <p className={`hint full ${validateMutation.data.valid ? "" : "error"}`}>
            {validateMutation.data.valid
              ? t("automation:processProgram.celOk")
              : validateMutation.data.error}
          </p>
        )}
        {saveMutation.error && <p className="hint error full">{String(saveMutation.error)}</p>}
      </form>
      <ObjectFederationBindSection path={path} canManage={canManage} />
    </section>
  );
}
