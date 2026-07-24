import type { ResolvedTheme } from "../theme";

/** Colors for bpmn-js `bpmnRenderer` (applied at modeler/viewer create time). */
export function bpmnRendererTheme(theme: ResolvedTheme): {
  defaultFillColor: string;
  defaultStrokeColor: string;
  defaultLabelColor: string;
} {
  if (theme === "dark") {
    return {
      defaultFillColor: "#21262d",
      defaultStrokeColor: "#e6edf3",
      defaultLabelColor: "#e6edf3",
    };
  }
  return {
    defaultFillColor: "#ffffff",
    defaultStrokeColor: "#1f2937",
    defaultLabelColor: "#1f2937",
  };
}
