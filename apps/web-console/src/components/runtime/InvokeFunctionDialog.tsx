import { useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Form, Input, Modal, Space, Typography } from "antd";
import { invokeFunction } from "../../api";
import { proxyFederationFunctionInvoke } from "../../api/federation";
import type { DataRecord, FunctionDescriptor } from "../../types";
import DataRecordValueEditor from "../schema/DataRecordValueEditor";
import { emptyRecord } from "../../utils/ui/record";
import { cloneSchema } from "../../utils/schema/dataSchema";

interface InvokeFunctionDialogProps {
  objectPath: string;
  fn: FunctionDescriptor;
  onClose: () => void;
  onInvoked: () => void;
  federated?: boolean;
  federationPeerId?: string | null;
  federationRemotePath?: string | null;
}

function defaultInputRecord(fn: FunctionDescriptor): DataRecord {
  return emptyRecord(cloneSchema(fn.inputSchema));
}

function functionHasImplementation(fn: FunctionDescriptor): boolean {
  if (fn.sourceType === "java" || fn.sourceType === "script") {
    return Boolean(fn.sourceBody?.trim());
  }
  return Boolean(fn.sourceBody?.trim());
}

function isEmptyInput(input: DataRecord): boolean {
  if (!input.rows || input.rows.length === 0) {
    return true;
  }
  return input.rows.every((row) => Object.keys(row).length === 0);
}

export default function InvokeFunctionDialog({
  objectPath,
  fn,
  onClose,
  onInvoked,
  federated = false,
  federationPeerId,
  federationRemotePath,
}: InvokeFunctionDialogProps) {
  const { t } = useTranslation(["runtime", "common", "inspector"]);
  const [record, setRecord] = useState<DataRecord>(() => defaultInputRecord(fn));
  const [showJson, setShowJson] = useState(false);
  const [inputJson, setInputJson] = useState(() => JSON.stringify(defaultInputRecord(fn), null, 2));
  const [resultJson, setResultJson] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const hasInputFields = useMemo(() => fn.inputSchema.fields.length > 0, [fn.inputSchema.fields.length]);

  const hasImplementation = useMemo(() => functionHasImplementation(fn), [fn]);

  const mutation = useMutation({
    mutationFn: () => {
      let input: DataRecord | undefined;
      if (hasInputFields) {
        try {
          input = showJson ? (JSON.parse(inputJson) as DataRecord) : record;
        } catch {
          throw new Error(t("common:error.invalidJsonInput"));
        }
        if (input && isEmptyInput(input)) {
          input = undefined;
        }
      }
      return federated && federationPeerId && federationRemotePath
        ? proxyFederationFunctionInvoke(
            federationPeerId,
            federationRemotePath,
            fn.name,
            input as Record<string, unknown> | undefined
          ).then((result) => result as unknown as DataRecord)
        : invokeFunction(objectPath, fn.name, input);
    },
    onSuccess: (result) => {
      setResultJson(JSON.stringify(result, null, 2));
      setError(null);
      onInvoked();
    },
    onError: (err: Error) => {
      setError(err.message);
      setResultJson(null);
    },
  });

  return (
    <Modal
      title={t("runtime:invokeFunction.title")}
      open
      onCancel={onClose}
      destroyOnHidden
      width={760}
      footer={[
        <Button key="close" onClick={onClose}>
          {t("common:action.close")}
        </Button>,
        <Button
          key="invoke"
          type="primary"
          disabled={mutation.isPending || !hasImplementation}
          loading={mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? t("runtime:invokeFunction.invoking") : t("runtime:invokeFunction.invoke")}
        </Button>,
      ]}
    >
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          <Typography.Text code>{objectPath}</Typography.Text> → <Typography.Text code>{fn.name}</Typography.Text>
        </Typography.Paragraph>
        {federated && (
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t("runtime:invokeFunction.federatedHint")}
          </Typography.Paragraph>
        )}
        {fn.description && (
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {fn.description}
          </Typography.Paragraph>
        )}
        {hasInputFields ? (
          <Space orientation="vertical" size="small" style={{ width: "100%" }}>
            <Space style={{ justifyContent: "space-between", width: "100%" }}>
              <Typography.Text strong>{t("runtime:invokeFunction.input")}</Typography.Text>
              <Button size="small" onClick={() => setShowJson((v) => !v)}>
                JSON
              </Button>
            </Space>
            {showJson ? (
              <Input.TextArea
                rows={8}
                className="json-editor"
                value={inputJson}
                onChange={(e) => setInputJson(e.target.value)}
                spellCheck={false}
              />
            ) : (
              <DataRecordValueEditor record={record} onChange={setRecord} />
            )}
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t("runtime:invokeFunction.inputHint")}
            </Typography.Paragraph>
          </Space>
        ) : (
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t("descriptor.emptyInputSchema")}
          </Typography.Paragraph>
        )}
        {!hasImplementation && <Alert type="error" showIcon message={t("descriptor.notInvocable")} />}
        {error && <Alert type="error" showIcon message={error} />}
        {resultJson && (
          <Form layout="vertical">
            <Form.Item label={t("runtime:invokeFunction.result")} style={{ marginBottom: 0 }}>
              <Input.TextArea rows={8} readOnly value={resultJson} spellCheck={false} className="json-editor" />
            </Form.Item>
          </Form>
        )}
      </Space>
    </Modal>
  );
}
