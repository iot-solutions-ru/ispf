import { useMemo, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Input, Tabs, Tree } from "antd";
import type { TreeDataNode } from "antd";
import {
  executeDataSourceQuery,
  fetchDataSourceCatalog,
  fetchDataSourceColumns,
} from "../../api/platformSql";
import type { DataSourceCatalog, DataSourceColumn, DataSourceQueryResult } from "../../api/platformSql";
import BffDataTable from "../operator/BffDataTable";
import DataSourceCreateTableDialog from "./DataSourceCreateTableDialog";
import { downloadCsv, parseTableNodeKey, previewSelect, tableNodeKey } from "./dataSourceBrowserSql";

const { TextArea } = Input;

const TABLE_PREVIEW_MAX_ROWS = 200;

interface SqlTab {
  id: string;
  title: string;
  sql: string;
  result: DataSourceQueryResult | null;
  error: string | null;
}

interface DataSourceBrowserProps {
  path: string;
  writesEnabled: boolean;
}

let tabSeq = 0;

function nextTabId(): string {
  tabSeq += 1;
  return `sql-${tabSeq}`;
}

export default function DataSourceBrowser({ path, writesEnabled }: DataSourceBrowserProps) {
  const { t } = useTranslation(["platform", "common"]);
  const catalogQuery = useQuery({
    queryKey: ["data-source-metadata", path],
    queryFn: () => fetchDataSourceCatalog(path),
  });
  const catalog = catalogQuery.data;
  const readOnly = catalog ? catalog.readOnly : !writesEnabled;
  const quote = catalog?.identifierQuote || '"';

  const [filter, setFilter] = useState("");
  const [columnsByTable, setColumnsByTable] = useState<Record<string, DataSourceColumn[]>>({});
  const [columnError, setColumnError] = useState<string | null>(null);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [runningId, setRunningId] = useState<string | null>(null);
  const [tabs, setTabs] = useState<SqlTab[]>(() => [
    { id: nextTabId(), title: "SQL 1", sql: "SELECT 1 AS n", result: null, error: null },
  ]);
  const [activeId, setActiveId] = useState(tabs[0].id);

  const treeData = useMemo(
    () => buildTree(catalog, filter, columnsByTable, t("platform:dataSource.view")),
    [catalog, filter, columnsByTable, t],
  );

  const selectedTable = selectedKey ? parseTableNodeKey(selectedKey) : null;

  const run = async (tabId: string, sql: string, maxRows?: number) => {
    setRunningId(tabId);
    setTabs((prev) => prev.map((tab) => (tab.id === tabId ? { ...tab, error: null } : tab)));
    try {
      const result = await executeDataSourceQuery(path, { query: sql, maxRows });
      setTabs((prev) => prev.map((tab) => (tab.id === tabId ? { ...tab, result, error: null } : tab)));
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setTabs((prev) => prev.map((tab) => (tab.id === tabId ? { ...tab, result: null, error: message } : tab)));
    } finally {
      setRunningId((current) => (current === tabId ? null : current));
    }
  };

  const openTable = (
    table: { schema: string | null; catalog: string | null; table: string },
    mode: "data" | "sql",
  ) => {
    const qualifier = table.schema || table.catalog;
    let sql: string;
    try {
      sql = previewSelect(qualifier, table.table, quote);
    } catch (error) {
      setColumnError(error instanceof Error ? error.message : String(error));
      return;
    }
    const title = qualifier ? `${qualifier}.${table.table}` : table.table;
    const existing = tabs.find((tab) => tab.title === title);
    const id = existing?.id ?? nextTabId();
    if (!existing) {
      setTabs((prev) => [...prev, { id, title, sql, result: null, error: null }]);
    } else {
      setTabs((prev) => prev.map((tab) => (tab.id === id ? { ...tab, sql } : tab)));
    }
    setActiveId(id);
    if (mode === "data") {
      void run(id, sql, TABLE_PREVIEW_MAX_ROWS);
    }
  };

  const addQuery = () => {
    const id = nextTabId();
    const title = `SQL ${tabs.length + 1}`;
    setTabs((prev) => [...prev, { id, title, sql: "SELECT 1 AS n", result: null, error: null }]);
    setActiveId(id);
  };

  const closeTab = (id: string) => {
    const remaining = tabs.filter((tab) => tab.id !== id);
    if (remaining.length === 0) {
      const fresh = { id: nextTabId(), title: "SQL 1", sql: "SELECT 1 AS n", result: null, error: null };
      setTabs([fresh]);
      setActiveId(fresh.id);
      return;
    }
    setTabs(remaining);
    if (activeId === id) {
      setActiveId(remaining[remaining.length - 1].id);
    }
  };

  return (
    <div className="data-source-browser">
      <aside className="data-source-schema-pane" aria-label={t("platform:dataSource.browser")}>
        <header className="data-source-schema-head">
          <h3 className="data-source-section-title">{t("platform:dataSource.browser")}</h3>
          <div className="data-source-schema-actions">
            <Button size="small" disabled={catalogQuery.isFetching} onClick={() => void catalogQuery.refetch()}>
              {t("platform:dataSource.refreshSchema")}
            </Button>
            {writesEnabled && !readOnly && (
              <Button size="small" type="primary" onClick={() => setCreateOpen(true)}>
                {t("platform:dataSource.createTable")}
              </Button>
            )}
          </div>
        </header>
        <Input
          allowClear
          value={filter}
          placeholder={t("platform:dataSource.schemaFilter")}
          onChange={(event) => setFilter(event.target.value)}
        />
        {catalogQuery.isLoading && <p className="hint">{t("platform:dataSource.loadingSchema")}</p>}
        {catalogQuery.error && (
          <Alert type="error" showIcon title={String(catalogQuery.error)} />
        )}
        {columnError && <Alert type="error" showIcon title={columnError} />}
        {catalog && (catalog.schemasTruncated || catalog.schemas.some((schema) => schema.truncated)) && (
          <p className="data-source-sql-hint">{t("platform:dataSource.schemaTruncated")}</p>
        )}
        {catalog && treeData.length === 0 && !catalogQuery.isLoading && (
          <p className="hint">{t("platform:dataSource.schemaEmpty")}</p>
        )}
        <div className="data-source-schema-tree">
          <Tree
            blockNode
            selectedKeys={selectedKey ? [selectedKey] : []}
            treeData={treeData}
            loadData={async (node) => {
              const key = String(node.key);
              const parsed = parseTableNodeKey(key);
              if (!parsed || columnsByTable[key]) {
                return;
              }
              try {
                const columns = await fetchDataSourceColumns(path, {
                  catalog: parsed.catalog,
                  schema: parsed.schema,
                  table: parsed.table,
                });
                setColumnsByTable((prev) => ({ ...prev, [key]: columns }));
                setColumnError(null);
              } catch (error) {
                setColumnError(error instanceof Error ? error.message : String(error));
              }
            }}
            onSelect={(keys) => setSelectedKey(keys.length > 0 ? String(keys[0]) : null)}
            titleRender={(node) => (
              <span
                onDoubleClick={(event) => {
                  event.preventDefault();
                  const parsed = parseTableNodeKey(String(node.key));
                  if (!parsed) {
                    return;
                  }
                  setSelectedKey(String(node.key));
                  openTable(parsed, "data");
                }}
              >
                {node.title as ReactNode}
              </span>
            )}
          />
        </div>
        <div className="data-source-schema-open">
          <Button size="small" disabled={!selectedTable} onClick={() => selectedTable && openTable(selectedTable, "data")}>
            {t("platform:dataSource.openData")}
          </Button>
          <Button size="small" disabled={!selectedTable} onClick={() => selectedTable && openTable(selectedTable, "sql")}>
            {t("platform:dataSource.openSql")}
          </Button>
        </div>
      </aside>

      <section className="data-source-work" aria-label={t("platform:dataSource.sqlConsole")}>
        {readOnly && <Alert type="info" showIcon title={t("platform:dataSource.readOnlyBanner")} />}
        <Tabs
          className="data-source-tabs"
          type="editable-card"
          activeKey={activeId}
          onChange={setActiveId}
          onEdit={(targetKey, action) => {
            if (action === "add") {
              addQuery();
              return;
            }
            if (typeof targetKey === "string") {
              closeTab(targetKey);
            }
          }}
          items={tabs.map((tab) => ({
            key: tab.id,
            label: tab.title,
            children: (
              <SqlTabPane
                tab={tab}
                readOnly={readOnly}
                running={runningId === tab.id}
                onSql={(sql) =>
                  setTabs((prev) => prev.map((item) => (item.id === tab.id ? { ...item, sql } : item)))
                }
                onRun={() => void run(tab.id, tab.sql)}
                onExport={() => {
                  if (!tab.result || tab.result.kind !== "rows") {
                    return;
                  }
                  const columns = tab.result.columns.length > 0
                    ? tab.result.columns
                    : Object.keys(tab.result.rows[0] ?? {});
                  downloadCsv(`${tab.title}.csv`, columns, tab.result.rows);
                }}
              />
            ),
          }))}
        />
      </section>

      {writesEnabled && !readOnly && (
        <DataSourceCreateTableDialog path={path} open={createOpen} onClose={() => setCreateOpen(false)} />
      )}
    </div>
  );
}

function SqlTabPane({
  tab,
  readOnly,
  running,
  onSql,
  onRun,
  onExport,
}: {
  tab: SqlTab;
  readOnly: boolean;
  running: boolean;
  onSql: (sql: string) => void;
  onRun: () => void;
  onExport: () => void;
}) {
  const { t } = useTranslation(["platform", "common"]);
  const columns = tab.result?.columns ?? [];
  return (
    <div className="data-source-tab-body">
      <div className="data-source-sql-head">
        <p className="data-source-sql-hint">
          {readOnly ? t("platform:dataSource.sqlHintReadOnly") : t("platform:dataSource.sqlHint")}
        </p>
        <div className="data-source-schema-actions">
          <Button size="small" disabled={!tab.result || tab.result.kind !== "rows"} onClick={onExport}>
            {t("platform:dataSource.exportCsv")}
          </Button>
          <Button type="primary" disabled={running || !tab.sql.trim()} onClick={onRun}>
            {running ? t("platform:dataSource.executing") : t("platform:dataSource.executeQuery")}
          </Button>
        </div>
      </div>
      <TextArea
        className="data-source-sql-input mono"
        value={tab.sql}
        onChange={(event) => onSql(event.target.value)}
        spellCheck={false}
        aria-label={t("platform:dataSource.sqlInputLabel")}
      />
      {tab.error && <Alert type="error" showIcon title={tab.error} />}
      {tab.result?.kind === "update" && (
        <p className="data-source-sql-meta tabular-nums">
          {t("platform:dataSource.updateResult", { count: tab.result.updateCount })}
        </p>
      )}
      {tab.result?.kind === "rows" && (
        <div className="data-source-sql-results">
          <p className="data-source-sql-meta tabular-nums">
            {t("platform:dataSource.rowCount", { count: tab.result.rowCount })}
          </p>
          <div className="data-source-sql-table-wrap">
            <BffDataTable rows={tab.result.rows} columnOrder={columns} />
          </div>
        </div>
      )}
    </div>
  );
}

function buildTree(
  catalog: DataSourceCatalog | undefined,
  filter: string,
  columnsByTable: Record<string, DataSourceColumn[]>,
  viewLabel: string,
): TreeDataNode[] {
  if (!catalog) {
    return [];
  }
  const needle = filter.trim().toLowerCase();
  const nodes: TreeDataNode[] = [];
  for (const schema of catalog.schemas) {
    const tables = schema.tables.filter((table) => !needle || table.name.toLowerCase().includes(needle));
    if (needle && tables.length === 0) {
      continue;
    }
    nodes.push({
      key: `schema|${encodeURIComponent(schema.name)}`,
      title: schema.name,
      selectable: false,
      children: tables.map((table) => {
        const key = tableNodeKey(schema.name, schema.catalog, schema.schema, table.name);
        const loaded = columnsByTable[key];
        return {
          key,
          title: table.type === "VIEW" ? `${table.name} (${viewLabel})` : table.name,
          isLeaf: false,
          children: loaded ? loaded.map((column) => columnNode(key, column)) : undefined,
        };
      }),
    });
  }
  return nodes;
}

function columnNode(tableKey: string, column: DataSourceColumn): TreeDataNode {
  const type = column.typeName || "";
  const pk = column.primaryKey ? " PK" : "";
  return {
    key: `column|${tableKey}|${encodeURIComponent(column.name)}`,
    title: `${column.name} ${type}${pk}`.trim(),
    isLeaf: true,
  };
}
