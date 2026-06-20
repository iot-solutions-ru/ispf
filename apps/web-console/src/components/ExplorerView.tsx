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
import {
  isAlertRulePath,
  isAlertRulesRoot,
  isCorrelatorPath,
  isCorrelatorsRoot,
} from "../utils/automationPath";
import AlertRuleInspector from "./automation/AlertRuleInspector";
import CorrelatorInspector from "./automation/CorrelatorInspector";
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
  const isAlertRule = isAlertRulePath(selectedPath);
  const isCorrelator = isCorrelatorPath(selectedPath);
  const isAlertRulesFolder = isAlertRulesRoot(selectedPath);
  const isCorrelatorsFolder = isCorrelatorsRoot(selectedPath);
  const showCreateButton =
    isAdmin
    && canCreateChildAt(selectedPath, selectedObject?.type)
    && !isUsersRoot
    && !isRolesRoot;

  return (
    <div
      className={`explorer-view${isOperatorAppChild ? " explorer-view-operator-app" : ""}`}
    >
      {!isOperatorAppChild && !isUsersRoot && !isUserObject && !isRolesRoot && !isRoleObject
        && !isAlertRule && !isCorrelator && (
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
      ) : isAlertRule ? (
        <AlertRuleInspector path={selectedPath} canManage={isAdmin} />
      ) : isCorrelator ? (
        <CorrelatorInspector path={selectedPath} canManage={isAdmin} />
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
          {isAlertRulesFolder && (
            <section className="operator-apps-folder-hint">
              <p className="hint">
                CEL-правила публикуют события при изменении переменных. Создайте правило кнопкой выше.
              </p>
            </section>
          )}
          {isCorrelatorsFolder && (
            <section className="operator-apps-folder-hint">
              <p className="hint">
                Корреляторы реагируют на события и запускают workflow. Создайте коррелятор кнопкой выше.
              </p>
            </section>
          )}
        </>
      )}
    </div>
  );
}
