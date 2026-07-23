import { describe, expect, it } from "vitest";
import {
  formatRefinePlanMessage,
  isExecuteIntentSuggestion,
  isPlanApprovalSuggestion,
  parseOperatorAgentArtifacts,
} from "./operatorAgentArtifacts";

describe("parseOperatorAgentArtifacts links", () => {
  it("drops agent links with unsafe url schemes", () => {
    const parsed = parseOperatorAgentArtifacts({
      links: [
        { kind: "dashboard", path: "/d/1", title: "ok", url: "https://example.com/x" },
        { kind: "dashboard", path: "/d/2", title: "xss", url: "javascript:alert(1)" },
        { kind: "report", path: "/r/1", title: "data", url: "data:text/html,<b>1</b>" },
        { kind: "report", path: "/r/2", title: "plain" },
      ],
    });
    expect(parsed.links?.map((link) => link.path)).toEqual(["/d/1", "/r/2"]);
  });
});

describe("formatRefinePlanMessage", () => {
  it("joins localized gaps under the completeness header", () => {
    const message = formatRefinePlanMessage(
      [
        "specBrief incomplete — need title, entities[], ≥3 functionalRequirements with sourcePhrase",
        "section ground_truth too thin — need summary ≥80 chars, ≥2 concrete steps, deliverables[]",
        "gapMatrix missing — map each FR to capability",
      ],
      "ru",
      "До утверждения не хватает",
      "fallback"
    );
    expect(message).toBe(
      [
        "До утверждения не хватает",
        "specBrief неполный — нужны title, entities[], ≥3 functionalRequirements с sourcePhrase",
        "Секция ground_truth слишком краткая — summary ≥80 символов, ≥2 конкретных шагов, deliverables[]",
        "Нет gapMatrix — сопоставьте каждый FR с capability платформы",
      ].join("\n")
    );
  });

  it("returns fallback when gaps are empty", () => {
    expect(formatRefinePlanMessage([], "ru", "До утверждения не хватает", "fallback")).toBe("fallback");
  });
});

describe("isExecuteIntentSuggestion", () => {
  it("detects execute shortcuts", () => {
    expect(isExecuteIntentSuggestion({ label: "Выполнить", message: "выполнить" })).toBe(true);
    expect(isExecuteIntentSuggestion({ label: "Execute", message: "execute" })).toBe(true);
  });

  it("does not treat approval as execute", () => {
    expect(
      isExecuteIntentSuggestion({
        label: "Утвердить полный план",
        message: "Утверждаю план, начинай выполнение",
        primary: true,
      })
    ).toBe(false);
  });

  it("detects approval by message even when not primary", () => {
    expect(
      isPlanApprovalSuggestion({
        label: "Утвердить план",
        message: "Утверждаю план, начинай выполнение",
      })
    ).toBe(true);
  });
});
