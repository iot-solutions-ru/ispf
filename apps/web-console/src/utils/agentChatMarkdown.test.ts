import { describe, expect, it } from "vitest";
import { normalizeAgentMarkdown, parseAgentMarkdownBlocks } from "./agentChatMarkdown";

describe("normalizeAgentMarkdown", () => {
  it("splits inline numbered steps onto separate lines", () => {
    const input =
      "Алгоритм: 1. **Сборка**: foo 2. **Валидация**: bar 3. **Импорт**: baz";
    const normalized = normalizeAgentMarkdown(input);
    expect(normalized).toContain("1. **Сборка**: foo\n2. **Валидация**: bar");
  });
});

describe("parseAgentMarkdownBlocks", () => {
  it("parses intro paragraph and ordered list", () => {
    const text = `Чтобы упаковать bundle:

1. **Сборка манифеста**: \`pull_application_from_tree\`
2. **Валидация**: \`validate_bundle\``;

    const blocks = parseAgentMarkdownBlocks(text);
    expect(blocks).toEqual([
      { type: "p", text: "Чтобы упаковать bundle:" },
      {
        type: "ol",
        items: [
          "**Сборка манифеста**: `pull_application_from_tree`",
          "**Валидация**: `validate_bundle`",
        ],
      },
    ]);
  });

  it("parses bullet list and heading", () => {
    const blocks = parseAgentMarkdownBlocks(`### Пример\n\n- mini-tec\n- pipeline-scada`);
    expect(blocks).toEqual([
      { type: "h3", text: "Пример" },
      { type: "ul", items: ["mini-tec", "pipeline-scada"] },
    ]);
  });

  it("recovers inline numbered steps from one-line agent replies", () => {
    const blocks = parseAgentMarkdownBlocks(
      "Шаги: 1. **A**: one 2. **B**: two"
    );
    expect(blocks).toEqual([
      { type: "p", text: "Шаги:" },
      { type: "ol", items: ["**A**: one", "**B**: two"] },
    ]);
  });
});
