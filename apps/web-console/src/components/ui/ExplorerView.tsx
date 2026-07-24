import { useTranslation } from "react-i18next";
import { Button } from "antd";
import ObjectPropertiesEditor from "../objectEditor/ObjectPropertiesEditor";
import OperatorAppsPanel from "../operator/OperatorAppsPanel";
import SecurityUsersPanel from "../security/SecurityUsersPanel";
import SecurityUserInspector from "../security/SecurityUserInspector";
import SecurityRolesPanel from "../security/SecurityRolesPanel";
import SecurityRoleInspector from "../security/SecurityRoleInspector";
import SecurityRootPanel from "../security/SecurityRootPanel";
import SystemFolderListPanel from "../platform/SystemFolderListPanel";
import FederationPeersPanel from "../federation/FederationPeersPanel";
import TenantsPanel from "../platform/TenantsPanel";
import { isBlueprintsPath } from "../../types/blueprints";
import type { ObjectSummary } from "../../types";
import {
  isOperatorAppChildPath,
} from "../../utils/operator/operatorAppsPath";
import { isSecurityUserPath, isSecurityUsersRoot } from "../../utils/security/securityUserPath";
import { isSecurityRolePath, isSecurityRolesRoot, isSecurityRoot } from "../../utils/security/securityRolePath";
import {
  isAlertRulePath,
  isAlertRulesRoot,
  isCorrelatorPath,
  isCorrelatorsRoot,
} from "../../utils/automation/automationPath";
import {
  isProcessProgramPath,
  isProcessProgramsRoot,
} from "../../utils/automation/processProgramPath";
import { isEventFilterPath, isEventFiltersRoot } from "../../utils/automation/eventFilterPath";
import { isAnalyticsTagDevice } from "../../utils/analytics/analyticsPath";
import AlertRuleInspector from "../automation/AlertRuleInspector";
import EventFilterInspector from "../automation/EventFilterInspector";
import AnalyticsTagInspector from "../analytics/AnalyticsTagInspector";
import AutomationRulesListPanel from "../automation/AutomationRulesListPanel";
import CorrelatorInspector from "../automation/CorrelatorInspector";
import ProcessProgramInspector from "../automation/ProcessProgramInspector";
import { isSpecializedEditorObject } from "../../utils/object/editorObject";
import { isSystemCatalogFolder } from "../../utils/platform/systemFolderConfig";
import { isPlatformSqlObjectPath } from "../../utils/platform/platformSqlPath";
import VisualGroupInspector from "../objectEditor/VisualGroupInspector";
import PlatformSqlObjectPanel from "../platform/PlatformSqlObjectPanel";
import ApplicationObjectPanel from "../platform/ApplicationObjectPanel";
import { isApplicationObjectPath } from "../../utils/platform/applicationPath";
import { isFederationRoot } from "../../utils/federation/federationPath";
import { isTenantsRoot } from "../../utils/platform/tenantPath";
import { APPLICATIONS_ROOT } from "../../utils/object/createObjectMode";

interface ExplorerViewProps {
  selectedPath: string | null;
  selectedObject: ObjectSummary | null;
  onOpenEditor: (path: string) => void;
  onOpenOperatorApp?: (path: string) => void;
  onDeleted: () => void;
  onSelectPath: (path: string) => void;
  onMembersChanged?: () => void;
  allObjects?: ObjectSummary[];
  canConfigure: boolean;
  /** Global admin — tenants, federation, system. */
  isPlatformAdmin: boolean;
  /** Global admin or tenant-admin — Security users/roles / ACL. */
  canManageTenantSecurity: boolean;
  onCreateApplication?: () => void;
  onCreateInFolder?: (parentPath: string) => void;
  showBackToTree?: boolean;
  onBackToTree?: () => void;
}

export default function ExplorerView({
  selectedPath,
  selectedObject,
  onOpenEditor,
  onOpenOperatorApp,
  onDeleted,
  onSelectPath,
  onMembersChanged,
  allObjects = [],
  canConfigure,
  isPlatformAdmin,
  canManageTenantSecurity,
  onCreateApplication,
  onCreateInFolder,
  showBackToTree = false,
  onBackToTree,
}: ExplorerViewProps) {
  const { t } = useTranslation(["explorer", "common"]);

  if (!selectedPath) {
    return <div className="inspector-empty">{t("explorer:empty.selectObject")}</div>;
  }

  const isOperatorAppChild = isOperatorAppChildPath(selectedPath);
  const isUsersRoot = isSecurityUsersRoot(selectedPath);
  const isUserObject = isSecurityUserPath(selectedPath);
  const isSecurityFolder = isSecurityRoot(selectedPath);
  const isRolesRoot = isSecurityRolesRoot(selectedPath);
  const isRoleObject = isSecurityRolePath(selectedPath);
  const isAlertRule = isAlertRulePath(selectedPath);
  const isCorrelator = isCorrelatorPath(selectedPath);
  const isProcessProgram = isProcessProgramPath(selectedPath);
  const isAlertRulesFolder = isAlertRulesRoot(selectedPath);
  const isCorrelatorsFolder = isCorrelatorsRoot(selectedPath);
  const isProcessProgramsFolder = isProcessProgramsRoot(selectedPath);
  const isEventFilter = isEventFilterPath(selectedPath);
  const isEventFiltersFolder = isEventFiltersRoot(selectedPath);
  const isAnalyticsTag = isAnalyticsTagDevice(selectedObject);
  const isFederation = isFederationRoot(selectedPath);
  const isTenants = isTenantsRoot(selectedPath);
  const isCatalogFolder = isSystemCatalogFolder(selectedPath, selectedObject?.type);
  const isPlatformSqlObject = isPlatformSqlObjectPath(selectedPath);
  const isApplicationObject =
    selectedObject?.type === "APPLICATION"
    && isApplicationObjectPath(selectedPath);
  const opensInEditor = Boolean(
    selectedObject
    && isSpecializedEditorObject(selectedPath, selectedObject.type, selectedObject.templateId),
  );
  const isVisualGroup = selectedObject?.type === "VISUAL_GROUP";
  const hideToolbar =
    isOperatorAppChild
    ||     isUsersRoot
    || isUserObject
    || isSecurityFolder
    || isRolesRoot
    || isRoleObject
    || isAlertRule
    || isCorrelator
    || isProcessProgram
    || isAlertRulesFolder
    || isCorrelatorsFolder
    || isProcessProgramsFolder
    || isEventFilter
    || isEventFiltersFolder
    || isAnalyticsTag
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
          <Button className="explorer-back-btn" onClick={onBackToTree}>
            {t("explorer:mobile.backToTree")}
          </Button>
        </div>
      )}
      {!hideToolbar && opensInEditor && (
        <div className="explorer-toolbar">
          <Button onClick={() => onOpenEditor(selectedPath)}>
            {t("common:action.openInEditor")}
          </Button>
          <span className="hint">
            {isBlueprintsPath(selectedPath)
              ? t("common:hint.modelFullDefinition")
              : t("common:hint.openEditorButton")}
          </span>
        </div>
      )}
      {!hideToolbar && !opensInEditor && (
        <div className="explorer-toolbar explorer-toolbar-hint-only">
          <span className="hint">{t("common:hint.objectPropertiesTabs")}</span>
        </div>
      )}

      {isOperatorAppChild ? (
        <OperatorAppsPanel canManage={canConfigure} selectedPath={selectedPath} />
      ) : isUsersRoot ? (
        <SecurityUsersPanel canManage={canManageTenantSecurity} onSelectUser={onSelectPath} />
      ) : isSecurityFolder ? (
        <SecurityRootPanel canManage={canManageTenantSecurity} onSelectPath={onSelectPath} />
      ) : isUserObject ? (
        <SecurityUserInspector key={selectedPath} path={selectedPath} canManage={canManageTenantSecurity} onDeleted={onDeleted} />
      ) : isRolesRoot ? (
        <SecurityRolesPanel canManage={canManageTenantSecurity} onSelectRole={onSelectPath} />
      ) : isRoleObject ? (
        <SecurityRoleInspector key={selectedPath} path={selectedPath} canManage={canManageTenantSecurity} onDeleted={onDeleted} />
      ) : isAlertRulesFolder ? (
        <AutomationRulesListPanel kind="alert-rules" canManage={canConfigure} onSelectPath={onSelectPath} />
      ) : isCorrelatorsFolder ? (
        <AutomationRulesListPanel kind="correlators" canManage={canConfigure} onSelectPath={onSelectPath} />
      ) : isAlertRule ? (
        <AlertRuleInspector key={selectedPath} path={selectedPath} canManage={canConfigure} />
      ) : isCorrelator ? (
        <CorrelatorInspector key={selectedPath} path={selectedPath} canManage={canConfigure} />
      ) : isProcessProgram ? (
        <ProcessProgramInspector key={selectedPath} path={selectedPath} canManage={canConfigure} />
      ) : isEventFilter ? (
        <EventFilterInspector key={selectedPath} path={selectedPath} canManage={canConfigure} />
      ) : isAnalyticsTag ? (
        <AnalyticsTagInspector key={selectedPath} path={selectedPath} canManage={canConfigure} />
      ) : isFederation ? (
        <FederationPeersPanel canManage={isPlatformAdmin} />
      ) : isTenants ? (
        <TenantsPanel canManage={isPlatformAdmin} onSelectPath={onSelectPath} />
      ) : isPlatformSqlObject ? (
        <PlatformSqlObjectPanel key={selectedPath} path={selectedPath} onOpenEditor={onOpenEditor} />
      ) : isVisualGroup ? (
        <VisualGroupInspector
          key={selectedPath}
          path={selectedPath}
          canManage={canConfigure}
          allObjects={allObjects}
          onSelectPath={onSelectPath}
          onMembersChanged={onMembersChanged}
        />
      ) : isApplicationObject ? (
        <ApplicationObjectPanel
          key={selectedPath}
          path={selectedPath}
          displayName={selectedObject?.displayName}
          description={selectedObject?.description}
          canManage={canConfigure}
        />
      ) : isCatalogFolder ? (
        <SystemFolderListPanel
          key={selectedPath}
          folderPath={selectedPath}
          folderType={selectedObject?.type}
          folderDisplayName={selectedObject?.displayName}
          folderDescription={selectedObject?.description}
          onSelectPath={onSelectPath}
          onOpenEditor={onOpenEditor}
          onOpenOperatorApp={onOpenOperatorApp}
          onCreateApplication={
            selectedPath === APPLICATIONS_ROOT && canConfigure ? onCreateApplication : undefined
          }
          onCreate={
            selectedPath !== APPLICATIONS_ROOT && canConfigure && onCreateInFolder
              ? () => onCreateInFolder(selectedPath)
              : undefined
          }
          canManage={canConfigure}
        />
      ) : (
        <ObjectPropertiesEditor
          key={selectedPath}
          path={selectedPath}
          embedded
          canManage={canConfigure}
          canManageAcl={canManageTenantSecurity}
          onDeleted={onDeleted}
          onSelectPath={onSelectPath}
        />
      )}
    </div>
  );
}
