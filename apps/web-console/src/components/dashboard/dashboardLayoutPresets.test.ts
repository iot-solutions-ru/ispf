import { describe, expect, it } from "vitest";
import {
  applyLayoutPreset,
  isVideoWallPreset,
  videoWallSlotCount,
} from "./dashboardLayoutPresets";
import type { DashboardLayout } from "../../types/dashboard";

const emptyLayout = (): DashboardLayout => ({
  version: 1,
  widgets: [],
});

describe("dashboardLayoutPresets video wall (BL-148)", () => {
  it("builds 2x2 / 3x3 / 4x4 slot grids", () => {
    expect(videoWallSlotCount("video-wall-2x2")).toBe(4);
    expect(videoWallSlotCount("video-wall-3x3")).toBe(9);
    expect(videoWallSlotCount("video-wall-4x4")).toBe(16);

    const wall4 = applyLayoutPreset("video-wall-4x4", emptyLayout());
    expect(wall4.layoutPreset).toBe("video-wall-4x4");
    expect(wall4.widgets).toHaveLength(16);
    expect(wall4.widgets[0].w).toBe(21);
    expect(wall4.widgets[15].title).toBe("4x4");
  });

  it("recognizes all video-wall presets", () => {
    expect(isVideoWallPreset("video-wall-2x2")).toBe(true);
    expect(isVideoWallPreset("video-wall-3x3")).toBe(true);
    expect(isVideoWallPreset("video-wall-4x4")).toBe(true);
    expect(isVideoWallPreset(undefined)).toBe(false);
  });
});
