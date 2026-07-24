import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Alert, Button, Input, Modal, Space, Switch, Tag, Typography } from "antd";
import {
  setVariable,
  updateVariableDefinition,
  updateVariableHistory,
} from "../../api";
import { fetchSecurityRoles } from "../../api/securityRoles";
import type { DataRecord, VariableDto } from "../../types";
import DataRecordValueEditor from "../schema/DataRecordValueEditor";
import VariableHistoryFields, {
  historyStateEqual,
  historyStateFromVariable,
  telemetryModeToApi,
  type VariableHistoryState,
} from "./VariableHistoryFields";
import RoleMultiSelect from "../security/RoleMultiSelect";
import { cloneRecord, recordsEqual } from "../../utils/ui/record";

interface EditVariableDialogProps {
  objectPath: string;
  variable: VariableDto;
  canManageHistory?: boolean;
  canEditDefinition?: boolean;
  onClose: () => void;
  onSaved: () => void;
}

function historyFromVariable(variable: VariableDto): VariableHistoryState {
  return historyStateFromVariable(variable);
}

function historyEqual(a: VariableHistoryState, b: VariableHistoryState): boolean {
  return historyStateEqual(a, b);
}

export default function EditVariableDialog({
  objectPath,
  variable,
  canManageHistory = true,
  canEditDefinition = false,
  onClose,
  onSaved,
}: EditVariableDialogProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const [record, setRecord] = useState<DataRecord>(() =>
    variable.value
      ? cloneRecord(variable.value)
      : { schema: { name: variable.name, fields: [] }, rows: [] }
  );
  const [readable, setReadable] = useState(variable.readable);
  const [writable, setWritable] = useState(variable.writable);
  const [readRoles, setReadRoles] = useState<string[]>(variable.readRoles ?? []);
  const [writeRoles, setWriteRoles] = useState<string[]>(variable.writeRoles ?? []);
  const [history, setHistory] = useState<VariableHistoryState>(() => historyFromVariable(variable));
  const [showJson, setShowJson] = useState(false);
  const [jsonText, setJsonText] = useState("{}");
  const [parseError, setParseError] = useState<string | null>(null);

  const initialHistory = useMemo(() => historyFromVariable(variable), [variable]);
  const initialRecord = useMemo(
    () =>
      variable.value
        ? cloneRecord(variable.value)
        : { schema: { name: variable.name, fields: [] }, rows: [] },
    [variable]
  );

  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
    enabled: canEditDefinition,
  });

  const rolesEqual = (a: string[], b: string[]) =>
    a.length === b.length && [...a].sort().join(",") === [...b].sort().join(",");

  const initialReadRoles = variable.readRoles ?? [];
  const initialWriteRoles = variable.writeRoles ?? [];
  const readRolesDirty = canEditDefinition && !rolesEqual(readRoles, initialReadRoles);
  const writeRolesDirty = canEditDefinition && !rolesEqual(writeRoles, initialWriteRoles);

  const canEditValue = variable.writable;
  const definitionDirty =
    canEditDefinition &&
    (readable !== variable.readable ||
      writable !== variable.writable ||
      readRolesDirty ||
      writeRolesDirty);
  const historyDirty = canManageHistory && !historyEqual(history, initialHistory);
  const jsonDirty = showJson && jsonText !== JSON.stringify(initialRecord, null, 2);
  const valueDirty = canEditValue && (!recordsEqual(record, initialRecord) || jsonDirty);

  useEffect(() => {
    const next = variable.value
      ? cloneRecord(variable.value)
      : { schema: { name: variable.name, fields: [] }, rows: [] };
    setRecord(next);
    setJsonText(JSON.stringify(next, null, 2));
    setReadable(variable.readable);
    setWritable(variable.writable);
    setReadRoles(variable.readRoles ?? []);
    setWriteRoles(variable.writeRoles ?? []);
    setHistory(historyFromVariable(variable));
  }, [variable]);

  useEffect(() => {
    if (!showJson) {
      setJsonText(JSON.stringify(record, null, 2));
    }
  }, [record, showJson]);

  function toggleJsonMode() {
    if (!showJson) {
      setJsonText(JSON.stringify(record, null, 2));
      setParseError(null);
      setShowJson(true);
      return;
    }
    try {
      setRecord(JSON.parse(jsonText) as DataRecord);
      setParseError(null);
      setShowJson(false);
    } catch {
      setParseError(t("common:error.invalidJson"));
    }
  }

  const mutation = useMutation({
    mutationFn: async () => {
      if (canEditDefinition && definitionDirty) {
        await updateVariableDefinition(objectPath, variable.name, {
          readable,
          writable,
          readRoles,
          writeRoles,
        });
      }
      if (canManageHistory && historyDirty) {
        await updateVariableHistory(objectPath, variable.name, {
          historyEnabled: history.historyEnabled,
          historyRetentionDays: history.historyRetentionDays,
          telemetryPublishMode: telemetryModeToApi(history.telemetryPublishMode),
          historySampleMode: history.historySampleMode,
          includePreviousValueInEvent: history.includePreviousValueInEvent,
          storageMode: history.storageMode,
        });
      }
      if (canEditValue && valueDirty) {
        const payload = showJson ? (JSON.parse(jsonText) as DataRecord) : record;
        await setVariable(objectPath, variable.name, payload);
      }
    },
    onSuccess: onSaved,
  });

  function handleSave() {
    const hasChanges = definitionDirty || historyDirty || valueDirty;
    if (!hasChanges) {
      onClose();
      return;
    }
    try {
      if (canEditValue && valueDirty && showJson) {
        JSON.parse(jsonText) as DataRecord;
      }
      setParseError(null);
      mutation.mutate();
    } catch {
      setParseError(t("common:error.invalidJson"));
    }
  }

  const title = canEditValue
    ? t("variables.editTitle", { name: variable.name })
    : t("variables.settingsTitle", { name: variable.name });

  const hasListShape =
    record.schema.fields.some((f) => f.type === "RECORD_LIST") || record.rows.length > 1;

  return (
    <Modal
      title={title}
      open
      onCancel={onClose}
      destroyOnHidden
      width={900}
      className="variable-editor-modal"
      footer={[
        <Button key="cancel" onClick={onClose}>{t("common:action.cancel")}</Button>,
        <Button
          key="save"
          type="primary"
          onClick={handleSave}
          disabled={
            mutation.isPending ||
            (!definitionDirty && !historyDirty && !valueDirty)
          }
          loading={mutation.isPending}
        >
          {t("common:action.save")}
        </Button>,
      ]}
    >
      <Space orientation="vertical" size="large" style={{ width: "100%" }}>
        <section className="modal-section variable-meta-badges">
          <Space size={[4, 4]} wrap>
            {variable.readable && <Tag>R</Tag>}
            {variable.writable && <Tag color="processing">W</Tag>}
            {(variable.readRoles?.length ?? 0) > 0 && (
              <Tag title={variable.readRoles?.join(", ")}>
                ACL-R
              </Tag>
            )}
            {(variable.writeRoles?.length ?? 0) > 0 && (
              <Tag title={variable.writeRoles?.join(", ")}>
                ACL-W
              </Tag>
            )}
            {variable.historyEnabled && <Tag color="success">H</Tag>}
          </Space>
          {!variable.writable && (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t("variables.computedSettingsHint")}
            </Typography.Paragraph>
          )}
        </section>

        {canEditDefinition && (
          <section className="modal-section antd-control-grid">
            <Typography.Title level={4} className="full">{t("variables.definitionSection")}</Typography.Title>
            <Space>
              <Switch checked={readable} onChange={setReadable} />
              <Typography.Text>{t("variables.readable")}</Typography.Text>
            </Space>
            <Space>
              <Switch checked={writable} onChange={setWritable} />
              <Typography.Text>{t("variables.writable")}</Typography.Text>
            </Space>
            <Typography.Paragraph type="secondary" className="full">
              {t("variables.schemaReadOnlyHint")}
            </Typography.Paragraph>
            <RoleMultiSelect
              id={`read-roles-${variable.name}`}
              label={t("variables.readRoles")}
              roles={rolesQuery.data ?? []}
              selected={readRoles}
              onChange={setReadRoles}
            />
            <RoleMultiSelect
              id={`write-roles-${variable.name}`}
              label={t("variables.writeRoles")}
              roles={rolesQuery.data ?? []}
              selected={writeRoles}
              onChange={setWriteRoles}
            />
            <Typography.Paragraph type="secondary" className="full">
              {t("variables.rolesHint")}
            </Typography.Paragraph>
          </section>
        )}

        {canManageHistory && (
          <section className="modal-section">
            <Typography.Title level={4}>{t("objectEditor.historyBtn")}</Typography.Title>
            <VariableHistoryFields
              idPrefix={`edit-${variable.name}`}
              value={history}
              onChange={setHistory}
            />
          </section>
        )}

        {canEditValue && (
          <section className="modal-section">
            <Space style={{ justifyContent: "space-between", width: "100%" }}>
              <Typography.Title level={4} style={{ margin: 0 }}>{t("variables.valuesSection")}</Typography.Title>
              <Button size="small" onClick={toggleJsonMode}>
                JSON
              </Button>
            </Space>
            {showJson ? (
              <Input.TextArea
                className="json-editor"
                rows={14}
                value={jsonText}
                onChange={(e) => setJsonText(e.target.value)}
                spellCheck={false}
              />
            ) : (
              <DataRecordValueEditor
                record={record}
                onChange={setRecord}
                allowMultipleRows={hasListShape}
              />
            )}
          </section>
        )}

        {!canEditValue && !canManageHistory && !canEditDefinition && (
          <Typography.Paragraph type="secondary">{t("variables.noActions")}</Typography.Paragraph>
        )}

        {parseError && <Alert type="error" showIcon message={parseError} />}
        {mutation.error && <Alert type="error" showIcon message={(mutation.error as Error).message} />}
      </Space>
    </Modal>
  );
}
