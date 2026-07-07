import { renderHook, act } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useMimicHistory } from "./useMimicHistory";

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
});
