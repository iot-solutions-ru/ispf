import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Alert, Button, Input, Modal } from "antd";
import { useTranslation } from "react-i18next";
import { upsertFunction } from "../../api";
import type { FunctionDescriptor } from "../../types";
import { isTechnicalIdentifier } from "../../utils/ui/technicalIdentifier";
import { functionCopyDescriptor, suggestedFunctionCopyName } from "./copyFunctionName";

export default function CopyFunctionDialog({
  objectPath,
  source,
  existingNames,
  onClose,
  onCopied,
}: {
  objectPath: string;
  source: FunctionDescriptor;
  existingNames: string[];
  onClose: () => void;
  onCopied: () => Promise<void>;
}) {
  const { t } = useTranslation("inspector");
  const [name, setName] = useState(() => suggestedFunctionCopyName(source.name));
  const trimmed = name.trim();
  const valid = isTechnicalIdentifier(trimmed, "code");
  const taken = existingNames.some((existing) => existing === trimmed);
  const mutation = useMutation({
    mutationFn: async () => {
      if (!valid || taken) {
        throw new Error(t("functions.copyNameTaken"));
      }
      await upsertFunction(objectPath, functionCopyDescriptor(source, trimmed));
    },
    onSuccess: async () => {
      await onCopied();
    },
  });

  return (
    <Modal
      open
      title={t("functions.copyTitle")}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>
          {t("common:action.cancel")}
        </Button>,
        <Button
          key="copy"
          type="primary"
          disabled={!valid || taken}
          loading={mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          {t("functions.copySubmit")}
        </Button>,
      ]}
    >
      <p className="hint">{t("functions.copyHint", { name: source.name })}</p>
      <label className="field">
        <span>{t("common:table.name")}</span>
        <Input value={name} autoFocus onChange={(event) => setName(event.target.value)} />
      </label>
      {taken && <Alert type="error" showIcon title={t("functions.copyNameTaken")} />}
      {mutation.isError && (
        <Alert
          type="error"
          showIcon
          title={mutation.error instanceof Error ? mutation.error.message : String(mutation.error)}
        />
      )}
    </Modal>
  );
}
