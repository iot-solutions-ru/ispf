import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button, Input, Modal, Select } from "antd";
import { createDataSourceTable } from "../../api/platformSql";
import type { DataSourceColumnSpec } from "../../api/platformSql";
import { COLUMN_TYPES, isSqlIdent } from "./dataSourceBrowserSql";
import type { ColumnTypeName } from "./dataSourceBrowserSql";

interface DraftColumn {
  key: string;
  name: string;
  type: ColumnTypeName;
  length: number;
  nullable: boolean;
  primaryKey: boolean;
}

interface DataSourceCreateTableDialogProps {
  path: string;
  open: boolean;
  onClose: () => void;
}

let draftSeq = 0;

function blankColumn(): DraftColumn {
  draftSeq += 1;
  return {
    key: `col-${draftSeq}`,
    name: "",
    type: "TEXT",
    length: 255,
    nullable: true,
    primaryKey: false,
  };
}

export default function DataSourceCreateTableDialog({ path, open, onClose }: DataSourceCreateTableDialogProps) {
  const { t } = useTranslation(["platform", "common"]);
  const queryClient = useQueryClient();
  const [table, setTable] = useState("");
  const [columns, setColumns] = useState<DraftColumn[]>([
    { key: "col-id", name: "id", type: "INTEGER", length: 255, nullable: false, primaryKey: true },
  ]);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (payload: { table: string; columns: DataSourceColumnSpec[] }) =>
      createDataSourceTable(path, payload),
    onSuccess: async () => {
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ["data-source-metadata", path] });
      onClose();
    },
    onError: (err: Error) => setError(err.message),
  });

  const patch = (key: string, partial: Partial<DraftColumn>) => {
    setColumns((prev) => prev.map((column) => (column.key === key ? { ...column, ...partial } : column)));
  };

  const submit = () => {
    const tableName = table.trim();
    if (!isSqlIdent(tableName) || columns.length === 0) {
      setError(t("platform:dataSource.invalidIdent"));
      return;
    }
    const specs: DataSourceColumnSpec[] = [];
    for (const column of columns) {
      const name = column.name.trim();
      if (!isSqlIdent(name)) {
        setError(t("platform:dataSource.invalidIdent"));
        return;
      }
      specs.push({
        name,
        type: column.type,
        length: column.type === "VARCHAR" ? column.length : undefined,
        nullable: column.primaryKey ? false : column.nullable,
        primaryKey: column.primaryKey,
      });
    }
    mutation.mutate({ table: tableName, columns: specs });
  };

  return (
    <Modal
      title={t("platform:dataSource.createTable")}
      open={open}
      onCancel={onClose}
      destroyOnHidden
      width={720}
      footer={[
        <Button key="cancel" onClick={onClose}>
          {t("common:action.cancel")}
        </Button>,
        <Button key="create" type="primary" loading={mutation.isPending} onClick={submit}>
          {t("platform:dataSource.createTable")}
        </Button>,
      ]}
    >
      <label className="ds-field">
        <span className="ds-field-label">{t("platform:dataSource.createTableName")}</span>
        <Input value={table} onChange={(event) => setTable(event.target.value)} spellCheck={false} />
      </label>
      <div className="data-source-create-columns">
        {columns.map((column) => (
          <div key={column.key} className="data-source-create-column">
            <Input
              value={column.name}
              placeholder={t("platform:dataSource.columnName")}
              aria-label={t("platform:dataSource.columnName")}
              onChange={(event) => patch(column.key, { name: event.target.value })}
            />
            <Select
              value={column.type}
              aria-label={t("platform:dataSource.columnType")}
              options={COLUMN_TYPES.map((type) => ({ value: type, label: type }))}
              onChange={(type: ColumnTypeName) => patch(column.key, { type })}
            />
            {column.type === "VARCHAR" && (
              <Input
                type="number"
                min={1}
                max={4000}
                value={column.length}
                aria-label={t("platform:dataSource.columnLength")}
                onChange={(event) => patch(column.key, { length: Number(event.target.value) })}
              />
            )}
            <label className="data-source-create-flag">
              <input
                type="checkbox"
                checked={column.nullable && !column.primaryKey}
                disabled={column.primaryKey}
                onChange={(event) => patch(column.key, { nullable: event.target.checked })}
              />
              {t("platform:dataSource.columnNullable")}
            </label>
            <label className="data-source-create-flag">
              <input
                type="checkbox"
                checked={column.primaryKey}
                onChange={(event) =>
                  patch(column.key, { primaryKey: event.target.checked, nullable: event.target.checked ? false : column.nullable })
                }
              />
              {t("platform:dataSource.columnPrimaryKey")}
            </label>
            <Button
              disabled={columns.length === 1}
              onClick={() => setColumns((prev) => prev.filter((item) => item.key !== column.key))}
            >
              {t("platform:dataSource.removeColumn")}
            </Button>
          </div>
        ))}
      </div>
      <Button onClick={() => setColumns((prev) => [...prev, blankColumn()])}>{t("platform:dataSource.addColumn")}</Button>
      {error && <p className="data-source-create-error">{error}</p>}
    </Modal>
  );
}
