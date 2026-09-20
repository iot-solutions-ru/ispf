export type AgentMarkdownBlock =
  | { type: "p"; text: string }
  | { type: "h3"; text: string }
  | { type: "ol"; items: string[] }
  | { type: "ul"; items: string[] };

const ORDERED_ITEM = /^\d+[.)]\s+(.*)$/;
const UNORDERED_ITEM = /^[-*•]\s+(.*)$/;

/** Split inline "1. … 2. …" steps onto separate lines for legacy agent replies. */
export function normalizeAgentMarkdown(text: string): string {
  let normalized = text.trim();
  if (!normalized) {
    return "";
  }
  normalized = normalized.replace(/(?<=[^\n])\s+(?=\d+[.)]\s)/g, "\n");
  normalized = normalized.replace(/:\s+(\d+[.)]\s)/g, ":\n$1");
  // Match legacy RU agent replies that start a section with **Пример
  normalized = normalized.replace(/(?<=[^\n])\s+(?=\*\*\u041F\u0440\u0438\u043C\u0435\u0440)/gi, "\n\n");
  return normalized;
}

export function parseAgentMarkdownBlocks(text: string): AgentMarkdownBlock[] {
  const normalized = normalizeAgentMarkdown(text);
  if (!normalized) {
    return [];
  }

  const blocks: AgentMarkdownBlock[] = [];
  const lines = normalized.split("\n");
  let paragraph: string[] = [];
  let listItems: string[] = [];
  let listKind: "ol" | "ul" | null = null;

  const flushParagraph = () => {
    const joined = paragraph.join(" ").trim();
    if (joined) {
      blocks.push({ type: "p", text: joined });
    }
    paragraph = [];
  };

  const flushList = () => {
    if (listItems.length > 0 && listKind) {
      blocks.push({ type: listKind, items: [...listItems] });
    }
    listItems = [];
    listKind = null;
  };

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) {
      flushList();
      flushParagraph();
      continue;
    }

    if (line.startsWith("### ")) {
      flushList();
      flushParagraph();
      blocks.push({ type: "h3", text: line.slice(4).trim() });
      continue;
    }

    const ordered = line.match(ORDERED_ITEM);
    if (ordered) {
      flushParagraph();
      if (listKind !== "ol") {
        flushList();
        listKind = "ol";
      }
      listItems.push(ordered[1].trim());
      continue;
    }

    const unordered = line.match(UNORDERED_ITEM);
    if (unordered) {
      flushParagraph();
      if (listKind !== "ul") {
        flushList();
        listKind = "ul";
      }
      listItems.push(unordered[1].trim());
      continue;
    }

    flushList();
    paragraph.push(line);
  }

  flushList();
  flushParagraph();
  return blocks;
}
