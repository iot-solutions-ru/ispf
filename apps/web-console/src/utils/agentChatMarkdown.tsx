import { memo, useMemo, type ReactNode } from "react";
export type AgentMarkdownBlock =
  | { type: "p"; text: string }
  | { type: "h3"; text: string }
  | { type: "ol"; items: string[] }
  | { type: "ul"; items: string[] };

const ORDERED_ITEM = /^\d+[\.)]\s+(.*)$/;
const UNORDERED_ITEM = /^[-*•]\s+(.*)$/;

/** Split inline "1. … 2. …" steps onto separate lines for legacy agent replies. */
export function normalizeAgentMarkdown(text: string): string {
  let normalized = text.trim();
  if (!normalized) {
    return "";
  }
  normalized = normalized.replace(/(?<=[^\n])\s+(?=\d+[\.)]\s)/g, "\n");
  normalized = normalized.replace(/:\s+(\d+[\.)]\s)/g, ":\n$1");
  normalized = normalized.replace(/(?<=[^\n])\s+(?=\*\*Пример)/gi, "\n\n");
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

function renderInlineMarkdown(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let index = 0;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index));
    }
    const token = match[0];
    if (token.startsWith("**")) {
      nodes.push(
        <strong key={`${keyPrefix}-b-${index}`}>{token.slice(2, -2)}</strong>
      );
    } else {
      nodes.push(
        <code key={`${keyPrefix}-c-${index}`} className="ai-agent-md-code">
          {token.slice(1, -1)}
        </code>
      );
    }
    lastIndex = match.index + token.length;
    index += 1;
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }
  return nodes.length > 0 ? nodes : [text];
}

export const AgentChatMessageBody = memo(function AgentChatMessageBody({ text }: { text: string }) {
  const blocks = useMemo(() => parseAgentMarkdownBlocks(text), [text]);  if (blocks.length === 0) {
    return null;
  }

  return (
    <div className="ai-agent-md">
      {blocks.map((block, blockIndex) => {
        if (block.type === "h3") {
          return (
            <h4 key={blockIndex} className="ai-agent-md-heading">
              {renderInlineMarkdown(block.text, `h-${blockIndex}`)}
            </h4>
          );
        }
        if (block.type === "ol") {
          return (
            <ol key={blockIndex} className="ai-agent-md-list">
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex}>{renderInlineMarkdown(item, `ol-${blockIndex}-${itemIndex}`)}</li>
              ))}
            </ol>
          );
        }
        if (block.type === "ul") {
          return (
            <ul key={blockIndex} className="ai-agent-md-list">
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex}>{renderInlineMarkdown(item, `ul-${blockIndex}-${itemIndex}`)}</li>
              ))}
            </ul>
          );
        }
        return (
          <p key={blockIndex} className="ai-agent-md-paragraph">
            {renderInlineMarkdown(block.text, `p-${blockIndex}`)}
          </p>
        );
      })}
    </div>
  );
});