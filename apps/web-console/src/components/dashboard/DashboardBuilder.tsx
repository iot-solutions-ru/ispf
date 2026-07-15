import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchObjects,
  fetchDashboard,
  saveDashboardLayout,
  saveDashboardTitle,
  saveDashboardRefreshInterval,
} from "../../api";
import type { DashboardLayout, DashboardLayoutPreset, DashboardView, DashboardWidget, WidgetType } from "../../types/dashboard";
import { layoutToJson, newWidget, resolveDashboardLayout } from "../../types/dashboard";
import WidgetPalette from "./WidgetPalette";
import { WIDGET_SAMPLE_PATHS } from "./widgetSamples";
import {
  DashboardProvider,
  mergeSession,
  type DashboardSession,
  type OpenDashboardOptions,
} from "./DashboardContext";
import DashboardGrid from "./DashboardGrid";
import DashboardModal from "./DashboardModal";
import { DashboardEditorProvider, type ContainerActiveSlots } from "./DashboardEditorContext";
import {
  findWidgetInLayout,
  getChildrenAtSlot,
  removeWidgetFromLayout,
  reparentWidgetToSlot,
  resolveAddTargetSlot,
  setChildrenAtSlot,
  updateWidgetInLayout,
  defaultChildPosition,
} from "./widgetLayoutTree";
import DashboardSettingsPanel from "./DashboardSettingsPanel";
import DashboardRulesPanel from "./DashboardRulesPanel";
import WidgetEditorPanel from "./WidgetEditorPanel";
import HaystackBindDialog from "./HaystackBindDialog";
import { nextWidgetZIndex } from "./widgetLayerUtils";
import { useDashboardContextSync } from "../../hooks/useDashboardContextSync";
import { useMobileLayout } from "../../hooks/useMobileLayout";
import { getStoredSession } from "../../auth/session";
import {
  cacheDashboardView,
  readCachedDashboardView,
} from "../../utils/operatorOfflineCache";
import { applyLayoutPreset, isVideoWallPreset } from "./dashboardLayoutPresets";

interface DashboardBuilderProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
  operatorMode?: boolean;
  embeddedModal?: boolean;
  onNavigateDashboard?: (path: string, options?: OpenDashboardOptions) => void;
  onOpenDashboardModal?: (path: string, title?: string, options?: OpenDashboardOptions) => void;
  session?: DashboardSession;
  selection?: Record<string, string>;
  params?: Record<string, unknown>;
  onSessionChange?: (next: DashboardSession) => void;
  onSelectionChange?: (next: Record<string, string>) => void;
  onParamsChange?: (next: Record<string, unknown>) => void;
  onLayoutPresetChange?: (preset: DashboardLayoutPreset | undefined) => void;
  subDashboardDepth?: number;
}

interface ModalState {
  path: string;
  title: string;
  session: DashboardSession;
}

function isKeyboardEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return Boolean(
    target.closest(
      "input, textarea, select, option, [contenteditable=''], [contenteditable='true']"
    )
  );
}

type EditorSidePanel = "widget" | "settings" | "rules";

export default function DashboardBuilder({
  path,
  onClose,
  onOpenProperties,
  operatorMode = false,
  embeddedModal = false,
  onNavigateDashboard,
  onOpenDashboardModal,
  session,
  selection,
  params,
  onSessionChange,
  onSelectionChange,
  onParamsChange,
  onLayoutPresetChange,
  subDashboardDepth = 0,
}: DashboardBuilderProps) {
  const { t } = useTranslation(["dashboard", "common"]);
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<"view" | "edit">(operatorMode ? "view" : "view");
  const [draftLayout, setDraftLayout] = useState<DashboardLayout | null>(null);
  const [draftTitle, setDraftTitle] = useState<string | null>(null);
  const [draftRefreshMs, setDraftRefreshMs] = useState<number | null>(null);
  const [selectedWidgetId, setSelectedWidgetId] = useState<string | null>(null);
  const [draggingWidgetId, setDraggingWidgetId] = useState<string | null>(null);
  const [dropTargetSlotKey, setDropTargetSlotKey] = useState<string | null>(null);
  const [activeSlots, setActiveSlots] = useState<ContainerActiveSlots>({
    tabId: {},
    slideIndex: {},
    stepId: {},
  });
  const [editorSidePanel, setEditorSidePanel] = useState<EditorSidePanel>("widget");
  const [showJson, setShowJson] = useState(false);
  const [showHaystackBind, setShowHaystackBind] = useState(false);
  const [mobilePaletteOpen, setMobilePaletteOpen] = useState(false);
  const isMobileLayout = useMobileLayout();
  const [modalDashboard, setModalDashboard] = useState<ModalState | null>(null);
  const layoutRef = useRef<DashboardLayout>(resolveDashboardLayout(undefined));
  const selectedWidgetIdRef = useRef<string | null>(null);
  selectedWidgetIdRef.current = selectedWidgetId;

  const currentSession = useMemo<DashboardSession>(
    () =>
      session ?? {
        selection: selection ?? {},
        params: params ?? {},
        widgets: {},
      },
    [session, selection, params]
  );

  useDashboardContextSync({
    path,
    enabled: operatorMode && Boolean(onSessionChange),
    session: currentSession,
    onSessionChange: onSessionChange ?? (() => {}),
    updatedBy: getStoredSession()?.username,
  });

  useEffect(() => {
    if (!isMobileLayout || mode !== "edit") {
      setMobilePaletteOpen(false);
    }
  }, [isMobileLayout, mode]);

  const applySession = (patch?: OpenDashboardOptions): DashboardSession => {
    const next = mergeSession(currentSession, patch);
    if (onSessionChange) {
      onSessionChange(next);
    } else {
      if (patch?.selection) {
        onSelectionChange?.(next.selection);
      }
      if (patch?.params) {
        onParamsChange?.(next.params);
      }
    }
    return next;
  };

  const handleNavigateDashboard = (targetPath: string, options?: OpenDashboardOptions) => {
    // Apply selection on the *target* dashboard via onNavigateDashboard options —
    // do not merge into the source session (that used to stick the previous device).
    if (!embeddedModal) {
      setModalDashboard(null);
    }
    onNavigateDashboard?.(targetPath, options);
  };

  const handleOpenDashboardModal = (
    targetPath: string,
    title?: string,
    options?: OpenDashboardOptions
  ) => {
    const nextSession = applySession(options);
    if (embeddedModal) {
      onOpenDashboardModal?.(targetPath, title, options);
      return;
    }
    setModalDashboard({
      path: targetPath,
      title: title?.trim() || targetPath.split(".").pop() || targetPath,
      session: nextSession,
    });
  };

  const handleCloseDashboardModal = useCallback(() => {
    if (embeddedModal) {
      onClose?.();
      return;
    }
    setModalDashboard(null);
  }, [embeddedModal, onClose]);

  const dashboard = useQuery({
    queryKey: ["dashboard", path],
    queryFn: async () => {
      try {
        const data = await fetchDashboard(path);
        if (operatorMode) {
          cacheDashboardView(path, data);
        }
        return data;
      } catch (error) {
        if (operatorMode) {
          const cached = readCachedDashboardView(path);
          if (cached) {
            return cached;
          }
        }
        throw error;
      }
    },
  });

  const objects = useQuery({
    queryKey: ["objects", "dashboard-bindings"],
    queryFn: () => fetchObjects(undefined, false),
  });

  const layout = useMemo(() => {
    if (draftLayout) return draftLayout;
    return resolveDashboardLayout(dashboard.data);
  }, [dashboard.data, draftLayout]);

  layoutRef.current = layout;

  useEffect(() => {
    onLayoutPresetChange?.(layout.layoutPreset);
  }, [layout.layoutPreset, onLayoutPresetChange]);

  useEffect(() => {
    setDraftLayout(null);
    setDraftTitle(null);
    setDraftRefreshMs(null);
    setSelectedWidgetId(null);
    setEditorSidePanel("widget");
  }, [path]);

  const title = draftTitle ?? dashboard.data?.title ?? path;
  const refreshIntervalMs =
    draftRefreshMs ?? dashboard.data?.refreshIntervalMs ?? 5000;
  const dirty =
    draftLayout !== null ||
    draftRefreshMs !== null ||
    (draftTitle !== null && draftTitle !== dashboard.data?.title);

  const bindingObjects = useMemo(
    () =>
      (objects.data ?? [])
        .filter((obj) => obj.variableNames.length > 0)
        .map((obj) => ({
          path: obj.path,
          displayName: obj.displayName,
          variableNames: obj.variableNames,
        })),
    [objects.data]
  );

  const dashboardObjects = useMemo(
    () =>
      (objects.data ?? [])
        .filter((obj) => obj.type === "DASHBOARD")
        .map((obj) => ({ path: obj.path, displayName: obj.displayName })),
    [objects.data]
  );

  const reportObjects = useMemo(
    () =>
      (objects.data ?? [])
        .filter((obj) => obj.type === "REPORT")
        .map((obj) => ({ path: obj.path, displayName: obj.displayName })),
    [objects.data]
  );

  const selectedWidget = useMemo(() => {
    if (!selectedWidgetId) return null;
    return findWidgetInLayout(layout, selectedWidgetId)?.widget ?? null;
  }, [layout, selectedWidgetId]);

  const saveMutation = useMutation({
    mutationFn: async (snapshot: {
      layout: DashboardLayout;
      title: string | null;
      refreshMs: number | null;
    }) => {
      await queryClient.cancelQueries({ queryKey: ["dashboard", path] });
      if (snapshot.title !== null) {
        await saveDashboardTitle(path, snapshot.title);
      }
      if (snapshot.refreshMs !== null) {
        await saveDashboardRefreshInterval(path, snapshot.refreshMs);
      }
      return saveDashboardLayout(path, layoutToJson(snapshot.layout));
    },
    onSuccess: (data, snapshot) => {
      const layoutJson = layoutToJson(snapshot.layout);
      const merged: DashboardView = {
        path,
        title: snapshot.title ?? data?.title ?? dashboard.data?.title ?? path,
        refreshIntervalMs:
          snapshot.refreshMs ?? data?.refreshIntervalMs ?? dashboard.data?.refreshIntervalMs ?? 5000,
        layout: snapshot.layout,
        layoutJson,
      };
      queryClient.setQueryData(["dashboard", path], merged);
      setDraftLayout(null);
      setDraftTitle(null);
      setDraftRefreshMs(null);
      queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
    },
  });

  const addWidget = (type: WidgetType) => {
    const slot = resolveAddTargetSlot(layout, selectedWidgetId, activeSlots);
    const siblings = getChildrenAtSlot(layout, slot);
    const position = defaultChildPosition(siblings, layout.columns);
    const widget = {
      ...newWidget(type, siblings.length),
      ...position,
      zIndex: nextWidgetZIndex(siblings),
      visible: true,
    };
    const next = setChildrenAtSlot(layout, slot, [...siblings, widget]);
    setDraftLayout(next);
    setSelectedWidgetId(widget.id);
    setEditorSidePanel("widget");
    setMode("edit");
  };

  const selectWidget = (widgetId: string | null) => {
    setSelectedWidgetId(widgetId);
    if (widgetId) {
      setEditorSidePanel("widget");
    }
  };

  const updateWidgets = (widgets: DashboardWidget[]) => {
    setDraftLayout(setChildrenAtSlot(layout, { kind: "root" }, widgets));
  };

  const updateWidget = (widget: DashboardWidget) => {
    setDraftLayout(updateWidgetInLayout(layout, widget));
  };

  const setChildrenAtSlotDraft = (slot: import("./widgetLayoutTree").WidgetSlotRef, children: DashboardWidget[]) => {
    setDraftLayout(setChildrenAtSlot(layout, slot, children));
  };

  const reparentToSlot = (widgetId: string, slot: import("./widgetLayoutTree").WidgetSlotRef) => {
    setDraftLayout(reparentWidgetToSlot(layout, widgetId, slot));
    setSelectedWidgetId(widgetId);
  };

  const updateLayoutSettings = (patch: Partial<DashboardLayout>) => {
    setDraftLayout({ ...layout, ...patch });
  };

  const handleApplyLayoutPreset = useCallback(
    (preset: DashboardLayoutPreset) => {
      setDraftLayout(applyLayoutPreset(preset, layout));
      setMode("edit");
      setEditorSidePanel("settings");
    },
    [layout]
  );

  const deleteWidget = useCallback(() => {
    const widgetId = selectedWidgetIdRef.current;
    if (!widgetId) return;
    const base = layoutRef.current;
    setDraftLayout(removeWidgetFromLayout(base, widgetId).layout);
    setSelectedWidgetId(null);
  }, []);

  const handleLayoutChange = (widgets: DashboardWidget[]) => {
    setDraftLayout((current) =>
      setChildrenAtSlot(current ?? layout, { kind: "root" }, widgets)
    );
  };

  const isEditorWorkspace = !operatorMode && mode === "edit";

  const editorContextValue = useMemo(
    () => ({
      enabled: isEditorWorkspace,
      layout,
      refreshIntervalMs,
      selectedWidgetId,
      draggingWidgetId,
      dropTargetSlotKey,
      activeSlots,
      selectWidget: selectWidget,
      setChildrenAtSlot: setChildrenAtSlotDraft,
      updateWidget,
      addWidget,
      deleteSelectedWidget: deleteWidget,
      setDraggingWidgetId,
      setDropTargetSlotKey,
      reparentToSlot,
      setActiveTab: (containerId: string, tabId: string) =>
        setActiveSlots((prev) => ({ ...prev, tabId: { ...prev.tabId, [containerId]: tabId } })),
      setActiveSlide: (containerId: string, slideIndex: number) =>
        setActiveSlots((prev) => ({
          ...prev,
          slideIndex: { ...prev.slideIndex, [containerId]: slideIndex },
        })),
      setActiveStep: (containerId: string, stepId: string) =>
        setActiveSlots((prev) => ({ ...prev, stepId: { ...prev.stepId, [containerId]: stepId } })),
    }),
    [
      activeSlots,
      addWidget,
      deleteWidget,
      draggingWidgetId,
      dropTargetSlotKey,
      isEditorWorkspace,
      layout,
      refreshIntervalMs,
      selectedWidgetId,
      updateWidget,
    ]
  );

  const editorSession = useMemo<DashboardSession>(
    () =>
      isEditorWorkspace
        ? mergeSession(currentSession, {
            selection: {
              device: WIDGET_SAMPLE_PATHS.device,
              order: WIDGET_SAMPLE_PATHS.device,
            },
            params: {
              clusterPath: WIDGET_SAMPLE_PATHS.devices,
              device: WIDGET_SAMPLE_PATHS.device,
              zoneLabel: t("editor.sampleZoneLabel"),
            },
          })
        : currentSession,
    [currentSession, isEditorWorkspace]
  );

  useEffect(() => {
    if (!isEditorWorkspace) {
      return;
    }
    document.body.classList.add("dashboard-editor-fullscreen");
    return () => {
      document.body.classList.remove("dashboard-editor-fullscreen");
    };
  }, [isEditorWorkspace]);

  useEffect(() => {
    if (!isEditorWorkspace) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Delete") return;
      if (isKeyboardEditableTarget(event.target)) return;
      if (!selectedWidgetIdRef.current) return;
      event.preventDefault();
      deleteWidget();
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [deleteWidget, isEditorWorkspace]);

  if (dashboard.isLoading) {
    return <div className="dashboard-shell loading">{t("loading")}</div>;
  }

  if (dashboard.error) {
    return (
      <div className="dashboard-shell error">
        {t("loadError", { message: (dashboard.error as Error).message })}
      </div>
    );
  }

  const videoWallClass =
    layout.layoutPreset && isVideoWallPreset(layout.layoutPreset)
      ? ` dashboard-shell--${layout.layoutPreset}`
      : "";

  return (
    <div
      className={`dashboard-shell ${operatorMode ? "operator-dashboard-shell" : ""}${
        embeddedModal ? " dashboard-shell--embedded-modal" : ""
      }${isEditorWorkspace ? " dashboard-shell--editor-fullscreen" : ""}${videoWallClass}`}
    >
      {!operatorMode && (
        <header className="dashboard-toolbar">
          <div>
            <div className="dashboard-kicker">{t("kicker")}</div>
            {mode === "edit" ? (
              <input
                className="dashboard-title-input"
                value={title}
                onChange={(e) => setDraftTitle(e.target.value)}
              />
            ) : (
              <h2>{title}</h2>
            )}
            <code className="path-code">{path}</code>
          </div>
          <div className="dashboard-toolbar-actions">
            <button
              type="button"
              className={`btn ${mode === "view" ? "primary" : ""}`}
              onClick={() => setMode("view")}
            >
              {t("mode.view")}
            </button>
            <button
              type="button"
              className={`btn ${mode === "edit" ? "primary" : ""}`}
              onClick={() => setMode("edit")}
            >
              {t("mode.edit")}
            </button>
            <button type="button" className="btn" onClick={() => setShowJson((v) => !v)}>
              {t("json")}
            </button>
            {mode === "edit" && (
              <>
                {isMobileLayout && (
                  <button
                    type="button"
                    className={`btn ${mobilePaletteOpen ? "primary" : ""}`}
                    onClick={() => setMobilePaletteOpen((open) => !open)}
                  >
                    {t("palette.title")}
                  </button>
                )}
                <button type="button" className="btn" onClick={() => setShowHaystackBind(true)}>
                  {t("haystackBind.open")}
                </button>
                <button
                  type="button"
                  className={`btn ${editorSidePanel === "settings" ? "primary" : ""}`}
                  onClick={() =>
                    setEditorSidePanel((panel) => (panel === "settings" ? "widget" : "settings"))
                  }
                >
                  {t("settings")}
                </button>
                <button
                  type="button"
                  className={`btn ${editorSidePanel === "rules" ? "primary" : ""}`}
                  onClick={() =>
                    setEditorSidePanel((panel) => (panel === "rules" ? "widget" : "rules"))
                  }
                >
                  {t("rules.tab")}
                </button>
              </>
            )}
            {onOpenProperties && (
              <button type="button" className="btn" onClick={onOpenProperties}>
                {t("common:action.properties")}
              </button>
            )}
            {dirty && (
              <button
                type="button"
                className="btn primary"
                disabled={saveMutation.isPending}
                onClick={() =>
                  saveMutation.mutate({
                    layout: layoutRef.current,
                    title: draftTitle,
                    refreshMs: draftRefreshMs,
                  })
                }
              >
                {t("common:action.save")}
              </button>
            )}
            {onClose && (
              <button type="button" className="btn" onClick={onClose}>
                {t("common:action.close")}
              </button>
            )}
          </div>
        </header>
      )}

      {!operatorMode && mode === "edit" && (
        <div
          className={`dashboard-editor-workspace${isMobileLayout ? " dashboard-editor-workspace--stacked" : ""}`}
        >
          {!isMobileLayout && (
            <aside className="dashboard-palette-sidebar">
              <WidgetPalette layout="sidebar" onAdd={addWidget} />
            </aside>
          )}

          {isMobileLayout && mobilePaletteOpen && (
            <div className="dashboard-palette-mobile-strip">
              <WidgetPalette onAdd={addWidget} />
            </div>
          )}

          <DashboardProvider
            session={editorSession}
            onSessionChange={onSessionChange}
            onSelectionChange={onSelectionChange}
            onParamsChange={onParamsChange}
            onNavigateDashboard={handleNavigateDashboard}
            onOpenDashboardModal={handleOpenDashboardModal}
            embeddedModal={embeddedModal}
            closeDashboardModal={handleCloseDashboardModal}
          >
            <DashboardEditorProvider value={editorContextValue}>
              <main className="dashboard-canvas dashboard-canvas--editor">
                <DashboardGrid
                  layout={layout}
                  refreshIntervalMs={refreshIntervalMs}
                  editable
                  selectedWidgetId={selectedWidgetId}
                  onSelectWidget={selectWidget}
                  onLayoutChange={handleLayoutChange}
                  subDashboardDepth={subDashboardDepth}
                />
                {showJson && (
                  <pre className="dashboard-json-panel">{layoutToJson(layout)}</pre>
                )}
              </main>
            </DashboardEditorProvider>
          </DashboardProvider>

          {editorSidePanel === "settings" ? (
            <DashboardSettingsPanel
              layout={layout}
              refreshIntervalMs={refreshIntervalMs}
              dashboardPath={path}
              onLayoutChange={updateLayoutSettings}
              onRefreshIntervalChange={setDraftRefreshMs}
              onApplyPreset={handleApplyLayoutPreset}
            />
          ) : editorSidePanel === "rules" ? (
            <DashboardRulesPanel path={path} widgets={layout.widgets} />
          ) : (
            <WidgetEditorPanel
              widget={selectedWidget}
              widgets={layout.widgets}
              objects={bindingObjects}
              dashboards={dashboardObjects}
              reports={reportObjects}
              onChange={updateWidget}
              onWidgetsChange={updateWidgets}
              onDelete={deleteWidget}
            />
          )}
        </div>
      )}

      {!isEditorWorkspace && (
        <div className="dashboard-body">
          <DashboardProvider
            operatorMode={operatorMode}
            session={currentSession}
            onSessionChange={onSessionChange}
            onSelectionChange={onSelectionChange}
            onParamsChange={onParamsChange}
            onNavigateDashboard={handleNavigateDashboard}
            onOpenDashboardModal={handleOpenDashboardModal}
            embeddedModal={embeddedModal}
            closeDashboardModal={handleCloseDashboardModal}
          >
            <main className="dashboard-canvas">
              <DashboardGrid
                layout={layout}
                refreshIntervalMs={refreshIntervalMs}
                editable={false}
                embeddedModal={embeddedModal}
                selectedWidgetId={null}
                subDashboardDepth={subDashboardDepth}
                contextWidgets={currentSession.widgets}
              />
              {!operatorMode && showJson && (
                <pre className="dashboard-json-panel">{layoutToJson(layout)}</pre>
              )}
            </main>
          </DashboardProvider>
        </div>
      )}
      {!embeddedModal && modalDashboard && (
        <DashboardModal
          path={modalDashboard.path}
          title={modalDashboard.title}
          session={modalDashboard.session}
          onSessionChange={onSessionChange}
          onSelectionChange={onSelectionChange}
          onParamsChange={onParamsChange}
          onNavigateDashboard={handleNavigateDashboard}
          onOpenDashboardModal={(nextPath, nextTitle, options) => {
            const nextSession = applySession(options);
            setModalDashboard({
              path: nextPath,
              title: nextTitle?.trim() || nextPath.split(".").pop() || nextPath,
              session: nextSession,
            });
          }}
          onClose={() => setModalDashboard(null)}
        />
      )}
      {showHaystackBind && (
        <HaystackBindDialog
          layout={layout}
          onApply={(nextLayout) => {
            setDraftLayout(nextLayout);
            setMode("edit");
          }}
          onClose={() => setShowHaystackBind(false)}
        />
      )}
    </div>
  );
}
