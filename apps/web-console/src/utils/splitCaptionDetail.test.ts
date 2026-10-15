import { describe, expect, it } from "vitest";
import { joinCaptionHints, splitCaptionDetail } from "./splitCaptionDetail";

describe("splitCaptionDetail", () => {
  it("splits trailing parenthetical into title and detail", () => {
    expect(splitCaptionDetail("Объект (Откуда читать данные)")).toEqual({
      title: "Объект",
      detail: "Откуда читать данные",
    });
  });

  it("joinCaptionHints skips empty parts", () => {
    expect(joinCaptionHints(undefined, "a", "", "b")).toBe("a\nb");
  });
});
