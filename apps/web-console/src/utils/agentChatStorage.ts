const STORAGE_KEY = "ispf-agent-chats";

export interface AgentChatIndexEntry {
  id: string;
  title: string;
  updatedAt: string;
}

export interface AgentChatIndex {
  activeSessionId: string | null;
  chats: AgentChatIndexEntry[];
}

const DEFAULT_INDEX: AgentChatIndex = {
  activeSessionId: null,
  chats: [],
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
