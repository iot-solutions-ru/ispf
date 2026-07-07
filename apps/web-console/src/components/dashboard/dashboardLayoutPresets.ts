import {
  DASHBOARD_COLUMNS,
  newWidget,
  type DashboardLayout,
  type DashboardLayoutPreset,
} from "../../types/dashboard";

/** Row units per video-wall quadrant (~448px at 8px row height). */
const VIDEO_WALL_QUADRANT_ROWS = 56;

export function applyLayoutPreset(
  preset: DashboardLayoutPreset,
  layout: DashboardLayout
): DashboardLayout {
  if (preset === "video-wall-2x2") {
    const halfW = DASHBOARD_COLUMNS / 2;
    const halfH = VIDEO_WALL_QUADRANT_ROWS;
    const quadrantTitles = ["TL", "TR", "BL", "BR"];
    const widgets = quadrantTitles.map((title, index) => {
      const widget = newWidget("panel", index);
      return {
        ...widget,
        id: `video-wall-${index}-${Date.now()}`,
        title,
        x: (index % 2) * halfW,
        y: Math.floor(index / 2) * halfH,
        w: halfW,
        h: halfH,
        childrenJson: "[]",
        sampleTemplate: false,
      };
    });
    return {
      ...layout,
      layoutPreset: preset,
      widgets,
    };
  }
  return { ...layout, layoutPreset: preset };
}

export function clearLayoutPreset(layout: DashboardLayout): DashboardLayout {
  const { layoutPreset: _removed, ...rest } = layout;
  return rest;
}
