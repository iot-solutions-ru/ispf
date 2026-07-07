import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchObjects } from "../api";
import type { ObjectSummary } from "../types";
import {
  childIdFromPath,
  getSystemFolderListMeta,
  type SystemFolderListMeta,
} from "../utils/systemFolderConfig";
import type { ObjectType } from "../types";
import { isSpecializedEditorObject } from "../utils/editorObject";
import { isOperatorAppChildPath } from "../utils/operatorAppsPath";
import { APPLICATIONS_ROOT } from "../utils/createObjectMode";

interface SystemFolderListPanelProps {
  folderPath: string;
  folderType?: ObjectType;
  folderDisplayName?: string;
  folderDescription?: string;
  onSelectPath: (path: string) => void;
  onOpenEditor?: (path: string) => void;
  onOpenOperatorApp?: (path: string) => void;
  onCreateApplication?: () => void;
  canManage?: boolean;
}

function sortChildren(items: ObjectSummary[]): ObjectSummary[] {
  return [...items].sort((a, b) => {
    if (a.sortOrder !== b.sortOrder) {
      return a.sortOrder - b.sortOrder;
    }
    return a.path.localeCompare(b.path);
  });
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

  const childrenQuery = useQuery({
    queryKey: ["objects", folderPath],
    queryFn: () => fetchObjects(folderPath),
  });

  const children = sortChildren(childrenQuery.data ?? []);
  const isApplicationsFolder = folderPath === APPLICATIONS_ROOT;

  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>{meta.title}</h3>
          <p className="op-muted">{meta.description}</p>
        </div>
        {isApplicationsFolder && canManage && onCreateApplication && (
          <button type="button" className="btn primary" onClick={onCreateApplication}>
            {t("contextMenu.create.application")}
          </button>
        )}
      </header>

      {childrenQuery.isLoading && <p className="op-muted">{t("common:action.loading")}</p>}
      {childrenQuery.error && (
        <div className="op-alert op-alert-error">{String(childrenQuery.error)}</div>
      )}

      {!childrenQuery.isLoading && !childrenQuery.error && children.length === 0 && (
        <p className="op-muted">{t("folderList.emptyCreate")}</p>
      )}

      {children.length > 0 && (
        <table className="op-table security-users-table security-users-table-compact">
          <thead>
            <tr>
              <th>{meta.idColumnLabel}</th>
              <th>{t("common:field.displayName")}</th>
              <th>{t("common:table.type")}</th>
              <th>{t("folderList.template")}</th>
              <th>{t("common:table.description")}</th>
              {onOpenEditor && <th>{t("common:table.actions")}</th>}
            </tr>
          </thead>
          <tbody>
            {children.map((child) => {
              const canOpenEditor = Boolean(
                onOpenEditor && isSpecializedEditorObject(child.path, child.type, child.templateId),
              );
              const canOpenOperatorApp = Boolean(
                onOpenOperatorApp && isOperatorAppChildPath(child.path),
              );
              return (
              <tr
                key={child.path}
                className={canOpenEditor || canOpenOperatorApp ? "catalog-row-openable" : undefined}
                onDoubleClick={() => {
                  if (canOpenOperatorApp) {
                    onOpenOperatorApp?.(child.path);
                    return;
                  }
                  if (canOpenEditor) {
                    onOpenEditor?.(child.path);
                  }
                }}
              >
                <td>
                  <button
                    type="button"
                    className="link-btn"
                    onClick={() => onSelectPath(child.path)}
                  >
                    <code>{childIdFromPath(folderPath, child.path)}</code>
                  </button>
                </td>
                <td>{child.displayName}</td>
                <td>
                  <code>{child.type}</code>
                </td>
                <td>{child.templateId ? <code>{child.templateId}</code> : t("common:empty.dash")}</td>
                <td>{child.description || t("common:empty.dash")}</td>
                {onOpenEditor && (
                  <td>
                    {canOpenEditor ? (
                      <button
                        type="button"
                        className="btn btn-sm"
                        onClick={() => onOpenEditor(child.path)}
                      >
                        {t("common:action.open")}
                      </button>
                    ) : (
                      t("common:empty.dash")
                    )}
                  </td>
                )}
              </tr>
            );
            })}
          </tbody>
        </table>
      )}
    </section>
  );
}
