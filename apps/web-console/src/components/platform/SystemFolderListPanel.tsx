import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchObjects } from "../../api";
import type { ObjectType } from "../../types";
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
  const [menuPath, setMenuPath] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);

  const childrenQuery = useQuery({
    queryKey: ["objects", folderPath],
    queryFn: () => fetchObjects(folderPath),
  });

  useEffect(() => {
    if (!menuPath) {
      return;
    }
    const onDoc = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuPath(null);
      }
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [menuPath]);

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

  return (
    <section className="security-users-panel catalog-folder-panel">
      <header className="security-users-header">
        <div>
          <h3>{meta.title}</h3>
          <p className="op-muted catalog-folder-desc" title={meta.description}>
            {truncateText(meta.description, 160)}
          </p>
        </div>
        {canManage && createAction && (
          <button type="button" className="btn primary" onClick={createAction}>
            {isApplicationsFolder
              ? t("contextMenu.create.application")
              : t("folderList.create")}
          </button>
        )}
      </header>

      {childrenQuery.isLoading && <p className="op-muted">{t("common:action.loading")}</p>}
      {childrenQuery.error && (
        <div className="op-alert op-alert-error">{String(childrenQuery.error)}</div>
      )}

      {!childrenQuery.isLoading && !childrenQuery.error && (childrenQuery.data?.length ?? 0) === 0 && (
        <EmptyState
          title={t("folderList.empty")}
          hint={t("folderList.emptyCreate")}
          action={
            canManage && createAction ? (
              <button type="button" className="btn primary" onClick={createAction}>
                {t("folderList.create")}
              </button>
            ) : undefined
          }
        />
      )}

      {(childrenQuery.data?.length ?? 0) > 0 && (
        <>
          <div className="catalog-folder-toolbar">
            <input
              type="search"
              className="catalog-folder-search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t("folderList.searchPlaceholder")}
              aria-label={t("folderList.searchPlaceholder")}
            />
            <span className="op-muted catalog-folder-count">
              {t("folderList.count", { count: rows.length, total: childrenQuery.data?.length ?? 0 })}
            </span>
          </div>

          {rows.length === 0 ? (
            <EmptyState title={t("common:empty.noMatches")} />
          ) : (
            <div className="catalog-folder-table-wrap">
              <table className="op-table security-users-table security-users-table-compact catalog-folder-table">
                <thead>
                  <tr>
                    <th>
                      <button type="button" className="catalog-sort-btn" onClick={() => toggleSort("name")}>
                        {t("common:field.displayName")}
                        {sortMark("name")}
                      </button>
                    </th>
                    <th>
                      <button type="button" className="catalog-sort-btn" onClick={() => toggleSort("type")}>
                        {t("common:table.type")}
                        {sortMark("type")}
                      </button>
                    </th>
                    <th>
                      <button type="button" className="catalog-sort-btn" onClick={() => toggleSort("template")}>
                        {t("folderList.template")}
                        {sortMark("template")}
                      </button>
                    </th>
                    <th>{t("common:table.description")}</th>
                    <th className="catalog-actions-col">{t("common:table.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((child) => {
                    const id = childIdFromPath(folderPath, child.path);
                    const name = child.displayName || id;
                    const showIdUnderName = id !== name;
                    const canOpenEditor = Boolean(
                      onOpenEditor && isSpecializedEditorObject(child.path, child.type, child.templateId),
                    );
                    const canOpenOperatorApp = Boolean(
                      onOpenOperatorApp && isOperatorAppChildPath(child.path),
                    );
                    const description = (child.description || "").trim();
                    return (
                      <tr
                        key={child.path}
                        className="catalog-row"
                        onDoubleClick={() => {
                          if (canOpenOperatorApp) {
                            onOpenOperatorApp?.(child.path);
                            return;
                          }
                          if (canOpenEditor) {
                            onOpenEditor?.(child.path);
                            return;
                          }
                          onSelectPath(child.path);
                        }}
                      >
                        <td>
                          <button
                            type="button"
                            className="link-btn catalog-name-btn"
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
                          </button>
                        </td>
                        <td>
                          <span className="catalog-type-chip">{child.type}</span>
                        </td>
                        <td>
                          {child.templateId ? (
                            <span className="catalog-template-chip" title={child.templateId}>
                              {shortTemplateId(child.templateId)}
                            </span>
                          ) : (
                            <span className="op-muted">{t("common:empty.dash")}</span>
                          )}
                        </td>
                        <td className="catalog-desc-cell">
                          {description ? (
                            <span title={description}>{truncateText(description)}</span>
                          ) : (
                            <span className="op-muted">{t("common:empty.dash")}</span>
                          )}
                        </td>
                        <td className="catalog-actions-col">
                          <div className="catalog-row-actions" ref={menuPath === child.path ? menuRef : undefined}>
                            {(canOpenEditor || canOpenOperatorApp) && (
                              <button
                                type="button"
                                className="btn btn-sm"
                                onClick={() => {
                                  if (canOpenOperatorApp) {
                                    onOpenOperatorApp?.(child.path);
                                    return;
                                  }
                                  onOpenEditor?.(child.path);
                                }}
                              >
                                {t("common:action.open")}
                              </button>
                            )}
                            <button
                              type="button"
                              className="btn btn-sm catalog-kebab"
                              aria-label={t("folderList.moreActions")}
                              aria-expanded={menuPath === child.path}
                              onClick={() =>
                                setMenuPath((current) => (current === child.path ? null : child.path))
                              }
                            >
                              ⋮
                            </button>
                            {menuPath === child.path && (
                              <div className="catalog-kebab-menu" role="menu">
                                <button
                                  type="button"
                                  role="menuitem"
                                  onClick={() => {
                                    onSelectPath(child.path);
                                    setMenuPath(null);
                                  }}
                                >
                                  {t("folderList.action.select")}
                                </button>
                                {(canOpenEditor || canOpenOperatorApp) && (
                                  <button
                                    type="button"
                                    role="menuitem"
                                    onClick={() => {
                                      if (canOpenOperatorApp) {
                                        onOpenOperatorApp?.(child.path);
                                      } else {
                                        onOpenEditor?.(child.path);
                                      }
                                      setMenuPath(null);
                                    }}
                                  >
                                    {t("common:action.open")}
                                  </button>
                                )}
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}
