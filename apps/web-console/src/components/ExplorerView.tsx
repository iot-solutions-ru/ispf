import ObjectInspector from "./ObjectInspector";
import OperatorAppsPanel from "./OperatorAppsPanel";
import SecurityUsersPanel from "./SecurityUsersPanel";
import SecurityUserInspector from "./SecurityUserInspector";
import SecurityRolesPanel from "./SecurityRolesPanel";
import SecurityRoleInspector from "./SecurityRoleInspector";
import { isModelsPath } from "../types/models";
import type { ObjectSummary } from "../types";
import {
  isOperatorAppChildPath,
  isOperatorAppsRootPath,
} from "../utils/operatorAppsPath";
import { isSecurityUserPath, isSecurityUsersRoot } from "../utils/securityUserPath";
import { isSecurityRolePath, isSecurityRolesRoot } from "../utils/securityRolePath";
import { APPLICATIONS_ROOT, canCreateChildAt, createActionLabel } from "../utils/createObjectMode";

interface ExplorerViewProps {
  selectedPath: string | null;
  selectedObject: ObjectSummary | null;
  onOpenEditor: (path: string) => void;
  onCreateChild: () => void;
  onDeleted: () => void;
  onSelectPath: (path: string) => void;
  isAdmin: boolean;
}

export default function ExplorerView({
  selectedPath,
  selectedObject,
  onOpenEditor,
  onCreateChild,
  onDeleted,
  onSelectPath,
  isAdmin,
}: ExplorerViewProps) {
  if (!selectedPath) {
    return <div className="inspector-empty">Выберите объект в дереве</div>;
  }

  const isOperatorAppChild = isOperatorAppChildPath(selectedPath);
  const isOperatorAppsRoot = isOperatorAppsRootPath(selectedPath);
  const isApplicationsRoot = selectedPath === APPLICATIONS_ROOT;
  const isUsersRoot = isSecurityUsersRoot(selectedPath);
  const isUserObject = isSecurityUserPath(selectedPath);
  const isRolesRoot = isSecurityRolesRoot(selectedPath);
  const isRoleObject = isSecurityRolePath(selectedPath);
  const showCreateButton =
    isAdmin
    && canCreateChildAt(selectedPath, selectedObject?.type)
    && !isUsersRoot
    && !isRolesRoot;

  return (
    <div
      className={`explorer-view${isOperatorAppChild ? " explorer-view-operator-app" : ""}`}
    >
      {!isOperatorAppChild && !isUsersRoot && !isUserObject && !isRolesRoot && !isRoleObject && (
        <div className="explorer-toolbar">
          {showCreateButton && (
            <button type="button" className="btn primary" onClick={onCreateChild}>
              {createActionLabel(selectedPath)}
            </button>
          )}
          <button type="button" className="btn" onClick={() => onOpenEditor(selectedPath)}>
            Открыть в редакторе
          </button>
          <span className="hint">
            {isModelsPath(selectedPath)
              ? "Полное определение модели — в редакторе (кнопка выше или двойной щелчок)"
              : "Двойной щелчок по узлу также открывает редактор"}
          </span>
        </div>
      )}

      {isOperatorAppChild ? (
        <OperatorAppsPanel canManage={isAdmin} selectedPath={selectedPath} />
      ) : isUsersRoot ? (
        <SecurityUsersPanel canManage={isAdmin} onSelectUser={onSelectPath} />
      ) : isUserObject ? (
        <SecurityUserInspector path={selectedPath} canManage={isAdmin} onDeleted={onDeleted} />
      ) : isRolesRoot ? (
        <SecurityRolesPanel canManage={isAdmin} onSelectRole={onSelectPath} />
      ) : isRoleObject ? (
        <SecurityRoleInspector path={selectedPath} canManage={isAdmin} onDeleted={onDeleted} />
      ) : (
        <>
          <ObjectInspector path={selectedPath} onDeleted={onDeleted} canManage={isAdmin} />
          {isApplicationsRoot && (
            <section className="operator-apps-folder-hint">
              <p className="hint">
                Deploy-приложения (функции, отчёты, bundle). Укажите App ID и название, затем
                deploy bundle через API.
              </p>
            </section>
          )}
          {isOperatorAppsRoot && (
            <section className="operator-apps-folder-hint">
              <p className="hint">
                Operator UI настраивается в дочерних объектах. Новое приложение — кнопка выше.
              </p>
            </section>
          )}
        </>
      )}
    </div>
  );
}
