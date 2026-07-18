import { describe, expect, it } from "vitest";
import {
  OPERATOR_SIDEBAR_BODY_LOCK_MAX_PX,
  shouldLockBodyForOperatorSidebar,
  shouldUseOperatorSidebarDrawer,
} from "./operatorShellLayout";

describe("operatorShellLayout", () => {
  it("always uses overlay drawer", () => {
    expect(shouldUseOperatorSidebarDrawer(400)).toBe(true);
    expect(shouldUseOperatorSidebarDrawer(901)).toBe(true);
    expect(shouldUseOperatorSidebarDrawer(1920)).toBe(true);
  });

  it("locks body scroll only on compact viewports", () => {
    expect(OPERATOR_SIDEBAR_BODY_LOCK_MAX_PX).toBe(900);
    expect(shouldLockBodyForOperatorSidebar(900)).toBe(true);
    expect(shouldLockBodyForOperatorSidebar(901)).toBe(false);
  });
});
