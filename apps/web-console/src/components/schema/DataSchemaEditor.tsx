import { useTranslation } from "react-i18next";
import { Button, Checkbox, Input, Select, Space, Table } from "antd";
import type { TableColumnsType } from "antd";
import type { DataSchema } from "../../types";
import {
  isNestedFieldType,
  newSchemaField,
  SCHEMA_FIELD_TYPES,
  type SchemaField,
} from "../../utils/schema/dataSchema";

interface DataSchemaEditorProps {
  value: DataSchema;
  onChange: (next: DataSchema) => void;
  disabled?: boolean;
  idPrefix?: string;
  showSchemaName?: boolean;
}

function patchField(fields: SchemaField[], index: number, patch: Partial<SchemaField>): SchemaField[] {
  return fields.map((field, i) => (i === index ? { ...field, ...patch } : field));
}

export default function DataSchemaEditor({
  value,
  onChange,
  disabled = false,
  idPrefix = "schema",
  showSchemaName = true,
}: DataSchemaEditorProps) {
  const { t } = useTranslation(["inspector", "common"]);
  type SchemaFieldRow = { field: SchemaField; index: number; key: string };

  function updateField(index: number, patch: Partial<SchemaField>) {
    onChange({ ...value, fields: patchField(value.fields, index, patch) });
  }

  function removeField(index: number) {
    onChange({ ...value, fields: value.fields.filter((_, i) => i !== index) });
  }

  function moveField(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= value.fields.length) {
      return;
    }
    const fields = [...value.fields];
    const [item] = fields.splice(index, 1);
    fields.splice(target, 0, item);
    onChange({ ...value, fields });
  }

  const fieldRows: SchemaFieldRow[] = value.fields.map((field, index) => ({
    field,
    index,
    key: `${idPrefix}-${field.name}-${index}`,
  }));

  const columns: TableColumnsType<SchemaFieldRow> = [
    {
      title: t("common:table.name"),
      dataIndex: ["field", "name"],
      width: "20%",
      render: (_name, { field, index }) => (
        <Input
          className="table-control mono"
          value={field.name}
          disabled={disabled}
          onChange={(e) => updateField(index, { name: e.target.value })}
        />
      ),
    },
    {
      title: t("common:table.type"),
      dataIndex: ["field", "type"],
      width: "22%",
      render: (_type, { field, index }) => (
        <Select
          className="table-control"
          value={field.type}
          disabled={disabled}
          onChange={(type) => {
            const patch: Partial<SchemaField> = { type };
            if (isNestedFieldType(type) && !field.nestedSchema) {
              patch.nestedSchema = { name: `${field.name}Item`, fields: [] };
            }
            if (!isNestedFieldType(type)) {
              patch.nestedSchema = undefined;
            }
            updateField(index, patch);
          }}
          options={SCHEMA_FIELD_TYPES.map((type) => ({ value: type, label: type }))}
        />
      ),
    },
    {
      title: t("common:table.description"),
      dataIndex: ["field", "description"],
      render: (_description, { field, index }) => (
        <Input
          className="table-control"
          value={field.description ?? ""}
          disabled={disabled}
          placeholder={t("schemaEditor.descriptionPlaceholder")}
          onChange={(e) => updateField(index, { description: e.target.value })}
        />
      ),
    },
    {
      title: t("schemaEditor.nullable"),
      dataIndex: ["field", "nullable"],
      width: 96,
      align: "center",
      render: (_nullable, { field, index }) => (
        <Checkbox
          className="table-checkbox"
          checked={field.nullable !== false}
          disabled={disabled}
          onChange={(e) => updateField(index, { nullable: e.target.checked })}
        />
      ),
    },
    {
      title: "",
      key: "actions",
      width: 128,
      align: "right",
      render: (_unused, { index }) =>
        !disabled ? (
          <Space.Compact className="schema-field-actions">
            <Button
              size="small"
              title={t("schemaEditor.moveUp")}
              disabled={index === 0}
              onClick={() => moveField(index, -1)}
            >
              ↑
            </Button>
            <Button
              size="small"
              title={t("schemaEditor.moveDown")}
              disabled={index === value.fields.length - 1}
              onClick={() => moveField(index, 1)}
            >
              ↓
            </Button>
            <Button
              size="small"
              danger
              title={t("common:action.delete")}
              onClick={() => removeField(index)}
            >
              ✕
            </Button>
          </Space.Compact>
        ) : null,
    },
  ];

  return (
    <div className="data-schema-editor">
      {showSchemaName && (
        <label className="field-block full">
          <span className="field-label">{t("schemaEditor.schemaName")}</span>
          <Input
            value={value.name}
            disabled={disabled}
            onChange={(e) => onChange({ ...value, name: e.target.value })}
          />
        </label>
      )}

      {value.fields.length === 0 && (
        <p className="hint">{t("schemaEditor.emptyFields")}</p>
      )}

      {value.fields.length > 0 && (
        <Table<SchemaFieldRow>
          className="data-schema-fields-table"
          columns={columns}
          dataSource={fieldRows}
          pagination={false}
          size="small"
          scroll={{ x: true }}
          expandable={{
            rowExpandable: ({ field }) => isNestedFieldType(field.type) && Boolean(field.nestedSchema),
            expandedRowRender: ({ field, index }) =>
              field.nestedSchema ? (
                <div className="schema-nested-panel">
                  <span className="field-label">{t("schemaEditor.nestedSchema")}</span>
                  <DataSchemaEditor
                    value={field.nestedSchema}
                    onChange={(nestedSchema) => updateField(index, { nestedSchema })}
                    disabled={disabled}
                    idPrefix={`${idPrefix}-nested-${index}`}
                    showSchemaName={false}
                  />
                </div>
              ) : null,
          }}
        />
      )}

      {!disabled && (
        <Button
          size="small"
          onClick={() =>
            onChange({
              ...value,
              fields: [...value.fields, newSchemaField(`field${value.fields.length + 1}`)],
            })
          }
        >
          {t("schemaEditor.addField")}
        </Button>
      )}
    </div>
  );
}
