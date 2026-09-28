import { lazy, Suspense } from "react";
import type { EditorTab } from "../types";
import type { DashboardSession } from "../components/dashboard/useDashboardContext";
import { isBlueprintsPath } from "../types/blueprints";

const ReportBuilder = lazy(() => import("../components/report/ReportBuilder"));
const WorkflowBuilder = lazy(() => import("../components/workflow/WorkflowBuilder"));
const DashboardBuilder = lazy(() => import("../components/dashboard/DashboardBuilder"));
const DataSourceEditor = lazy(() => import("../components/platform/DataSourceEditor"));
const MigrationEditor = lazy(() => import("../components/platform/MigrationEditor"));
const SqlBindingEditor = lazy(() => import("../components/platform/SqlBindingEditor"));
const ScheduleEditor = lazy(() => import("../components/platform/ScheduleEditor"));
const MimicEditorPanel = lazy(() => import("../components/scada/MimicEditorPanel"));
const BlueprintEditorPanel = lazy(() => import("../components/platform/BlueprintEditorPanel"));
const ApplicationEditorPanel = lazy(() => import("../components/platform/ApplicationEditorPanel"));

export function LazyFallback() {
  return <div className="loading" />;
}

export interface EditorWorkspaceProps {
  editor: EditorTab;
  canConfigure: boolean;
  dashboardSession?: DashboardSession;
  onClose: (tabId: string) => void;
  onOpenProperties: (path: string) => void;
  onSelectObjectPath: (path: string) => void;
  onOpenEditor: (path: string) => void;
  onDashboardSessionChange: (tabId: string, next: DashboardSession) => void;
  /** Blueprint editor navigates within its own tree and also moves the explorer selection. */
  onSelectBlueprintPath: (path: string) => void;
  /** Object currently highlighted in the explorer tree. */
  explorerPath?: string | null;
}

/**
 * Renders the editor that matches the active workspace tab. Extracted from `App.tsx`
 * (F-04 code-analysis follow-up) so the shell only owns tab state and layout.
 */
export default function EditorWorkspace({
  editor,
  canConfigure,
  dashboardSession,
  onClose,
  onOpenProperties,
  onSelectObjectPath,
  onOpenEditor,
  onDashboardSessionChange,
  onSelectBlueprintPath,
  explorerPath,
}: EditorWorkspaceProps) {
  const close = () => onClose(editor.id);
  const openProperties = () => onOpenProperties(editor.path);

  return (
    <main className="main editor-main dashboard-main">
      <Suspense fallback={<LazyFallback />}>
        {renderEditor()}
      </Suspense>
    </main>
  );

  function renderEditor() {
    switch (editor.objectType) {
      case "DASHBOARD":
        return (
          <DashboardBuilder
            path={editor.path}
            onClose={close}
            onOpenProperties={openProperties}
            onSelectObjectPath={onSelectObjectPath}
            session={dashboardSession}
            onSessionChange={(next) => onDashboardSessionChange(editor.id, next)}
            onNavigateDashboard={onOpenEditor}
          />
        );
      case "REPORT":
        return <ReportBuilder path={editor.path} onClose={close} onOpenProperties={openProperties} />;
      case "WORKFLOW":
        return <WorkflowBuilder path={editor.path} onClose={close} onOpenProperties={openProperties} />;
      default:
        break;
    }
    // Anything under the blueprints subtree opens in the blueprint editor regardless of type
    // (same precedence as the original App.tsx chain).
    if (editor.objectType === "BLUEPRINT" || isBlueprintsPath(editor.path)) {
      return (
        <BlueprintEditorPanel
          selectedPath={editor.path}
          canManage={canConfigure}
          title={editor.title}
          onClose={close}
          onSelectPath={onSelectBlueprintPath}
          explorerPath={explorerPath}
        />
      );
    }
    switch (editor.objectType) {
      case "DATA_SOURCE":
        return <DataSourceEditor path={editor.path} onClose={close} onOpenProperties={openProperties} />;
      case "MIGRATION":
        return <MigrationEditor path={editor.path} onClose={close} onOpenProperties={openProperties} />;
      case "BINDING":
        return <SqlBindingEditor path={editor.path} onClose={close} onOpenProperties={openProperties} />;
      case "SCHEDULE":
        return <ScheduleEditor path={editor.path} onClose={close} onOpenProperties={openProperties} />;
      case "MIMIC":
        return <MimicEditorPanel path={editor.path} title={editor.title} onClose={close} />;
      case "APPLICATION":
        return (
          <ApplicationEditorPanel
            path={editor.path}
            title={editor.title}
            onClose={close}
            onOpenProperties={openProperties}
            canManage={canConfigure}
          />
        );
      default:
        return null;
    }
  }
}
