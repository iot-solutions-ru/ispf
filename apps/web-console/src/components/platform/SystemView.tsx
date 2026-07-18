import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import ClusterView from "./ClusterView";
import SystemMetricsView from "./SystemMetricsView";
import SystemSettingsView from "./SystemSettingsView";
import EventJournalPanel from "../operator/EventJournalPanel";
import FunctionInvokeJournalPanel from "../runtime/FunctionInvokeJournalPanel";
import BindingInvokeJournalPanel from "../runtime/BindingInvokeJournalPanel";
import PlatformBackupPanel from "./PlatformBackupPanel";
import AnalyticsFormulasPanel from "./AnalyticsFormulasPanel";
import PlatformChangeSetsPanel from "./PlatformChangeSetsPanel";
import PlatformSchedulesPanel from "./PlatformSchedulesPanel";
import SemanticExportPanel from "./SemanticExportPanel";
import SolutionCatalogPanel from "./SolutionCatalogPanel";
import { usePersistentTab } from "../../hooks/usePersistentTab";
import { usePublishAdminFocus } from "../../hooks/usePublishAdminFocus";
import type { AdminClientFocus } from "../../context/AdminFocusContext";

type SystemTab =
  | "metrics"
  | "cluster"
  | "settings"
  | "solutions"
  | "formulas"
  | "events"
  | "functions"
  | "bindings"
  | "changeSets"
  | "schedules"
  | "semanticExport"
  | "backup";

const SYSTEM_TABS: readonly SystemTab[] = [
  "metrics", "cluster", "settings", "solutions", "formulas", "events", "functions", "bindings", "changeSets", "schedules", "semanticExport", "backup",
];

const SYSTEM_TAB_IDS = SYSTEM_TABS as readonly string[];

export default function SystemView() {
  const { t } = useTranslation("system");
  const [tab, setTab] = usePersistentTab<SystemTab>("system", "metrics", SYSTEM_TABS);

  const tabs: { id: SystemTab; labelKey: string }[] = [
    { id: "metrics", labelKey: "tab.metrics" },
    { id: "cluster", labelKey: "tab.cluster" },
    { id: "settings", labelKey: "tab.settings" },
    { id: "solutions", labelKey: "tab.solutions" },
    { id: "formulas", labelKey: "tab.formulas" },
    { id: "events", labelKey: "tab.events" },
    { id: "functions", labelKey: "tab.functions" },
    { id: "bindings", labelKey: "tab.bindings" },
    { id: "changeSets", labelKey: "tab.changeSets" },
    { id: "schedules", labelKey: "tab.schedules" },
    { id: "semanticExport", labelKey: "tab.semanticExport" },
    { id: "backup", labelKey: "tab.backup" },
  ];

  const systemFocus = useMemo((): AdminClientFocus => {
    const tabHints: Record<string, string> = {
      metrics: "Platform metrics overview/diagnostics — explain gauges, pools, replica pressure",
      cluster: "Cluster health nodes — explain status/profile, suggest recovery checks",
      settings: "Runtime settings — explain integrations and env/restart impact",
      solutions: "Solution marketplace/catalog — explain install/activation of apps and packs",
      formulas: "Analytics formulas — draft/adapt CEL formula expressions",
      events: "Event journal — diagnose fires, draft alerts/bindings from samples",
      functions: "Function invoke journal — debug scripts and propose retries",
      bindings: "Binding invoke journal — fix CEL rules from failed/changed samples",
      changeSets: "Change sets — draft ops JSON and explain apply/preview",
      schedules: "App schedules — draft interval invoke actions",
      semanticExport: "Haystack/Brick semantic export — choose rootPath/includePoints",
      backup: "Platform backup export/import JSON",
    };
    return {
      surface: "system",
      priority: 55,
      detail: {
        screenTitle: "System (Система)",
        systemTab: tab,
        availableSystemTabs: [...SYSTEM_TAB_IDS],
        screenHint: tabHints[tab] ?? `System console tab: ${tab}`,
        helpIntents: ["explainScreen", "gatherLiveData", "draftConfig", "createEntity"],
      },
    };
  }, [tab]);
  usePublishAdminFocus("system-view", systemFocus, true);

  return (
    <main className="main system-view">
      <header className="system-metrics-header">
        <div>
          <h2>{t("title")}</h2>
          <p className="op-muted">{t("subtitle")}</p>
        </div>
      </header>

      <div className="tabs-scroll">
        <nav className="tabs" aria-label={t("tab.tabsAria")}>
          {tabs.map((item) => (
            <button
              key={item.id}
              type="button"
              className={tab === item.id ? "active" : ""}
              onClick={() => setTab(item.id)}
            >
              {t(item.labelKey)}
            </button>
          ))}
        </nav>
      </div>

      {tab === "metrics" && <SystemMetricsView embedded />}
      {tab === "cluster" && <ClusterView />}
      {tab === "settings" && <SystemSettingsView />}
      {tab === "solutions" && <SolutionCatalogPanel />}
      {tab === "formulas" && <AnalyticsFormulasPanel />}
      {tab === "events" && (
        <EventJournalPanel limit={100} showFilters objectPathFilter="" publishAdminFocus />
      )}
      {tab === "functions" && <FunctionInvokeJournalPanel limit={100} showFilters publishAdminFocus />}
      {tab === "bindings" && <BindingInvokeJournalPanel limit={100} showFilters publishAdminFocus />}
      {tab === "changeSets" && <PlatformChangeSetsPanel />}
      {tab === "schedules" && <PlatformSchedulesPanel />}
      {tab === "semanticExport" && <SemanticExportPanel />}
      {tab === "backup" && <PlatformBackupPanel />}
    </main>
  );
}
