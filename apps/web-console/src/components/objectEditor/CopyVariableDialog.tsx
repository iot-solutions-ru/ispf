import { useMemo, useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Form, Input, Modal } from "antd";
import { createVariable, updateVariableHistory } from "../../api";
import type { VariableDto } from "../../types";
import { isTechnicalIdentifier } from "../../utils/ui/technicalIdentifier";
import { historyStateFromVariable, telemetryModeToApi } from "./variableHistoryModel";
import { schemaForVariableCopy, suggestedVariableCopyName, valueForVariableCopy } from "./copyVariableName";

interface CopyVariableDialogProps {
  objectPath: string;
  variable: VariableDto;
  onClose: () => void;
  onSaved: () => void;
}

export default function CopyVariableDialog({
  objectPath,
  variable,
  onClose,
  onSaved,
}: CopyVariableDialogProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const [name, setName] = useState(() => suggestedVariableCopyName(variable.name));
  const nameValid = isTechnicalIdentifier(name, "code");

  const mutation = useMutation({
    mutationFn: async () => {
      const varName = name.trim();
      const schema = schemaForVariableCopy(variable, varName);
      const history = historyStateFromVariable(variable);
      await createVariable(objectPath, {
        name: varName,
        schema,
        readable: variable.readable,
        writable: variable.writable,
        initialValue: valueForVariableCopy(variable, schema),
        historyEnabled: history.historyEnabled,
        historyRetentionDays: history.historyRetentionDays,
        readRoles: variable.readRoles ?? [],
        writeRoles: variable.writeRoles ?? [],
      });
      await updateVariableHistory(objectPath, varName, {
        historyEnabled: history.historyEnabled,
        historyRetentionDays: history.historyRetentionDays,
        telemetryPublishMode: telemetryModeToApi(history.telemetryPublishMode),
        historySampleMode: history.historySampleMode,
        includePreviousValueInEvent: history.includePreviousValueInEvent,
        storageMode: history.storageMode,
      });
    },
    onSuccess: onSaved,
  });

  const copyRef = useRef<() => void>(() => {});
  copyRef.current = () => {
    if (nameValid) mutation.mutate();
  };
  const footer = useMemo(
    () => [
      <Button key="cancel" onClick={onClose}>{t("common:action.cancel")}</Button>,
      <Button
        key="copy"
        type="primary"
        disabled={!nameValid || mutation.isPending}
        loading={mutation.isPending}
        onClick={() => copyRef.current()}
      >
        {t("variables.copySubmit")}
      </Button>,
    ],
    [nameValid, mutation.isPending, onClose, t],
  );

  return (
    <Modal
      title={t("variables.copyTitle")}
      open
      onCancel={onClose}
      destroyOnHidden
      footer={footer}
    >
      <p className="hint">{t("variables.copyHint", { name: variable.name })}</p>
      {mutation.error && (
        <Alert type="error" showIcon title={(mutation.error as Error).message} style={{ marginBottom: 12 }} />
      )}
      <Form layout="vertical">
        <Form.Item
          label={t("common:table.name")}
          validateStatus={name && !nameValid ? "error" : undefined}
          help={name && !nameValid ? t("common:error.invalidCodeIdentifier") : undefined}
          required
        >
          <Input
            value={name}
            onChange={(event) => setName(event.target.value)}
            pattern="[A-Za-z_][A-Za-z0-9_]*"
            autoComplete="off"
            aria-invalid={Boolean(name) && !nameValid}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
