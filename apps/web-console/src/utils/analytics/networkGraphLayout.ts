import type { ElementDefinition } from "cytoscape";

export type NetworkGraphApplyMode = "layout" | "preserve";

export type NetworkGraphNodePosition = { x: number; y: number };

/** In edit mode keep node coordinates across data ticks; re-layout only once or when the algorithm changes. */
export function networkGraphApplyMode(options: {
  editable: boolean;
  hasLaidOut: boolean;
  layoutChanged: boolean;
}): NetworkGraphApplyMode {
  if (!options.editable) {
    return "layout";
  }
  if (!options.hasLaidOut || options.layoutChanged) {
    return "layout";
  }
  return "preserve";
}

export function shouldFitNetworkGraphOnResize(editable: boolean): boolean {
  return !editable;
}

export function shouldFitNetworkGraphOnApply(applyMode: NetworkGraphApplyMode): boolean {
  return applyMode === "layout";
}

export function elementsWithPreservedPositions(
  elements: ElementDefinition[],
  positions: Record<string, NetworkGraphNodePosition>
): ElementDefinition[] {
  return elements.map((element) => {
    const id = element.data?.id;
    if (!id || element.data?.source != null) {
      return element;
    }
    const position = positions[String(id)];
    if (!position) {
      return element;
    }
    return { ...element, position: { x: position.x, y: position.y } };
  });
}
