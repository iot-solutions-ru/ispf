import { memo, useMemo } from "react";
import type { ReactNode } from "react";
import { parseAgentMarkdownBlocks } from "./agentChatMarkdown";

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
  const blocks = useMemo(() => parseAgentMarkdownBlocks(text), [text]);
  if (blocks.length === 0) {
    if (!text?.trim()) {
      return <span className="op-muted">…</span>;
    }
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