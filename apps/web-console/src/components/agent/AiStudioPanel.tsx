import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import AiAgentChat from "./AiAgentChat";
import AiStudioBundleTab, { defaultBundleManifest } from "./AiStudioBundleTab";
import AiStudioPrefsTab from "./AiStudioPrefsTab";
import AiStudioStatusTab from "./AiStudioStatusTab";
import { useAgentRunStatus } from "../../utils/agent/agentRunStatus";
import { loadAiStudioPrefs, saveAiStudioPrefs } from "../../utils/agent/agentChatStorage";
import { usePublishAdminFocus } from "../../hooks/usePublishAdminFocus";
import type { AdminClientFocus } from "../../context/AdminFocusContext";

export type StudioMode = "agent" | "bundle" | "status" | "prefs";

export default function AiStudioPanel() {
  const { t } = useTranslation("ai");
  const { isPending } = useAgentRunStatus();
  const [mode, setMode] = useState<StudioMode>(() => loadAiStudioPrefs().lastTab);
  const [appId, setAppId] = useState(() => loadAiStudioPrefs().defaultAppId);
  const [prompt, setPrompt] = useState(
    "Create a minimal warehouse-style bundle with one migration table, one script function, and operatorUi with a single dashboard."
  );
  const [manifestText, setManifestText] = useState(defaultBundleManifest);
  const [validationText, setValidationText] = useState<string | null>(null);
  const [dryRunText, setDryRunText] = useState<string | null>(null);

  const backgroundBusy = isPending;

  useEffect(() => {
    const prefs = loadAiStudioPrefs();
    saveAiStudioPrefs({ ...prefs, lastTab: mode });
  }, [mode]);

  const tabs: StudioMode[] = ["agent", "bundle", "status", "prefs"];

  const studioFocus = useMemo((): AdminClientFocus => {
    return {
      surface: "ai-studio",
      priority: 60,
      detail: {
        screenTitle: "AI Studio (Platform Studio)",
        studioTab: mode,
        availableStudioTabs: tabs,
        screenHint:
          mode === "agent"
            ? "Build/plan agent chat for creating solutions (separate from Admin Copilot)"
            : mode === "bundle"
              ? "Bundle/manifest generation and validation"
              : mode === "status"
                ? "LLM provider status, context pack, agent tools catalog"
                : "Local AI Studio preferences and chat index",
      },
    };
  }, [mode]);
  usePublishAdminFocus("ai-studio-panel", studioFocus, true);

  return (
    <div className="ai-studio-panel">
      <header className="ai-studio-head">
        <h3>{t("studio.title")}</h3>
        {backgroundBusy && mode !== "agent" && (
          <div className="ai-studio-background-hint" role="status">
            <span className="ai-agent-status-bar-pulse" aria-hidden />
            {t("studio.backgroundBusy")}
          </div>
        )}
      </header>

      <nav className="tabs ai-studio-tabs" aria-label={t("studio.sectionsAria")}>
        {tabs.map((tab) => (
          <button
            key={tab}
            type="button"
            className={mode === tab ? "active" : ""}
            onClick={() => setMode(tab)}
          >
            {t(`studio.tab.${tab}`)}
            {tab === "agent" && backgroundBusy && (
              <span className="tab-pending-dot" title={t("agent.pendingTitle")} />
            )}
          </button>
        ))}
      </nav>

      <div className="ai-studio-body">
        {mode === "agent" && <AiAgentChat />}

        {mode === "bundle" && (
          <AiStudioBundleTab
            appId={appId}
            setAppId={setAppId}
            prompt={prompt}
            setPrompt={setPrompt}
            manifestText={manifestText}
            setManifestText={setManifestText}
            validationText={validationText}
            setValidationText={setValidationText}
            dryRunText={dryRunText}
            setDryRunText={setDryRunText}
          />
        )}

        {mode === "status" && <AiStudioStatusTab />}

        {mode === "prefs" && <AiStudioPrefsTab />}
      </div>
    </div>
  );
}
