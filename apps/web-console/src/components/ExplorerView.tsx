import { useState } from "react";
import { useTranslation } from "react-i18next";
import ObjectPropertiesEditor from "./ObjectPropertiesEditor";
import DriverWriteDialog from "./DriverWriteDialog";
import OperatorAppsPanel from "./OperatorAppsPanel";
import SecurityUsersPanel from "./SecurityUsersPanel";
import SecurityUserInspector from "./SecurityUserInspector";
import SecurityRolesPanel from "./SecurityRolesPanel";
import SecurityRoleInspector from "./SecurityRoleInspector";
import SecurityRootPanel from "./SecurityRootPanel";
import SystemFolderListPanel from "./SystemFolderListPanel";
import FederationPeersPanel from "./FederationPeersPanel";
import TenantsPanel from "./TenantsPanel";
import { isBlueprintsPath } from "../types/blueprints";
import type { ObjectSummary } from "../types";
import {
  isOperatorAppChildPath,
} from "../utils/operatorAppsPath";
import { isSecurityUserPath, isSecurityUsersRoot } from "../utils/securityUserPath";
import { isSecurityRolePath, isSecurityRolesRoot, isSecurityRoot } from "../utils/securityRolePath";
import {
  isAlertRulePath,
  isAlertRulesRoot,
  isCorrelatorPath,
  isCorrelatorsRoot,
} from "../utils/automationPath";
import {
  isProcessProgramPath,
  isProcessProgramsRoot,
} from "../utils/processProgramPath";
import { isEventFilterPath, isEventFiltersRoot } from "../utils/eventFilterPath";
import { isAnalyticsTagDevice } from "../utils/analyticsPath";
import AlertRuleInspector from "./automation/AlertRuleInspector";
import EventFilterInspector from "./automation/EventFilterInspector";
import AnalyticsTagInspector from "./analytics/AnalyticsTagInspector";
import AutomationRulesListPanel from "./automation/AutomationRulesListPanel";
import CorrelatorInspector from "./automation/CorrelatorInspector";
import ProcessProgramInspector from "./automation/ProcessProgramInspector";
import { isSpecializedEditorObject } from "../utils/editorObject";
import { isSystemCatalogFolder } from "../utils/systemFolderConfig";
import { isPlatformSqlObjectPath } from "../utils/platformSqlPath";
import VisualGroupInspector from "./VisualGroupInspector";
import PlatformSqlObjectPanel from "./platform/PlatformSqlObjectPanel";
import ApplicationObjectPanel from "./platform/ApplicationObjectPanel";
import { isApplicationObjectPath } from "../utils/applicationPath";
import { isFederationRoot } from "../utils/federationPath";
import { isTenantsRoot } from "../utils/tenantPath";
import { APPLICATIONS_ROOT } from "../utils/createObjectMode";

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
  isPlatformAdmin: boolean;
  onCreateApplication?: () => void;
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
  onCreateApplication,
  showBackToTree = false,
  onBackToTree,
}: ExplorerViewProps) {
  const { t } = useTranslation(["explorer", "common"]);
  const [driverWriteOpen, setDriverWriteOpen] = useState(false);

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
  const isDevice = selectedObject?.type === "DEVICE";
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
          <button type="button" className="btn explorer-back-btn" onClick={onBackToTree}>
            {t("explorer:mobile.backToTree")}
          </button>
        </div>
      )}
      {!hideToolbar && opensInEditor && (
        <div className="explorer-toolbar">
          <button type="button" className="btn" onClick={() => onOpenEditor(selectedPath)}>
            {t("common:action.openInEditor")}
          </button>
          <span className="hint">
            {isBlueprintsPath(selectedPath)
              ? t("common:hint.modelFullDefinition")
              : t("common:hint.openEditorButton")}
          </span>
        </div>
      )}
      {!hideToolbar && !opensInEditor && (
        <div className="explorer-toolbar explorer-toolbar-hint-only">
          {isDevice && canConfigure && (
            <button
              type="button"
              className="btn"
              onClick={() => setDriverWriteOpen(true)}
            >
              {t("explorer:driver.writePoint")}
            </button>
          )}
          <span className="hint">{t("common:hint.objectPropertiesTabs")}</span>
        </div>
      )}

      {isOperatorAppChild ? (
        <OperatorAppsPanel canManage={canConfigure} selectedPath={selectedPath} />
      ) : isUsersRoot ? (
        <SecurityUsersPanel canManage={isPlatformAdmin} onSelectUser={onSelectPath} />
      ) : isSecurityFolder ? (
        <SecurityRootPanel canManage={isPlatformAdmin} onSelectPath={onSelectPath} />
      ) : isUserObject ? (
        <SecurityUserInspector key={selectedPath} path={selectedPath} canManage={isPlatformAdmin} onDeleted={onDeleted} />
      ) : isRolesRoot ? (
        <SecurityRolesPanel canManage={isPlatformAdmin} onSelectRole={onSelectPath} />
      ) : isRoleObject ? (
        <SecurityRoleInspector key={selectedPath} path={selectedPath} canManage={isPlatformAdmin} onDeleted={onDeleted} />
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
          canManage={canConfigure}
        />
      ) : (
        <ObjectPropertiesEditor
          key={selectedPath}
          path={selectedPath}
          embedded
          canManage={canConfigure}
          canManageAcl={isPlatformAdmin}
          onDeleted={onDeleted}
        />
      )}
      {driverWriteOpen && isDevice && selectedPath && (
        <DriverWriteDialog
          devicePath={selectedPath}
          canManage={canConfigure}
          onClose={() => setDriverWriteOpen(false)}
        />
      )}
    </div>
  );
}
