const STORAGE_KEY_STUDIO = "ispf-agent-chats";
const STORAGE_KEY_COPILOT = "ispf-agent-chats-copilot";
const PENDING_KEY = "ispf-agent-pending";
const PREFS_KEY = "ispf-ai-studio-prefs";
const COPILOT_PREFS_KEY = "ispf-copilot-prefs";

export type AgentChatChannel = "studio" | "copilot";

export interface AgentChatIndexEntry {
  id: string;
  title: string;
  updatedAt: string;
}

export interface AgentChatIndex {
  activeSessionId: string | null;
  chats: AgentChatIndexEntry[];
}

export interface AiStudioPrefs {
  defaultRootPath: string;
  defaultAppId: string;
  lastTab: "agent" | "bundle" | "status" | "prefs";
  restoreLastChat: boolean;
  interactionMode: "auto" | "plan" | "execute" | "ask";
}

export interface CopilotPrefs {
  restoreLastChat: boolean;
  /** Copilot defaults to ask — help with the focused screen. */
  interactionMode: "auto" | "plan" | "execute" | "ask";
}

const DEFAULT_INDEX: AgentChatIndex = {
  activeSessionId: null,
  chats: [],
};

const DEFAULT_PREFS: AiStudioPrefs = {
  defaultRootPath: "root",
  defaultAppId: "ai-generated",
  lastTab: "agent",
  restoreLastChat: true,
  interactionMode: "auto",
};

const DEFAULT_COPILOT_PREFS: CopilotPrefs = {
  /** Copilot is here-and-now — never restore a deep prior chat. */
  restoreLastChat: false,
  interactionMode: "ask",
};

function storageKeyForChannel(channel: AgentChatChannel): string {
  return channel === "copilot" ? STORAGE_KEY_COPILOT : STORAGE_KEY_STUDIO;
}

export function loadAgentChatIndex(channel: AgentChatChannel = "studio"): AgentChatIndex {
  try {
    const raw = localStorage.getItem(storageKeyForChannel(channel));
    if (!raw) {
      return { ...DEFAULT_INDEX };
    }
    const parsed = JSON.parse(raw) as AgentChatIndex;
    return {
      activeSessionId: parsed.activeSessionId ?? null,
      chats: Array.isArray(parsed.chats) ? parsed.chats : [],
    };
  } catch {
    return { ...DEFAULT_INDEX };
  }
}

export function saveAgentChatIndex(
  index: AgentChatIndex,
  channel: AgentChatChannel = "studio"
): void {
  localStorage.setItem(storageKeyForChannel(channel), JSON.stringify(index));
}

export function upsertChatEntry(index: AgentChatIndex, entry: AgentChatIndexEntry): AgentChatIndex {
  const without = index.chats.filter((chat) => chat.id !== entry.id);
  const chats = [entry, ...without].sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
  );
  return { ...index, chats };
}

export function removeChatEntry(index: AgentChatIndex, sessionId: string): AgentChatIndex {
  const chats = index.chats.filter((chat) => chat.id !== sessionId);
  const activeSessionId =
    index.activeSessionId === sessionId ? (chats[0]?.id ?? null) : index.activeSessionId;
  return { ...index, chats, activeSessionId };
}

export function clearAgentChatIndex(channel: AgentChatChannel = "studio"): AgentChatIndex {
  const cleared = { ...DEFAULT_INDEX };
  saveAgentChatIndex(cleared, channel);
  return cleared;
}

/** Удаляет устаревший ключ ispf-agent-pending (больше не используется). */
export function purgeLegacyAgentPending(): void {
  try {
    localStorage.removeItem(PENDING_KEY);
  } catch {
    // ignore quota / private mode
  }
}

export function loadAiStudioPrefs(): AiStudioPrefs {
  try {
    const raw = localStorage.getItem(PREFS_KEY);
    if (!raw) {
      return { ...DEFAULT_PREFS };
    }
    const parsed = JSON.parse(raw) as Partial<AiStudioPrefs> & { lastTab?: string };
    const lastTabRaw = parsed.lastTab as string | undefined;
    const lastTab: AiStudioPrefs["lastTab"] =
      lastTabRaw === "settings"
        ? "status"
        : lastTabRaw === "agent" ||
            lastTabRaw === "bundle" ||
            lastTabRaw === "status" ||
            lastTabRaw === "prefs"
          ? lastTabRaw
          : DEFAULT_PREFS.lastTab;
    return {
      defaultRootPath: parsed.defaultRootPath?.trim() || DEFAULT_PREFS.defaultRootPath,
      defaultAppId: parsed.defaultAppId?.trim() || DEFAULT_PREFS.defaultAppId,
      lastTab,
      restoreLastChat: parsed.restoreLastChat ?? DEFAULT_PREFS.restoreLastChat,
      interactionMode: parsed.interactionMode ?? DEFAULT_PREFS.interactionMode,
    };
  } catch {
    return { ...DEFAULT_PREFS };
  }
}

export function saveAiStudioPrefs(prefs: AiStudioPrefs): void {
  localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
}

export function loadCopilotPrefs(): CopilotPrefs {
  try {
    const raw = localStorage.getItem(COPILOT_PREFS_KEY);
    if (!raw) {
      return { ...DEFAULT_COPILOT_PREFS };
    }
    const parsed = JSON.parse(raw) as Partial<CopilotPrefs>;
    return {
      // Always false: Admin Copilot answers from live UI focus, not prior threads.
      restoreLastChat: false,
      interactionMode: parsed.interactionMode ?? DEFAULT_COPILOT_PREFS.interactionMode,
    };
  } catch {
    return { ...DEFAULT_COPILOT_PREFS };
  }
}

export function saveCopilotPrefs(prefs: CopilotPrefs): void {
  localStorage.setItem(COPILOT_PREFS_KEY, JSON.stringify(prefs));
}
