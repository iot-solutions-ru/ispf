import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Input, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { fetchObjects } from "../../api";
import type { ObjectSummary, ObjectType } from "../../types";
import {
  childIdFromPath,
  getSystemFolderListMeta,
  type SystemFolderListMeta,
} from "../../utils/platform/systemFolderConfig";
import { isSpecializedEditorObject } from "../../utils/object/editorObject";
import { isOperatorAppChildPath } from "../../utils/operator/operatorAppsPath";
import { APPLICATIONS_ROOT } from "../../utils/object/createObjectMode";
import EmptyState from "../ui/EmptyState";

interface SystemFolderListPanelProps {
  folderPath: string;
  folderType?: ObjectType;
  folderDisplayName?: string;
  folderDescription?: string;
  onSelectPath: (path: string) => void;
  onOpenEditor?: (path: string) => void;
  onOpenOperatorApp?: (path: string) => void;
  onCreateApplication?: () => void;
  onCreate?: () => void;
  canManage?: boolean;
}

type SortKey = "name" | "type" | "template";

function shortTemplateId(templateId: string): string {
  if (templateId.length <= 28) {
    return templateId;
  }
  const leaf = templateId.includes(".")
    ? templateId.slice(templateId.lastIndexOf(".") + 1)
    : templateId;
  return leaf.length <= 28 ? leaf : `${leaf.slice(0, 25)}…`;
}

function truncateText(text: string, max = 96): string {
  const normalized = text.replace(/\s+/g, " ").trim();
  if (normalized.length <= max) {
    return normalized;
  }
  return `${normalized.slice(0, max - 1)}…`;
}

export default function SystemFolderListPanel({
  folderPath,
  folderType,
  folderDisplayName,
  folderDescription,
  onSelectPath,
  onOpenEditor,
  onOpenOperatorApp,
  onCreateApplication,
  onCreate,
  canManage = false,
}: SystemFolderListPanelProps) {
  const { t } = useTranslation(["explorer", "common", "objectTree"]);
  const meta: SystemFolderListMeta = getSystemFolderListMeta(
    folderPath,
    t,
    folderType,
    folderDisplayName,
    folderDescription,
  );

  const [query, setQuery] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("name");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");

  const childrenQuery = useQuery({
    queryKey: ["objects", folderPath],
    queryFn: () => fetchObjects(folderPath),
  });

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    let items = [...(childrenQuery.data ?? [])];
    if (q) {
      items = items.filter((child) => {
        const id = childIdFromPath(folderPath, child.path).toLowerCase();
        return (
          id.includes(q)
          || child.displayName.toLowerCase().includes(q)
          || child.type.toLowerCase().includes(q)
          || (child.templateId ?? "").toLowerCase().includes(q)
          || (child.description ?? "").toLowerCase().includes(q)
        );
      });
    }
    const dir = sortDir === "asc" ? 1 : -1;
    items.sort((a, b) => {
      const idA = childIdFromPath(folderPath, a.path);
      const idB = childIdFromPath(folderPath, b.path);
      let cmp = 0;
      if (sortKey === "type") {
        cmp = a.type.localeCompare(b.type);
      } else if (sortKey === "template") {
        cmp = (a.templateId ?? "").localeCompare(b.templateId ?? "");
      } else {
        cmp = (a.displayName || idA).localeCompare(b.displayName || idB, undefined, {
          sensitivity: "base",
        });
      }
      if (cmp === 0) {
        cmp = a.path.localeCompare(b.path);
      }
      return cmp * dir;
    });
    return items;
  }, [childrenQuery.data, folderPath, query, sortDir, sortKey]);

  const isApplicationsFolder = folderPath === APPLICATIONS_ROOT;
  const createAction = isApplicationsFolder
    ? onCreateApplication
    : onCreate;

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
      return;
    }
    setSortKey(key);
    setSortDir("asc");
  };

  const sortMark = (key: SortKey) => {
    if (sortKey !== key) {
      return "";
    }
    return sortDir === "asc" ? " ↑" : " ↓";
  };
  const openChild = (child: ObjectSummary) => {
    const canOpenEditor = Boolean(
      onOpenEditor && isSpecializedEditorObject(child.path, child.type, child.templateId),
    );
    const canOpenOperatorApp = Boolean(
      onOpenOperatorApp && isOperatorAppChildPath(child.path),
    );
    if (canOpenOperatorApp) {
      onOpenOperatorApp?.(child.path);
      return;
    }
    if (canOpenEditor) {
      onOpenEditor?.(child.path);
      return;
    }
    onSelectPath(child.path);
  };
  const columns: TableColumnsType<ObjectSummary> = [
    {
      title: (
        <Button type="link" className="catalog-sort-btn" onClick={() => toggleSort("name")}>
          {t("common:field.displayName")}
          {sortMark("name")}
        </Button>
      ),
      key: "name",
      render: (_, child) => {
        const id = childIdFromPath(folderPath, child.path);
        const name = child.displayName || id;
        const showIdUnderName = id !== name;
        const canOpenEditor = Boolean(
          onOpenEditor && isSpecializedEditorObject(child.path, child.type, child.templateId),
        );
        const canOpenOperatorApp = Boolean(
          onOpenOperatorApp && isOperatorAppChildPath(child.path),
        );
        return (
          <Button
            type="link"
            className="catalog-name-btn"
            onClick={() => {
              if (canOpenOperatorApp) {
                onOpenOperatorApp?.(child.path);
                return;
              }
              if (canOpenEditor && child.type === "DASHBOARD") {
                onOpenEditor?.(child.path);
                return;
              }
              onSelectPath(child.path);
            }}
          >
            <span className="catalog-name">{name}</span>
            {showIdUnderName && <span className="catalog-id-sub">{id}</span>}
          </Button>
        );
      },
    },
    {
      title: (
        <Button type="link" className="catalog-sort-btn" onClick={() => toggleSort("type")}>
          {t("common:table.type")}
          {sortMark("type")}
        </Button>
      ),
      dataIndex: "type",
      key: "type",
      render: (type: string) => <Tag className="catalog-type-chip">{type}</Tag>,
    },
    {
      title: (
        <Button type="link" className="catalog-sort-btn" onClick={() => toggleSort("template")}>
          {t("folderList.template")}
          {sortMark("template")}
        </Button>
      ),
      dataIndex: "templateId",
      key: "templateId",
      render: (templateId: string | null | undefined) =>
        templateId ? (
          <Tag className="catalog-template-chip" title={templateId}>
            {shortTemplateId(templateId)}
          </Tag>
        ) : (
          <Typography.Text type="secondary">{t("common:empty.dash")}</Typography.Text>
        ),
    },
    {
      title: t("common:table.description"),
      dataIndex: "description",
      key: "description",
      render: (descriptionValue: string | null | undefined) => {
        const description = (descriptionValue || "").trim();
        return description ? (
          <span title={description}>{truncateText(description)}</span>
        ) : (
          <Typography.Text type="secondary">{t("common:empty.dash")}</Typography.Text>
        );
      },
    },
    {
      title: t("common:table.actions"),
      key: "actions",
      className: "catalog-actions-col",
      render: (_, child) => {
        const canOpenEditor = Boolean(
          onOpenEditor && isSpecializedEditorObject(child.path, child.type, child.templateId),
        );
        const canOpenOperatorApp = Boolean(
          onOpenOperatorApp && isOperatorAppChildPath(child.path),
        );
        return (
          <Space className="catalog-row-actions">
            {(canOpenEditor || canOpenOperatorApp) && (
              <Button size="small" onClick={() => openChild(child)}>
                {t("common:action.open")}
              </Button>
            )}
            <Button size="small" onClick={() => onSelectPath(child.path)}>
              {t("folderList.action.select")}
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <section className="security-users-panel catalog-folder-panel">
      <header className="security-users-header">
        <div>
          <Typography.Title level={3}>{meta.title}</Typography.Title>
          <Typography.Paragraph type="secondary" className="catalog-folder-desc" title={meta.description}>
            {truncateText(meta.description, 160)}
          </Typography.Paragraph>
        </div>
        {canManage && createAction && (
          <Button type="primary" onClick={createAction}>
            {isApplicationsFolder
              ? t("contextMenu.create.application")
              : t("folderList.create")}
          </Button>
        )}
      </header>

      {childrenQuery.isLoading && <Typography.Text type="secondary">{t("common:action.loading")}</Typography.Text>}
      {childrenQuery.error && (
        <Alert type="error" showIcon message={String(childrenQuery.error)} />
      )}

      {!childrenQuery.isLoading && !childrenQuery.error && (childrenQuery.data?.length ?? 0) === 0 && (
        <EmptyState
          title={t("folderList.empty")}
          hint={t("folderList.emptyCreate")}
          action={
            canManage && createAction ? (
              <Button type="primary" onClick={createAction}>
                {t("folderList.create")}
              </Button>
            ) : undefined
          }
        />
      )}

      {(childrenQuery.data?.length ?? 0) > 0 && (
        <>
          <div className="catalog-folder-toolbar">
            <Input.Search
              className="catalog-folder-search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t("folderList.searchPlaceholder")}
              aria-label={t("folderList.searchPlaceholder")}
            />
            <Typography.Text type="secondary" className="catalog-folder-count">
              {t("folderList.count", { count: rows.length, total: childrenQuery.data?.length ?? 0 })}
            </Typography.Text>
          </div>

          {rows.length === 0 ? (
            <EmptyState title={t("common:empty.noMatches")} />
          ) : (
            <div className="catalog-folder-table-wrap">
              <Table
                className="security-users-table security-users-table-compact catalog-folder-table"
                size="small"
                pagination={false}
                rowKey="path"
                rowClassName="catalog-row"
                columns={columns}
                dataSource={rows}
                onRow={(child) => ({
                  onDoubleClick: () => openChild(child),
                })}
              />
            </div>
          )}
        </>
      )}
    </section>
  );
}
