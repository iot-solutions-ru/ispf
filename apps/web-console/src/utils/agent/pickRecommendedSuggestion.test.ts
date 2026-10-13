import { describe, expect, it } from "vitest";
import {
  DEFAULT_AUTO_CONTINUE_SECONDS,
  pickRecommendedSuggestion,
  resolveAutoContinueSeconds,
} from "./pickRecommendedSuggestion";

describe("pickRecommendedSuggestion", () => {
  it("prefers primary clarify option", () => {
    const picked = pickRecommendedSuggestion([
      { label: "Alt", message: "do alt" },
      { label: "Recommended", message: "do recommended", primary: true },
    ]);
    expect(picked?.message).toBe("do recommended");
  });

  it("skips plan approval primary", () => {
    const picked = pickRecommendedSuggestion([
      { label: "Утвердить план", message: "Утверждаю план, начинай исполнение", primary: true },
      { label: "Use virt-cluster", message: "Use appId=virt-cluster", primary: true },
    ]);
    expect(picked?.message).toBe("Use appId=virt-cluster");
  });

  it("ignores non-primary options", () => {
    expect(
      pickRecommendedSuggestion([{ label: "Alt", message: "do alt" }])
    ).toBeNull();
  });
});

describe("resolveAutoContinueSeconds", () => {
  it("uses result.autoContinueSeconds when set", () => {
    expect(resolveAutoContinueSeconds({ autoContinueSeconds: 30 })).toBe(30);
  });

  it("defaults when awaitingUser", () => {
    expect(resolveAutoContinueSeconds({ awaitingUser: true })).toBe(DEFAULT_AUTO_CONTINUE_SECONDS);
  });

  it("returns 0 for interactive without awaitingUser", () => {
    expect(resolveAutoContinueSeconds({ interactive: true })).toBe(0);
  });
});
