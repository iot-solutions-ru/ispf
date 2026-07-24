import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Form, Input, InputNumber, Switch, Typography } from "antd";
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
  const [antdForm] = Form.useForm();

  const variables = variablesQuery.data ?? [];
  const form = {
    programId: variableString(variables, "programId"),
    cycleIntervalMs: variableNumber(variables, "cycleIntervalMs", 1000),
    controlExpression: variableString(variables, "controlExpression"),
    targetObjectPath: variableString(variables, "targetObjectPath"),
    outputVariable: variableString(variables, "outputVariable"),
    interlockExpression: variableString(variables, "interlockExpression"),
    enabled: variableBoolean(variables, "enabled", false),
    lastCycleAt: variableString(variables, "lastCycleAt"),
    lastOutput: variableString(variables, "lastOutput"),
    lastError: variableString(variables, "lastError"),
  };

  const saveMutation = useMutation({
    mutationFn: async (payload: {
      programId: string;
      cycleIntervalMs: number;
      controlExpression: string;
      targetObjectPath: string;
      outputVariable: string;
      interlockExpression: string;
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
      await writeField("targetObjectPath", "value", payload.targetObjectPath);
      await writeField("outputVariable", "value", payload.outputVariable);
      await writeField("interlockExpression", "value", payload.interlockExpression);
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
      <Form
        form={antdForm}
        key={path}
        layout="vertical"
        className="antd-control-grid"
        initialValues={form}
        disabled={!canManage}
        onFinish={(values: typeof form) => {
          if (!canManage) {
            return;
          }
          saveMutation.mutate({
            programId: String(values.programId ?? ""),
            cycleIntervalMs: Number(values.cycleIntervalMs ?? 1000),
            controlExpression: String(values.controlExpression ?? ""),
            targetObjectPath: String(values.targetObjectPath ?? ""),
            outputVariable: String(values.outputVariable ?? ""),
            interlockExpression: String(values.interlockExpression ?? ""),
            enabled: Boolean(values.enabled),
          });
        }}
      >
        <Form.Item name="programId" label={t("automation:processProgram.programId")} rules={[{ required: true }]} required>
          <Input />
        </Form.Item>
        <Form.Item name="cycleIntervalMs" label={t("automation:processProgram.cycleIntervalMs")}>
          <InputNumber min={100} step={100} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item className="full" name="targetObjectPath" label={t("automation:processProgram.targetObjectPath")}>
          <Input placeholder={t("automation:processProgram.targetObjectPathPlaceholder")} />
        </Form.Item>
        <Form.Item name="outputVariable" label={t("automation:processProgram.outputVariable")}>
          <Input placeholder={t("automation:processProgram.outputVariablePlaceholder")} />
        </Form.Item>
        <Form.Item className="full" name="controlExpression" label={t("automation:processProgram.controlExpression")}>
          <Input.TextArea rows={4} placeholder={t("automation:processProgram.controlExpressionPlaceholder")} />
        </Form.Item>
        <Form.Item className="full" name="interlockExpression" label={t("automation:processProgram.interlockExpression")}>
          <Input.TextArea rows={2} placeholder={t("automation:processProgram.interlockExpressionPlaceholder")} />
        </Form.Item>
        <Form.Item name="enabled" valuePropName="checked" label={t("automation:processProgram.enabled")}>
          <Switch />
        </Form.Item>
        {(form.lastCycleAt || form.lastOutput || form.lastError) && (
          <div className="full runtime-meta">
            {form.lastCycleAt && (
              <Typography.Paragraph type="secondary">
                {t("automation:processProgram.lastCycleAt")}: <code>{form.lastCycleAt}</code>
              </Typography.Paragraph>
            )}
            {form.lastOutput && (
              <Typography.Paragraph type="secondary">
                {t("automation:processProgram.lastOutput")}: <code>{form.lastOutput}</code>
              </Typography.Paragraph>
            )}
            {form.lastError && (
              <Alert type="error" showIcon message={`${t("automation:processProgram.lastError")}: ${form.lastError}`} />
            )}
          </div>
        )}
        {canManage && (
          <div className="form-actions full">
            <Button
              onClick={() => {
                const expression = antdForm.getFieldValue("controlExpression") as string | undefined;
                if (expression) {
                  validateMutation.mutate(expression);
                }
              }}
            >
              {t("automation:processProgram.validateCel")}
            </Button>
            <Button htmlType="submit" type="primary" loading={saveMutation.isPending}>
              {t("common:action.save")}
            </Button>
          </div>
        )}
        {validateMutation.data && (
          <p className={`hint full ${validateMutation.data.valid ? "" : "error"}`}>
            {validateMutation.data.valid
              ? t("automation:processProgram.celOk")
              : validateMutation.data.error}
          </p>
        )}
        {saveMutation.error && <Alert className="full" type="error" message={String(saveMutation.error)} showIcon />}
      </Form>
      <ObjectFederationBindSection path={path} canManage={canManage} />
    </section>
  );
}
