import {
  DASHBOARD_COLUMNS,
  newWidget,
  type DashboardLayout,
  type DashboardLayoutPreset,
} from "../../types/dashboard";

/** Row units per video-wall quadrant (~448px at 8px row height). */
const VIDEO_WALL_QUADRANT_ROWS = 56;

function videoWallGrid(_preset: DashboardLayoutPreset, columns: number, rows: number): DashboardLayout["widgets"] {
  const cellW = DASHBOARD_COLUMNS / columns;
  const cellH = VIDEO_WALL_QUADRANT_ROWS;
  const slotCount = columns * rows;
  return Array.from({ length: slotCount }, (_, index) => {
    const widget = newWidget("panel", index);
    const col = index % columns;
    const row = Math.floor(index / columns);
    return {
      ...widget,
      id: `video-wall-${index}-${Date.now()}`,
      title: `${col + 1}x${row + 1}`,
      x: col * cellW,
      y: row * cellH,
      w: cellW,
      h: cellH,
      childrenJson: "[]",
      sampleTemplate: false,
    };
  });
}

function videoWallDimensions(preset: DashboardLayoutPreset): { columns: number; rows: number } | null {
  if (preset === "video-wall-2x2") {
    return { columns: 2, rows: 2 };
  }
  if (preset === "video-wall-3x3") {
    return { columns: 3, rows: 3 };
  }
  if (preset === "video-wall-4x4") {
    return { columns: 4, rows: 4 };
  }
  return null;
}

export function applyLayoutPreset(
  preset: DashboardLayoutPreset,
  layout: DashboardLayout
): DashboardLayout {
  const dims = videoWallDimensions(preset);
  if (dims) {
    return {
      ...layout,
      layoutPreset: preset,
      widgets: videoWallGrid(preset, dims.columns, dims.rows),
    };
  }
  return { ...layout, layoutPreset: preset };
}

export function clearLayoutPreset(layout: DashboardLayout): DashboardLayout {
  const { layoutPreset: _removed, ...rest } = layout;
  return rest;
}

export function videoWallSlotCount(preset: DashboardLayoutPreset): number {
  const dims = videoWallDimensions(preset);
  return dims ? dims.columns * dims.rows : 4;
}

export function isVideoWallPreset(preset: DashboardLayoutPreset | undefined): preset is DashboardLayoutPreset {
  return preset === "video-wall-2x2" || preset === "video-wall-3x3" || preset === "video-wall-4x4";
}
