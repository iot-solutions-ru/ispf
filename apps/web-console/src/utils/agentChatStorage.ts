const STORAGE_KEY = "ispf-agent-chats";
const PENDING_KEY = "ispf-agent-pending";
const PREFS_KEY = "ispf-ai-studio-prefs";

export interface AgentChatIndexEntry {
  id: string;
  title: string;
  updatedAt: string;
}

export interface AgentChatIndex {
  activeSessionId: string | null;
  chats: AgentChatIndexEntry[];
}

export interface AgentPendingTurn {
  sessionId: string;
  startedAt: string;
  userMessage: string;
  turnCountBefore: number;
}

export interface AiStudioPrefs {
  defaultRootPath: string;
  defaultAppId: string;
  lastTab: "agent" | "bundle" | "settings";
  restoreLastChat: boolean;
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
};

export function loadAgentChatIndex(): AgentChatIndex {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
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

export function saveAgentChatIndex(index: AgentChatIndex): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(index));
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

export function clearAgentChatIndex(): AgentChatIndex {
  const cleared = { ...DEFAULT_INDEX };
  saveAgentChatIndex(cleared);
  return cleared;
}

export function loadAgentPendingTurn(): AgentPendingTurn | null {
  try {
    const raw = localStorage.getItem(PENDING_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as AgentPendingTurn;
    if (!parsed.sessionId || !parsed.startedAt) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveAgentPendingTurn(pending: AgentPendingTurn): void {
  localStorage.setItem(PENDING_KEY, JSON.stringify(pending));
}

export function clearAgentPendingTurn(): void {
  localStorage.removeItem(PENDING_KEY);
}

export function loadAiStudioPrefs(): AiStudioPrefs {
  try {
    const raw = localStorage.getItem(PREFS_KEY);
    if (!raw) {
      return { ...DEFAULT_PREFS };
    }
    const parsed = JSON.parse(raw) as Partial<AiStudioPrefs>;
    return {
      defaultRootPath: parsed.defaultRootPath?.trim() || DEFAULT_PREFS.defaultRootPath,
      defaultAppId: parsed.defaultAppId?.trim() || DEFAULT_PREFS.defaultAppId,
      lastTab: parsed.lastTab ?? DEFAULT_PREFS.lastTab,
      restoreLastChat: parsed.restoreLastChat ?? DEFAULT_PREFS.restoreLastChat,
    };
  } catch {
    return { ...DEFAULT_PREFS };
  }
}

export function saveAiStudioPrefs(prefs: AiStudioPrefs): void {
  localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
}
