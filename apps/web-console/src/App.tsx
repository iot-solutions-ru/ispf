import { useMemo, useState, useEffect, useRef, useCallback, lazy, Suspense } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchPlatformInfo, reorderObjectChildren } from "./api";
import { fetchOperatorApps } from "./api/operatorApps";
import { logout } from "./auth/login";
import {
  canManageTenantSecurity,
  getPrimaryRole,
  getStoredSession,
  isAdminSession,
  isConfiguratorSession,
  setStoredSession,
  type AuthSession,
} from "./auth/session";
import { SESSION_INVALID_EVENT, validateStoredSession } from "./auth/validateSession";
import {
  clearOidcCallbackParams,
  completeOidcLogin,
  fetchAuthConfig,
  readOidcCallback,
} from "./auth/oidc";
import {
  resolveInitialAppMode,
  shouldOpenOperatorShell,
} from "./auth/routing";
import { AuthLoadingCard, AuthLoginGate } from "./shell/AuthBootstrapViews";
import OperatorShellGate from "./shell/OperatorShellGate";
import type { EditorTab, ObjectType } from "./types";
import { resolveEditorObjectType, isSpecializedEditorObject } from "./utils/object/editorObject";
import { buildObjectTree } from "./utils/tree/tree";
import { objectTreeKey, type TreeRowSelection } from "./utils/tree/treeRowKey";
import { readSelectedPath, writeSelectedPath } from "./utils/tree/treeExpanded";
import {
  clearInvalidAdminPathFromUrl,
  resolveInitialAdminPath,
  syncAdminPathToUrl,
} from "./utils/platform/adminRouting";
import { useObjectWebSocket, useFederatedPathSubscription } from "./hooks/useObjectWebSocket";
import { useLazyObjectTree } from "./hooks/useLazyObjectTree";
import { useMobileLayout } from "./hooks/useMobileLayout";
import ObjectPropertiesEditor from "./components/objectEditor/ObjectPropertiesEditor";
import ObjectTree from "./components/objectEditor/ObjectTree";
import WorkspaceTabs, { type WorkspaceTabItem } from "./components/ui/WorkspaceTabs";
import CreateObjectDialog from "./components/objectEditor/CreateObjectDialog";
import {
  emptySession,
  mergeSession,
  type DashboardSession,
  type OpenDashboardOptions,
} from "./components/dashboard/DashboardContext";
import AgentChatStatusBar from "./components/agent/AgentChatStatusBar";
import AgentChatRunBootstrap from "./components/agent/AgentChatRunBootstrap";
import PlatformUpdateBanner from "./components/platform/PlatformUpdateBanner";
import ShellPreferences from "./components/ui/ShellPreferences";
import { AgentChatProvider } from "./context/AgentChatContext";
import { AdminFocusProvider } from "./context/AdminFocusContext";
import { AdminCopilotChatProvider } from "./context/AdminCopilotChatContext";
import AdminCopilotFab from "./components/agent/AdminCopilotFab";
import AdminWorkspaceFocusSync from "./components/agent/AdminWorkspaceFocusSync";
import CommandPalette from "./components/ui/CommandPalette";
import { useAgentRunStatus } from "./utils/agent/agentRunStatus";
import { ThemeProvider, useThemeController } from "./theme";
import { isBlueprintsPath } from "./types/blueprints";
import {
  isOperatorAppChildPath,
  resolveOperatorAppId as resolveRegistryOperatorAppId,
  resolveOperatorAppIdFromPath,
} from "./utils/operator/operatorAppsPath";
import { APPLICATIONS_ROOT } from "./utils/object/createObjectMode";

const SystemView = lazy(() => import("./components/platform/SystemView"));
const AiStudioPanel = lazy(() => import("./components/agent/AiStudioPanel"));
const ReportBuilder = lazy(() => import("./components/report/ReportBuilder"));
const WorkflowBuilder = lazy(() => import("./components/workflow/WorkflowBuilder"));
const DashboardBuilder = lazy(() => import("./components/dashboard/DashboardBuilder"));
const ExplorerView = lazy(() => import("./components/ui/ExplorerView"));
const DataSourceEditor = lazy(() => import("./components/platform/DataSourceEditor"));
const MigrationEditor = lazy(() => import("./components/platform/MigrationEditor"));
const SqlBindingEditor = lazy(() => import("./components/platform/SqlBindingEditor"));
const ScheduleEditor = lazy(() => import("./components/platform/ScheduleEditor"));
const MimicEditorPanel = lazy(() => import("./components/scada/MimicEditorPanel"));
const BlueprintEditorPanel = lazy(() => import("./components/platform/BlueprintEditorPanel"));
const ApplicationEditorPanel = lazy(() => import("./components/platform/ApplicationEditorPanel"));

function LazyFallback() {
  return <div className="loading" />;
}

let tabCounter = 1;

function AiStudioWorkspaceTabLabel() {
  const chat = useAgentRunStatus();
  const busy = chat.isPending;
  const { t } = useTranslation("shell");
  return (
    <span className="ai-studio-tab-label">
      {t("admin.tab.aiStudio")}
      {busy && <span className="tab-pending-dot" title={t("admin.agentBusyTitle")} />}
    </span>
  );
}

function useAppMode(session: AuthSession | null): ["admin" | "operator", (mode: "admin" | "operator") => void] {
  const [mode, setModeState] = useState<"admin" | "operator">(() => resolveInitialAppMode(session));
  const setMode = (next: "admin" | "operator") => {
    setModeState(next);
    const url = new URL(window.location.href);
    if (next === "operator") {
      url.searchParams.set("mode", "operator");
      if (!url.searchParams.get("app") && session?.autoStartApp) {
        url.searchParams.set("app", session.autoStartApp);
      }
    } else {
      url.searchParams.delete("mode");
      url.searchParams.delete("app");
      url.searchParams.delete("screen");
    }
    window.history.replaceState({}, "", url.toString());
  };
  return [mode, setMode];
}

export default function App() {
  const theme = useThemeController();
  return (
    <ThemeProvider value={theme}>
      <AppShell />
    </ThemeProvider>
  );
}

function AppShell() {
  const { t } = useTranslation(["shell", "common", "explorer"]);
  const [session, setSession] = useState<AuthSession | null>(() => getStoredSession());
  const [authBootstrapping, setAuthBootstrapping] = useState(
    () => Boolean(getStoredSession()?.token)
  );
  const [appMode, setAppMode] = useAppMode(session);
  const queryClient = useQueryClient();
  const [workspaceTab, setWorkspaceTab] = useState<"explorer" | string>("explorer");
  const [editorTabs, setEditorTabs] = useState<EditorTab[]>([]);
  const [dashboardSessions, setDashboardSessions] = useState<Record<string, DashboardSession>>({});
  const [propertiesTabPath, setPropertiesTabPath] = useState<string | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedPath, setSelectedPath] = useState<string | null>(() =>
    resolveInitialAdminPath(window.location.search, readSelectedPath())
  );
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [visibleRowKeys, setVisibleRowKeys] = useState<string[]>([]);
  const selectionAnchorRef = useRef<string | null>(null);
  const [oidcBootstrapping, setOidcBootstrapping] = useState(() => readOidcCallback() != null);

  useEffect(() => {
    const callback = readOidcCallback();
    if (!callback) {
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const config = await fetchAuthConfig();
        const result = await completeOidcLogin(config, callback.code, callback.state);
        if (cancelled) {
          return;
        }
        const nextSession: AuthSession = {
          token: result.accessToken,
          username: result.principal,
          displayName: result.principal,
          roles: result.roles,
        };
        setStoredSession(nextSession);
        setSession(nextSession);
        clearOidcCallbackParams();
      } catch (error) {
        if (!cancelled) {
          console.error(error);
        }
      } finally {
        if (!cancelled) {
          setOidcBootstrapping(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!getStoredSession()?.token) {
      setAuthBootstrapping(false);
      return;
    }
    let cancelled = false;
    void validateStoredSession().then((valid) => {
      if (cancelled) {
        return;
      }
      if (!valid) {
        setSession(null);
      }
      setAuthBootstrapping(false);
    });
    return () => {
      cancelled = true;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps -- validate stored token once on load

  useEffect(() => {
    const onSessionInvalid = () => setSession(null);
    window.addEventListener(SESSION_INVALID_EVENT, onSessionInvalid);
    return () => window.removeEventListener(SESSION_INVALID_EVENT, onSessionInvalid);
  }, []);

  const operatorAppsQuery = useQuery({
    queryKey: ["operator-apps"],
    queryFn: fetchOperatorApps,
    enabled: Boolean(session?.token),
  });

  useEffect(() => {
    if (!session?.autoStartEnabled || !session.autoStartApp) {
      return;
    }
    const apps = operatorAppsQuery.data;
    if (!apps) {
      return;
    }
    const resolved = resolveRegistryOperatorAppId(session.autoStartApp, apps);
    if (!apps.some((app) => app.appId === resolved)) {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    if (params.get("mode") === "admin" && isConfiguratorSession(session)) {
      return;
    }
    if (!params.get("app")) {
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", resolved);
      window.history.replaceState({}, "", url.toString());
      setAppMode("operator");
    }
  }, [session, setAppMode, operatorAppsQuery.data]);

  useEffect(() => {
    const fromUrl = searchParams.get("path");
    if (fromUrl && fromUrl !== selectedPath) {
      setSelectedPath(fromUrl);
    }
  }, [searchParams]);

  useEffect(() => {
    if (selectedPath) {
      writeSelectedPath(selectedPath);
      syncAdminPathToUrl(selectedPath);
    }
  }, [selectedPath]);

  const [showCreate, setShowCreate] = useState(false);
  const [createParentPath, setCreateParentPath] = useState<string | null>(null);
  const [createPresetType, setCreatePresetType] = useState<ObjectType | null>(null);
  const [treeFilter, setTreeFilter] = useState("");
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);
  const isMobileLayout = useMobileLayout();
  const [mobileExplorerPane, setMobileExplorerPane] = useState<"tree" | "detail">("tree");
  const sidebarRef = useRef<HTMLElement | null>(null);
  const explorerMainRef = useRef<HTMLElement | null>(null);

  useObjectWebSocket();
  useFederatedPathSubscription(selectedPath);

  const info = useQuery({ queryKey: ["info"], queryFn: fetchPlatformInfo });
  const { tree: lazyTree, objects: objectList, loadChildren, invalidateAll, treeLoadError } =
    useLazyObjectTree(Boolean(session));

  useEffect(() => {
    if (!selectedPath || !objectList.length) {
      return;
    }
    if (objectList.some((obj) => obj.path === selectedPath)) {
      return;
    }
    // Lazy tree refresh can briefly drop rows; don't jump to root during that window.
    const timer = window.setTimeout(() => {
      if (!objectList.some((obj) => obj.path === selectedPath)) {
        setSelectedPath("root");
        writeSelectedPath("root");
        clearInvalidAdminPathFromUrl();
      }
    }, 2000);
    return () => window.clearTimeout(timer);
  }, [objectList, selectedPath]);

  const reorderMutation = useMutation({
    mutationFn: ({ parentPath, orderedPaths }: { parentPath: string; orderedPaths: string[] }) =>
      reorderObjectChildren(parentPath, orderedPaths),
    onSuccess: () => void invalidateAll(),
  });

  const handleTreeReorder = useCallback(
    (parentPath: string, orderedPaths: string[]) => {
      void loadChildren(parentPath, true).then(() => {
        reorderMutation.mutate({ parentPath, orderedPaths });
      });
    },
    [loadChildren, reorderMutation],
  );

  const handleTreeLoadChildren = useCallback(
    (path: string) => {
      void loadChildren(path);
    },
    [loadChildren],
  );

  const tree = useMemo(() => {
    if (!treeFilter.trim()) {
      return lazyTree;
    }
    let list = objectList;
    const q = treeFilter.toLowerCase();
    const included = new Set<string>();
    const addAncestors = (path: string) => {
      let p: string | null = path;
      while (p) {
        included.add(p);
        const dot = p.lastIndexOf(".");
        p = dot === -1 ? null : p.slice(0, dot);
      }
    };
    for (const c of list) {
      if (c.path.toLowerCase().includes(q) || c.displayName.toLowerCase().includes(q)) {
        addAncestors(c.path);
        if (c.groupContextPath) {
          addAncestors(c.groupContextPath);
        }
        included.add(objectTreeKey(c));
      }
    }
    list = list.filter(
      (c) =>
        included.has(c.path)
        || included.has(objectTreeKey(c))
        || (c.groupContextPath != null && included.has(c.groupContextPath)),
    );
    return buildObjectTree(list);
  }, [objectList, lazyTree, treeFilter]);

  const selectPathInExplorer = (path: string) => {
    setSelectedPath(path);
    setSelectedKeys(new Set([path]));
    selectionAnchorRef.current = path;
    setWorkspaceTab("explorer");
    if (isMobileLayout) {
      setMobileExplorerPane("detail");
    }
  };

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== "k") {
        return;
      }
      const target = event.target;
      if (
        target instanceof HTMLElement
        && target.closest("textarea, [contenteditable=''], [contenteditable='true']")
      ) {
        return;
      }
      event.preventDefault();
      setCommandPaletteOpen((open) => !open);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const showObjectTreeSidebar =
    workspaceTab !== "system"
    && workspaceTab !== "ai-studio"
    && (!isMobileLayout || (workspaceTab === "explorer" && mobileExplorerPane === "tree"));

  const showExplorerMain =
    workspaceTab === "explorer"
    && (!isMobileLayout || mobileExplorerPane === "detail");

  const applyDashboardOpenOptions = (tabId: string, options?: OpenDashboardOptions) => {
    if (!options) {
      return;
    }
    setDashboardSessions((current) => ({
      ...current,
      [tabId]: mergeSession(current[tabId] ?? emptySession(), options),
    }));
  };

  const openEditor = (path: string, options?: OpenDashboardOptions) => {
    const ctx = objectList.find((c) => c.path === path);
    if (!isSpecializedEditorObject(path, ctx?.type, ctx?.templateId)) {
      setSelectedPath(path);
      setWorkspaceTab("explorer");
      if (isMobileLayout) {
        setMobileExplorerPane("detail");
      }
      return;
    }
    const existing = editorTabs.find((t) => t.path === path);
    if (existing) {
      if (existing.objectType === "DASHBOARD") {
        applyDashboardOpenOptions(existing.id, options);
      }
      setWorkspaceTab(existing.id);
      return;
    }
    const objectType = resolveEditorObjectType(path, ctx?.type, ctx?.templateId);
    const tab: EditorTab = {
      id: `editor-${tabCounter++}`,
      path,
      title: ctx?.displayName ?? path.split(".").pop() ?? path,
      objectType,
    };
    setEditorTabs((tabs) => [...tabs, tab]);
    if (objectType === "DASHBOARD") {
      applyDashboardOpenOptions(tab.id, options);
    }
    setWorkspaceTab(tab.id);
  };

  const handleTreeRowSelect = useCallback(
    (row: TreeRowSelection, event: { metaKey: boolean; shiftKey: boolean }) => {
      setSelectedPath(row.path);
      writeSelectedPath(row.path);
      syncAdminPathToUrl(row.path);
      setWorkspaceTab("explorer");
      if (isMobileLayout) {
        setMobileExplorerPane("detail");
      }

      if (event.shiftKey && selectionAnchorRef.current) {
        const start = visibleRowKeys.indexOf(selectionAnchorRef.current);
        const end = visibleRowKeys.indexOf(row.key);
        if (start !== -1 && end !== -1) {
          const [from, to] = start < end ? [start, end] : [end, start];
          setSelectedKeys(new Set(visibleRowKeys.slice(from, to + 1)));
          return;
        }
      }

      if (event.metaKey) {
        setSelectedKeys((current) => {
          const next = new Set(current);
          if (next.has(row.key)) {
            next.delete(row.key);
          } else {
            next.add(row.key);
          }
          return next;
        });
        selectionAnchorRef.current = row.key;
        return;
      }

      setSelectedKeys(new Set([row.key]));
      selectionAnchorRef.current = row.key;

      // Dashboards: single-click opens the HMI editor (properties remain via "Open properties").
      const ctx = objectList.find((c) => c.path === row.path);
      if (ctx?.type === "DASHBOARD") {
        openEditor(row.path);
      }
    },
    [isMobileLayout, visibleRowKeys, objectList, openEditor],
  );

  const openCreateApplication = () => {
    setCreatePresetType(null);
    setCreateParentPath(APPLICATIONS_ROOT);
    setShowCreate(true);
  };

  const closeEditor = (tabId: string) => {
    const tab = editorTabs.find((t) => t.id === tabId);
    setEditorTabs((tabs) => tabs.filter((t) => t.id !== tabId));
    setDashboardSessions((current) => {
      if (!(tabId in current)) {
        return current;
      }
      const next = { ...current };
      delete next[tabId];
      return next;
    });
    if (tab && propertiesTabPath === tab.path) {
      setPropertiesTabPath(null);
    }
    if (workspaceTab === tabId) {
      setWorkspaceTab("explorer");
    }
  };

  const activeEditor = editorTabs.find((t) => t.id === workspaceTab);
  const isSpecializedEditor = activeEditor != null && (
    activeEditor.objectType === "DASHBOARD"
    || activeEditor.objectType === "REPORT"
    || activeEditor.objectType === "WORKFLOW"
    || activeEditor.objectType === "BLUEPRINT"
    || activeEditor.objectType === "DATA_SOURCE"
    || activeEditor.objectType === "MIGRATION"
    || activeEditor.objectType === "BINDING"
    || activeEditor.objectType === "SCHEDULE"
    || activeEditor.objectType === "MIMIC"
    || activeEditor.objectType === "APPLICATION"
    || isBlueprintsPath(activeEditor.path)
  );
  const showPropertiesEditor =
    activeEditor != null
    && isSpecializedEditor
    && propertiesTabPath === activeEditor.path;

  useEffect(() => {
    if (!activeEditor || workspaceTab !== activeEditor.id) {
      return;
    }
    const ctx = objectList.find((c) => c.path === activeEditor.path);
    if (!isSpecializedEditorObject(activeEditor.path, ctx?.type, ctx?.templateId)) {
      setSelectedPath(activeEditor.path);
      setPropertiesTabPath(null);
      setWorkspaceTab("explorer");
      setEditorTabs((tabs) => tabs.filter((t) => t.id !== activeEditor.id));
    }
  }, [activeEditor, objectList, workspaceTab]);

  const parentForCreate = createParentPath ?? selectedPath ?? "root";
  const selectedObject = useMemo(
    () => objectList.find((obj) => obj.path === selectedPath) ?? null,
    [objectList, selectedPath]
  );
  const isAdmin = isAdminSession(session);
  const canConfigure = isConfiguratorSession(session);
  const canManageSecurity = canManageTenantSecurity(session);
  const primaryRole = getPrimaryRole(session);
  const showAiStudio = canConfigure && workspaceTab === "ai-studio";
  // Keep provider mounted for FAB even when Studio tab is hidden.
  const mountAgentChat = canConfigure;

  const handleLogout = async () => {
    await logout();
    setSession(null);
    queryClient.clear();
  };

  const selectOperatorApp = (appId: string) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set("mode", "operator");
        if (appId.trim()) {
          next.set("app", appId.trim());
        } else {
          next.delete("app");
        }
        next.delete("screen");
        next.delete("dashboard");
        next.delete("report");
        return next;
      },
      { replace: true }
    );
    setAppMode("operator");
  };

  const openOperatorAppFromPath = useCallback(
    (path: string) => {
      const appId = resolveOperatorAppIdFromPath(path, operatorAppsQuery.data ?? []);
      if (appId) {
        selectOperatorApp(appId);
      }
    },
    [operatorAppsQuery.data],
  );

  const handleLoggedIn = (next: AuthSession) => {
    setSession(next);
    queryClient.invalidateQueries();
    const params = new URLSearchParams(window.location.search);
    const urlApp = params.get("app");
    const apps = operatorAppsQuery.data;
    const autoStartResolved =
      next.autoStartEnabled && next.autoStartApp && apps
        ? resolveRegistryOperatorAppId(next.autoStartApp, apps)
        : null;
    const autoStartKnown =
      Boolean(autoStartResolved)
      && Boolean(apps?.some((app) => app.appId === autoStartResolved));
    if (autoStartKnown && autoStartResolved && !urlApp) {
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", autoStartResolved);
      url.searchParams.delete("screen");
      url.searchParams.delete("dashboard");
      window.history.replaceState({}, "", url.toString());
      setAppMode("operator");
    } else if (params.get("mode") === "operator" || urlApp) {
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      if (urlApp) {
        url.searchParams.set("app", urlApp);
      }
      url.searchParams.delete("screen");
      window.history.replaceState({}, "", url.toString());
      setAppMode("operator");
    } else if (!isConfiguratorSession(next)) {
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.delete("app");
      url.searchParams.delete("screen");
      window.history.replaceState({}, "", url.toString());
      setAppMode("operator");
    }
  };

  if (oidcBootstrapping || authBootstrapping) {
    return <AuthLoadingCard />;
  }

  if (!session?.token) {
    return <AuthLoginGate onLoggedIn={handleLoggedIn} />;
  }

  if (shouldOpenOperatorShell(session, appMode)) {
    return (
      <OperatorShellGate
        session={session}
        searchParams={searchParams}
        operatorApps={operatorAppsQuery.data}
        canConfigure={canConfigure}
        onSelectApp={selectOperatorApp}
        onSwitchAdmin={() => setAppMode("admin")}
        onLogout={() => void handleLogout()}
      />
    );
  }

  const shell = (
    <div
      className={`admin-shell${isMobileLayout ? " admin-shell--mobile" : ""}${
        mountAgentChat && !isMobileLayout ? " admin-shell--with-fab" : ""
      }`}
      data-testid="admin-shell"
    >
      <a href="#main-content" className="skip-link">
        {t("shell:admin.skipToContent")}
      </a>
      {canConfigure && <AgentChatRunBootstrap enabled />}
      {mountAgentChat && (
        <>
          <AdminWorkspaceFocusSync
            workspaceTab={workspaceTab}
            selectedPath={selectedPath}
            activeEditor={activeEditor}
            showPropertiesEditor={showPropertiesEditor}
          />
          {!isMobileLayout && <AdminCopilotFab />}
        </>
      )}
      <CommandPalette
        open={commandPaletteOpen}
        onClose={() => setCommandPaletteOpen(false)}
        objects={objectList}
        canConfigure={canConfigure}
        isAdmin={isAdmin}
        onSelectPath={selectPathInExplorer}
        onOpenWorkspace={(tab) => setWorkspaceTab(tab)}
        onCreate={
          canConfigure
            ? () => setCreateParentPath(selectedPath || "root.platform.devices")
            : undefined
        }
      />
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">ISPF</span>
          <div>
            <strong>{t("shell:admin.title")}</strong>
            <span className="brand-sub">
              {session.displayName} · {primaryRole ?? t("common:empty.dash")}
              {info.data?.version ? ` · v${info.data.version}` : ""}
              {info.data?.springBootVersion ? ` · Boot ${info.data.springBootVersion}` : ""}
              {info.data?.javaVersion ? ` · Java ${info.data.javaVersion}` : ""}
            </span>
          </div>
        </div>
        <div className="topbar-actions">
          <button
            type="button"
            className="btn"
            title={t("shell:commandPalette.openShortcut")}
            onClick={() => setCommandPaletteOpen(true)}
          >
            {t("shell:commandPalette.open")}
          </button>
          <ShellPreferences />
          <button type="button" className="btn" onClick={() => void handleLogout()}>
            {t("common:action.logout")}
          </button>
        </div>
      </header>

      {isAdmin && <PlatformUpdateBanner />}

      <WorkspaceTabs
        tabs={(
          [
            {
              id: "explorer",
              label: t("shell:admin.tab.explorer"),
              active: workspaceTab === "explorer",
              testId: "workspace-tab-explorer",
              onClick: () => {
                setWorkspaceTab("explorer");
                if (isMobileLayout) {
                  setMobileExplorerPane("tree");
                }
              },
            },
            ...(isAdmin
              ? [
                  {
                    id: "system",
                    label: t("shell:admin.tab.system"),
                    active: workspaceTab === "system",
                    testId: "workspace-tab-system",
                    onClick: () => setWorkspaceTab("system"),
                  },
                ]
              : []),
            ...(canConfigure
              ? [
                  {
                    id: "ai-studio",
                    label: <AiStudioWorkspaceTabLabel />,
                    title: t("shell:admin.tab.aiStudio"),
                    active: workspaceTab === "ai-studio",
                    testId: "workspace-tab-ai-studio",
                    onClick: () => setWorkspaceTab("ai-studio"),
                  },
                ]
              : []),
            ...editorTabs.map((tab) => ({
              id: tab.id,
              label: tab.title,
              active: workspaceTab === tab.id,
              onClick: () => setWorkspaceTab(tab.id),
              onClose: () => closeEditor(tab.id),
            })),
          ] as WorkspaceTabItem[]
        )}
      />

      <AgentChatStatusBar
        workspaceTab={workspaceTab}
        onOpenAiStudio={() => setWorkspaceTab("ai-studio")}
      />

      <div
        className={`workspace${isMobileLayout && workspaceTab === "explorer" ? ` workspace-mobile workspace-mobile-${mobileExplorerPane}` : ""}`}
      >
        {showObjectTreeSidebar && (
          <aside className="sidebar" ref={sidebarRef}>
            <div className="sidebar-head">
              <div className="sidebar-head-title">
                <h3>{t("shell:admin.treeTitle")}</h3>
                {canConfigure && (
                  <button
                    type="button"
                    className="btn small primary"
                    aria-label={t("shell:admin.createChild")}
                    title={t("shell:admin.createChild")}
                    onClick={() => {
                      setCreatePresetType(null);
                      setCreateParentPath(selectedPath ?? "root");
                      setShowCreate(true);
                    }}
                  >
                    + {t("common:action.create")}
                  </button>
                )}
              </div>
              <input
                type="search"
                placeholder={t("common:action.search")}
                value={treeFilter}
                onChange={(e) => setTreeFilter(e.target.value)}
              />
            </div>
            <div className="sidebar-body">
              {treeLoadError && <p className="sidebar-msg error">{treeLoadError}</p>}
              {!treeLoadError && objectList.length === 0 && (
                <p className="sidebar-msg">{t("shell:admin.treeLoading")}</p>
              )}
              {!treeLoadError && tree.length > 0 && (
                <ObjectTree
                  nodes={tree}
                  objects={objectList}
                  selectedPath={selectedPath}
                  selectedKeys={selectedKeys}
                  onRowSelect={handleTreeRowSelect}
                  onOpenEditor={openEditor}
                  onOpenOperatorApp={openOperatorAppFromPath}
                  canReorder={canConfigure && !treeFilter.trim()}
                  onReorder={handleTreeReorder}
                  onLoadChildren={handleTreeLoadChildren}
                  onVisibleRowKeysChange={setVisibleRowKeys}
                  bulkActions={
                    canConfigure
                      ? {
                          visibleRowKeys,
                          selectedKeys,
                          objects: objectList,
                          onSelectionChange: setSelectedKeys,
                          onDeleted: () => void invalidateAll(),
                          onMembersChanged: () => void invalidateAll(),
                          contextPath: selectedPath,
                          contextObjectType: selectedObject?.type,
                          onCreateChild: (parentPath) => {
                            setCreatePresetType(null);
                            setCreateParentPath(parentPath);
                            setShowCreate(true);
                          },
                          onCreateVisualGroup: (parentPath) => {
                            setCreatePresetType("VISUAL_GROUP");
                            setCreateParentPath(parentPath);
                            setShowCreate(true);
                          },
                        }
                      : undefined
                  }
                />
              )}
            </div>
          </aside>
        )}

        {showExplorerMain && (
          <main id="main-content" className="main explorer-main" ref={explorerMainRef}>
            <Suspense fallback={<LazyFallback />}>
              <ExplorerView
                selectedPath={selectedPath}
                selectedObject={selectedObject}
                onOpenEditor={openEditor}
                onOpenOperatorApp={openOperatorAppFromPath}
                onDeleted={() => {
                  setSelectedPath("root");
                  if (isMobileLayout) {
                    setMobileExplorerPane("tree");
                  }
                }}
                onSelectPath={selectPathInExplorer}
                onMembersChanged={() => void invalidateAll()}
                allObjects={objectList}
                canConfigure={canConfigure}
                isPlatformAdmin={isAdmin}
                canManageTenantSecurity={canManageSecurity}
                onCreateApplication={openCreateApplication}
                onCreateInFolder={(parentPath) => setCreateParentPath(parentPath)}
                showBackToTree={isMobileLayout}
                onBackToTree={() => setMobileExplorerPane("tree")}
              />
            </Suspense>
          </main>
        )}

        {workspaceTab === "system" && isAdmin && (
          <Suspense fallback={<LazyFallback />}>
            <SystemView />
          </Suspense>
        )}

        {showAiStudio && (
          <main className="main ai-studio-main">
            <Suspense fallback={<LazyFallback />}>
              <AiStudioPanel />
            </Suspense>
          </main>
        )}

        {activeEditor && workspaceTab === activeEditor.id && !showPropertiesEditor && (
          <main className="main editor-main dashboard-main">
            <Suspense fallback={<LazyFallback />}>
              {activeEditor.objectType === "DASHBOARD" ? (
                <DashboardBuilder
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                  onSelectObjectPath={selectPathInExplorer}
                  session={dashboardSessions[activeEditor.id]}
                  onSessionChange={(next) => {
                    setDashboardSessions((current) => ({
                      ...current,
                      [activeEditor.id]: next,
                    }));
                  }}
                  onNavigateDashboard={openEditor}
                />
              ) : activeEditor.objectType === "REPORT" ? (
                <ReportBuilder
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                />
              ) : activeEditor.objectType === "WORKFLOW" ? (
                <WorkflowBuilder
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                />
              ) : activeEditor.objectType === "BLUEPRINT" || isBlueprintsPath(activeEditor.path) ? (
                <BlueprintEditorPanel
                  selectedPath={activeEditor.path}
                  canManage={canConfigure}
                  title={activeEditor.title}
                  onClose={() => closeEditor(activeEditor.id)}
                  onSelectPath={(path) => {
                    setSelectedPath(path);
                    openEditor(path);
                  }}
                />
              ) : activeEditor.objectType === "DATA_SOURCE" ? (
                <DataSourceEditor
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                />
              ) : activeEditor.objectType === "MIGRATION" ? (
                <MigrationEditor
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                />
              ) : activeEditor.objectType === "BINDING" ? (
                <SqlBindingEditor
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                />
              ) : activeEditor.objectType === "SCHEDULE" ? (
                <ScheduleEditor
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                />
              ) : activeEditor.objectType === "MIMIC" ? (
                <MimicEditorPanel
                  path={activeEditor.path}
                  title={activeEditor.title}
                  onClose={() => closeEditor(activeEditor.id)}
                />
              ) : activeEditor.objectType === "APPLICATION" ? (
                <ApplicationEditorPanel
                  path={activeEditor.path}
                  title={activeEditor.title}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                  canManage={canConfigure}
                />
              ) : null}
            </Suspense>
          </main>
        )}

        {activeEditor && workspaceTab === activeEditor.id && showPropertiesEditor && (
          <main id="main-content" className="main editor-main">
            <ObjectPropertiesEditor
              key={activeEditor.path}
              path={activeEditor.path}
              canManage={canConfigure}
              canManageAcl={canManageSecurity}
              onSelectPath={selectPathInExplorer}
              onClose={() => {
                setPropertiesTabPath(null);
              }}
              onDeleted={() => {
                setSelectedPath("root");
                setPropertiesTabPath(null);
                closeEditor(activeEditor.id);
              }}
            />
          </main>
        )}
      </div>

      {showCreate && (
        <CreateObjectDialog
          parentPath={parentForCreate}
          presetType={createPresetType ?? undefined}
          onClose={() => {
            setShowCreate(false);
            setCreateParentPath(null);
            setCreatePresetType(null);
          }}
          onCreated={(path) => {
            setShowCreate(false);
            setCreateParentPath(null);
            setCreatePresetType(null);
            void invalidateAll();
            setSelectedPath(path);
            const stayInExplorer =
              isOperatorAppChildPath(path) || path.startsWith(`${APPLICATIONS_ROOT}.`);
            if (!stayInExplorer) {
              openEditor(path);
            }
          }}
        />
      )}
    </div>
  );

  if (!mountAgentChat) {
    return <AdminFocusProvider>{shell}</AdminFocusProvider>;
  }

  return (
    <AdminFocusProvider>
      <AgentChatProvider enabled>
        <AdminCopilotChatProvider enabled>{shell}</AdminCopilotChatProvider>
      </AgentChatProvider>
    </AdminFocusProvider>
  );
}
