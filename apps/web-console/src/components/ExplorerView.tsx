import ObjectInspector from "./ObjectInspector";
import OperatorAppsPanel from "./OperatorAppsPanel";
import SecurityUsersPanel from "./SecurityUsersPanel";
import SecurityUserInspector from "./SecurityUserInspector";
import SecurityRolesPanel from "./SecurityRolesPanel";
import SecurityRoleInspector from "./SecurityRoleInspector";
import SystemFolderListPanel from "./SystemFolderListPanel";
import FederationPeersPanel from "./FederationPeersPanel";
import TenantsPanel from "./TenantsPanel";
import { isModelsPath } from "../types/models";
import type { ObjectSummary } from "../types";
import {
  isOperatorAppChildPath,
} from "../utils/operatorAppsPath";
import { isSecurityUserPath, isSecurityUsersRoot } from "../utils/securityUserPath";
import { isSecurityRolePath, isSecurityRolesRoot } from "../utils/securityRolePath";
import {
  isAlertRulePath,
  isCorrelatorPath,
} from "../utils/automationPath";
import AlertRuleInspector from "./automation/AlertRuleInspector";
import CorrelatorInspector from "./automation/CorrelatorInspector";
import { canCreateChildAt, createActionLabel } from "../utils/createObjectMode";
import { isSpecializedEditorObject } from "../utils/editorObject";
import { isSystemCatalogFolder } from "../utils/systemFolderConfig";
import { isPlatformSqlObjectPath } from "../utils/platformSqlPath";
import VisualGroupInspector from "./VisualGroupInspector";
import PlatformSqlObjectPanel from "./platform/PlatformSqlObjectPanel";
import { isFederationRoot } from "../utils/federationPath";
import { isTenantsRoot } from "../utils/tenantPath";

interface ExplorerViewProps {
  selectedPath: string | null;
  selectedObject: ObjectSummary | null;
  onOpenEditor: (path: string) => void;
  onCreateChild: () => void;
  onDeleted: () => void;
  onSelectPath: (path: string) => void;
  onMembersChanged?: () => void;
  allObjects?: ObjectSummary[];
  isAdmin: boolean;
  showBackToTree?: boolean;
  onBackToTree?: () => void;
}

export default function ExplorerView({
  selectedPath,
  selectedObject,
  onOpenEditor,
  onCreateChild,
  onDeleted,
  onSelectPath,
  onMembersChanged,
  allObjects = [],
  isAdmin,
  showBackToTree = false,
  onBackToTree,
}: ExplorerViewProps) {
  if (!selectedPath) {
    return <div className="inspector-empty">Выберите объект в дереве</div>;
  }

  const isOperatorAppChild = isOperatorAppChildPath(selectedPath);
  const isUsersRoot = isSecurityUsersRoot(selectedPath);
  const isUserObject = isSecurityUserPath(selectedPath);
  const isRolesRoot = isSecurityRolesRoot(selectedPath);
  const isRoleObject = isSecurityRolePath(selectedPath);
  const isAlertRule = isAlertRulePath(selectedPath);
  const isCorrelator = isCorrelatorPath(selectedPath);
  const isFederation = isFederationRoot(selectedPath);
  const isTenants = isTenantsRoot(selectedPath);
  const isCatalogFolder = isSystemCatalogFolder(selectedPath, selectedObject?.type);
  const isPlatformSqlObject = isPlatformSqlObjectPath(selectedPath);
  const opensInEditor = Boolean(
    selectedObject
    && isSpecializedEditorObject(selectedPath, selectedObject.type, selectedObject.templateId),
  );
  const showCreateButton =
    isAdmin
    && canCreateChildAt(selectedPath, selectedObject?.type)
    && !isUsersRoot
    && !isRolesRoot;
  const isVisualGroup = selectedObject?.type === "VISUAL_GROUP";
  const hideToolbar =
    isOperatorAppChild
    || isUsersRoot
    || isUserObject
    || isRolesRoot
    || isRoleObject
    || isAlertRule
    || isCorrelator
    || isFederation
    || isTenants
    || isCatalogFolder
    || isPlatformSqlObject
    || isVisualGroup;

  return (
    <div
      className={`explorer-view${isOperatorAppChild ? " explorer-view-operator-app" : ""}`}
    >
      {showBackToTree && onBackToTree && (
        <div className="explorer-mobile-nav">
          <button type="button" className="btn explorer-back-btn" onClick={onBackToTree}>
            ← Дерево объектов
          </button>
        </div>
      )}
      {!hideToolbar && (
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
              : opensInEditor
                ? "Редактор — двойной щелчок в дереве или «Открыть в редакторе»"
                : "Двойной щелчок по узлу также открывает редактор"}
          </span>
        </div>
      )}

      {isOperatorAppChild ? (
        <OperatorAppsPanel canManage={isAdmin} selectedPath={selectedPath} />
      ) : isUsersRoot ? (
        <SecurityUsersPanel canManage={isAdmin} onSelectUser={onSelectPath} />
      ) : isUserObject ? (
        <SecurityUserInspector key={selectedPath} path={selectedPath} canManage={isAdmin} onDeleted={onDeleted} />
      ) : isRolesRoot ? (
        <SecurityRolesPanel canManage={isAdmin} onSelectRole={onSelectPath} />
      ) : isRoleObject ? (
        <SecurityRoleInspector key={selectedPath} path={selectedPath} canManage={isAdmin} onDeleted={onDeleted} />
      ) : isAlertRule ? (
        <AlertRuleInspector key={selectedPath} path={selectedPath} canManage={isAdmin} />
      ) : isCorrelator ? (
        <CorrelatorInspector key={selectedPath} path={selectedPath} canManage={isAdmin} />
      ) : isFederation ? (
        <FederationPeersPanel canManage={isAdmin} />
      ) : isTenants ? (
        <TenantsPanel canManage={isAdmin} onSelectPath={onSelectPath} />
      ) : isPlatformSqlObject ? (
        <PlatformSqlObjectPanel key={selectedPath} path={selectedPath} onOpenEditor={onOpenEditor} />
      ) : isVisualGroup ? (
        <VisualGroupInspector
          key={selectedPath}
          path={selectedPath}
          canManage={isAdmin}
          allObjects={allObjects}
          onSelectPath={onSelectPath}
          onMembersChanged={onMembersChanged}
        />
      ) : isCatalogFolder ? (
        <SystemFolderListPanel
          key={selectedPath}
          folderPath={selectedPath}
          folderType={selectedObject?.type}
          folderDisplayName={selectedObject?.displayName}
          folderDescription={selectedObject?.description}
          canManage={isAdmin}
          createLabel={showCreateButton ? createActionLabel(selectedPath) : undefined}
          onCreateChild={showCreateButton ? onCreateChild : undefined}
          onSelectPath={onSelectPath}
          onOpenEditor={onOpenEditor}
        />
      ) : (
        <ObjectInspector path={selectedPath} onDeleted={onDeleted} canManage={isAdmin} />
      )}
    </div>
  );
}
