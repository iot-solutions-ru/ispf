import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Modal, Segmented, Space, Switch, Typography } from "antd";
import { createVariable, type CreateVariablePayload } from "../../api";
import type { DataRecord, DataSchema } from "../../types";
import DataSchemaEditor from "../schema/DataSchemaEditor";
import DataRecordValueEditor from "../schema/DataRecordValueEditor";
import VariableHistoryFields, { type VariableHistoryState } from "./VariableHistoryFields";
import { scalarValueSchema, syncRecordSchema, type SchemaFieldType } from "../../utils/schema/dataSchema";
import { isTechnicalIdentifier } from "../../utils/ui/technicalIdentifier";

interface CreateVariableDialogProps {
  objectPath: string;
  onClose: () => void;
  onSaved: () => void;
}

type SchemaPreset = "DOUBLE" | "BOOLEAN" | "STRING" | "INTEGER";

const PRESETS: SchemaPreset[] = ["DOUBLE", "BOOLEAN", "STRING", "INTEGER"];

function schemaForPreset(preset: SchemaPreset, schemaName: string): DataSchema {
  return scalarValueSchema(schemaName, preset as SchemaFieldType);
}

export default function CreateVariableDialog({
  objectPath,
  onClose,
  onSaved,
}: CreateVariableDialogProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const [name, setName] = useState("");
  const [readable, setReadable] = useState(true);
  const [writable, setWritable] = useState(false);
  const [schemaPreset, setSchemaPreset] = useState<SchemaPreset>("DOUBLE");
  const [schema, setSchema] = useState<DataSchema>(() => scalarValueSchema("value", "DOUBLE"));
  const [record, setRecord] = useState<DataRecord>(() => ({
    schema: scalarValueSchema("value", "DOUBLE"),
    rows: [],
  }));
  const [history, setHistory] = useState<VariableHistoryState>({
    historyEnabled: false,
    historyRetentionDays: null,
    telemetryPublishMode: "INHERIT",
    historySampleMode: "CHANGES_ONLY",
    includePreviousValueInEvent: false,
    storageMode: "PERSISTENT",
  });
  const [setInitialValue, setSetInitialValue] = useState(false);
  const nameValid = isTechnicalIdentifier(name, "code");

  const schemaName = useMemo(() => name.trim() || "value", [name]);

  function applyPreset(preset: SchemaPreset) {
    setSchemaPreset(preset);
    const next = schemaForPreset(preset, schemaName);
    setSchema(next);
    setRecord((prev) => syncRecordSchema(prev, next));
  }

  function handleSchemaChange(next: DataSchema) {
    const named = { ...next, name: schemaName };
    setSchema(named);
    setRecord((prev) => syncRecordSchema(prev, named));
    const only = named.fields.length === 1 ? named.fields[0] : null;
    if (only?.name === "value" && PRESETS.includes(only.type as SchemaPreset)) {
      setSchemaPreset(only.type as SchemaPreset);
    }
  }

  const mutation = useMutation({
    mutationFn: () => {
      const varName = name.trim();
      const finalSchema: DataSchema = { ...schema, name: varName || schema.name };
      if (finalSchema.fields.length === 0) {
        throw new Error(t("variables.schemaRequired"));
      }
      const payload: CreateVariablePayload = {
        name: varName,
        schema: finalSchema,
        readable,
        writable,
        historyEnabled: history.historyEnabled,
        historyRetentionDays: history.historyRetentionDays,
      };
      if (setInitialValue && writable && record.rows.length > 0) {
        payload.initialValue = { schema: finalSchema, rows: record.rows };
      }
      return createVariable(objectPath, payload);
    },
    onSuccess: onSaved,
  });

  return (
    <Modal
      title={t("variables.newTitle")}
      open
      onCancel={onClose}
      destroyOnHidden
      width={900}
      className="variable-editor-modal"
      footer={[
        <Button key="cancel" onClick={onClose}>{t("common:action.cancel")}</Button>,
        <Button
          key="create"
          type="primary"
          disabled={!nameValid || schema.fields.length === 0 || mutation.isPending}
          loading={mutation.isPending}
          onClick={() => { if (nameValid) mutation.mutate(); }}
        >
          {t("common:action.create")}
        </Button>,
      ]}
    >
      <Space orientation="vertical" size="large" style={{ width: "100%" }}>
        <Form layout="vertical">
          <Form.Item
            label={t("common:table.name")}
            validateStatus={name && !nameValid ? "error" : undefined}
            help={name && !nameValid ? t("common:error.invalidCodeIdentifier") : undefined}
            required
          >
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              pattern="[A-Za-z_][A-Za-z0-9_]*"
              placeholder="myVariable"
              required
              aria-invalid={Boolean(name) && !nameValid}
            />
          </Form.Item>
          <Space size="large" wrap>
            <Space>
              <Switch checked={readable} onChange={setReadable} />
              <Typography.Text>{t("variables.readable")}</Typography.Text>
            </Space>
            <Space>
              <Switch checked={writable} onChange={setWritable} />
              <Typography.Text>{t("variables.writable")}</Typography.Text>
            </Space>
          </Space>
          <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
            {t("variables.computedHint")}
          </Typography.Paragraph>
        </Form>

        <section className="modal-section">
          <Typography.Title level={4}>{t("variables.schemaSection")}</Typography.Title>
          <Segmented<SchemaPreset>
            value={schemaPreset}
            onChange={applyPreset}
            options={PRESETS.map((preset) => ({
              value: preset,
              label: t(`variables.schemaPreset.${preset}`),
            }))}
          />
          <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>
            {t("variables.schemaPresetHint")}
          </Typography.Paragraph>
          <DataSchemaEditor
            value={{ ...schema, name: schemaName }}
            onChange={handleSchemaChange}
            idPrefix="create-var-schema"
          />
        </section>

        <section className="modal-section">
          <VariableHistoryFields
            idPrefix="create-var"
            value={history}
            onChange={setHistory}
          />
        </section>

        {writable && schema.fields.length > 0 && (
          <section className="modal-section">
            <Space>
              <Switch checked={setInitialValue} onChange={setSetInitialValue} />
              <Typography.Text>{t("variables.setInitialValue")}</Typography.Text>
            </Space>
            {setInitialValue && (
              <DataRecordValueEditor record={record} onChange={setRecord} />
            )}
          </section>
        )}

        {mutation.error && (
          <Alert type="error" showIcon message={(mutation.error as Error).message} />
        )}
      </Space>
    </Modal>
  );
}
