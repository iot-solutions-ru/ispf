import { renderHook, act } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MIMIC_HISTORY_LIMIT, useMimicHistory } from "./useMimicHistory";

describe("useMimicHistory", () => {
  it("supports undo and redo", () => {
    const { result } = renderHook(() => useMimicHistory({ count: 0 }));

    act(() => result.current.setPresent({ count: 1 }));
    act(() => result.current.setPresent({ count: 2 }));

    expect(result.current.present).toEqual({ count: 2 });
    expect(result.current.canUndo).toBe(true);

    act(() => result.current.undo());
    expect(result.current.present).toEqual({ count: 1 });

    act(() => result.current.redo());
    expect(result.current.present).toEqual({ count: 2 });
  });

  it(`caps undo stack at ${MIMIC_HISTORY_LIMIT} operations`, () => {
    const { result } = renderHook(() => useMimicHistory({ count: 0 }));

    for (let i = 1; i <= MIMIC_HISTORY_LIMIT + 5; i += 1) {
      act(() => result.current.setPresent({ count: i }));
    }

    let undoCount = 0;
    while (result.current.canUndo) {
      act(() => result.current.undo());
      undoCount += 1;
    }

    expect(undoCount).toBe(MIMIC_HISTORY_LIMIT);
    expect(result.current.present).toEqual({ count: 5 });
  });
});
