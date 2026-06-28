import { describe, expect, it } from "vitest";
import {
  OPERATOR_SIDEBAR_DESKTOP_MIN_PX,
  shouldUseOperatorSidebarDrawer,
} from "./operatorShellLayout";

describe("operatorShellLayout", () => {
  it("uses drawer below desktop breakpoint", () => {
    expect(OPERATOR_SIDEBAR_DESKTOP_MIN_PX).toBe(901);
    expect(shouldUseOperatorSidebarDrawer(900)).toBe(true);
    expect(shouldUseOperatorSidebarDrawer(901)).toBe(false);
  });
});
