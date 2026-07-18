import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
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
    <div className="modal-backdrop" role="presentation">
      <div className="modal wide variable-editor-modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{t("variables.newTitle")}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>

        <section className="modal-section form-grid">
          <label className="full">
            {t("common:table.name")}
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              pattern="[A-Za-z_][A-Za-z0-9_]*"
              placeholder="myVariable"
              required
              aria-invalid={Boolean(name) && !nameValid}
            />
            {name && !nameValid && (
              <span className="hint error">{t("common:error.invalidCodeIdentifier")}</span>
            )}
          </label>
          <label className="checkbox-label inline">
            <input
              type="checkbox"
              checked={readable}
              onChange={(e) => setReadable(e.target.checked)}
            />
            {t("variables.readable")}
          </label>
          <label className="checkbox-label inline">
            <input
              type="checkbox"
              checked={writable}
              onChange={(e) => setWritable(e.target.checked)}
            />
            {t("variables.writable")}
          </label>
          <p className="hint full">{t("variables.computedHint")}</p>
        </section>

        <section className="modal-section">
          <h4>{t("variables.schemaSection")}</h4>
          <div className="btn-row variable-schema-presets" role="group" aria-label={t("variables.schemaPresetLabel")}>
            {PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                className={`btn small${schemaPreset === preset ? " primary" : ""}`}
                onClick={() => applyPreset(preset)}
              >
                {t(`variables.schemaPreset.${preset}`)}
              </button>
            ))}
          </div>
          <p className="hint">{t("variables.schemaPresetHint")}</p>
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
            <label className="checkbox-label inline">
              <input
                type="checkbox"
                checked={setInitialValue}
                onChange={(e) => setSetInitialValue(e.target.checked)}
              />
              {t("variables.setInitialValue")}
            </label>
            {setInitialValue && (
              <DataRecordValueEditor record={record} onChange={setRecord} />
            )}
          </section>
        )}

        {mutation.error && (
          <p className="hint error">{(mutation.error as Error).message}</p>
        )}

        <footer>
          <button type="button" className="btn" onClick={onClose}>{t("common:action.cancel")}</button>
          <button
            type="button"
            className="btn primary"
            disabled={!nameValid || schema.fields.length === 0 || mutation.isPending}
            onClick={() => { if (nameValid) mutation.mutate(); }}
          >
            {t("common:action.create")}
          </button>
        </footer>
      </div>
    </div>
  );
}
