import { useEffect, useState } from "react";
import AiAgentChat from "./AiAgentChat";
import AiStudioBundleTab, { defaultBundleManifest } from "./AiStudioBundleTab";
import AiStudioSettingsTab from "./AiStudioSettingsTab";
import { useAgentChat } from "../context/AgentChatContext";
import { loadAiStudioPrefs, saveAiStudioPrefs } from "../utils/agentChatStorage";

type StudioMode = "agent" | "bundle" | "settings";

const MODE_LABELS: Record<StudioMode, string> = {
  agent: "Агент",
  bundle: "Пакет bundle",
  settings: "Настройки",
};

export default function AiStudioPanel() {
  const { isPending } = useAgentChat();
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

  return (
    <div className="ai-studio-panel">
      <header className="ai-studio-head">
        <h3>AI Studio</h3>
        {backgroundBusy && mode !== "agent" && (
          <div className="ai-studio-background-hint" role="status">
            <span className="ai-agent-status-bar-pulse" aria-hidden />
            Агент выполняет задачу в фоне — переключитесь на вкладку «Агент»
          </div>
        )}
      </header>

      <nav className="tabs" aria-label="Разделы AI Studio">
        {(Object.keys(MODE_LABELS) as StudioMode[]).map((tab) => (
          <button
            key={tab}
            type="button"
            className={mode === tab ? "active" : ""}
            onClick={() => setMode(tab)}
          >
            {MODE_LABELS[tab]}
            {tab === "agent" && backgroundBusy && (
              <span className="tab-pending-dot" title="Выполняется запрос" />
            )}
          </button>
        ))}
      </nav>

      <div className="ai-studio-body">
        <div className={`ai-studio-tab-layer ${mode === "agent" ? "active" : "dormant"}`}>
          <AiAgentChat />
        </div>

        <div className={`ai-studio-tab-layer ${mode === "bundle" ? "active" : "dormant"}`}>
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
        </div>

        <div className={`ai-studio-tab-layer ${mode === "settings" ? "active" : "dormant"}`}>
          <AiStudioSettingsTab />
        </div>
      </div>
    </div>
  );
}
