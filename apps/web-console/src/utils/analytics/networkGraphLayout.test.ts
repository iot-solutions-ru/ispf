import { describe, expect, it } from "vitest";
import {
  elementsWithPreservedPositions,
  networkGraphApplyMode,
  shouldFitNetworkGraphOnApply,
  shouldFitNetworkGraphOnResize,
} from "./networkGraphLayout";

describe("networkGraphApplyMode", () => {
  it("always layouts in view mode", () => {
    expect(
      networkGraphApplyMode({ editable: false, hasLaidOut: true, layoutChanged: false })
    ).toBe("layout");
  });

  it("layouts the first edit pass and when the algorithm changes", () => {
    expect(
      networkGraphApplyMode({ editable: true, hasLaidOut: false, layoutChanged: false })
    ).toBe("layout");
    expect(
      networkGraphApplyMode({ editable: true, hasLaidOut: true, layoutChanged: true })
    ).toBe("layout");
  });

  it("preserves positions on later data ticks in edit mode", () => {
    expect(
      networkGraphApplyMode({ editable: true, hasLaidOut: true, layoutChanged: false })
    ).toBe("preserve");
  });
});

describe("shouldFitNetworkGraph", () => {
  it("does not fit on resize while editing", () => {
    expect(shouldFitNetworkGraphOnResize(true)).toBe(false);
    expect(shouldFitNetworkGraphOnResize(false)).toBe(true);
  });

  it("fits only after a layout pass", () => {
    expect(shouldFitNetworkGraphOnApply("layout")).toBe(true);
    expect(shouldFitNetworkGraphOnApply("preserve")).toBe(false);
  });
});

describe("elementsWithPreservedPositions", () => {
  it("reattaches stored coordinates so a data refresh does not dump nodes at origin", () => {
    const next = elementsWithPreservedPositions(
      [
        { data: { id: "a", label: "A" } },
        { data: { id: "e1", source: "a", target: "b" } },
      ],
      { a: { x: 40, y: 80 } }
    );
    expect(next[0]).toMatchObject({ data: { id: "a" }, position: { x: 40, y: 80 } });
    expect(next[1].position).toBeUndefined();
  });
});
