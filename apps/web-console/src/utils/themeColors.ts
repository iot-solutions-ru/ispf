import { useMemo } from "react";
import { useTheme } from "../theme";

function readCssVar(name: string, fallback: string): string {
  if (typeof document === "undefined") {
    return fallback;
  }
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

/** Resolved theme colors for libraries that cannot use CSS variables (e.g. Cytoscape). */
export function useThemeColors() {
  const { resolvedTheme } = useTheme();

  return useMemo(
    () => ({
      accent: readCssVar("--accent", "#2f81f7"),
      text: readCssVar("--text", "#e6edf3"),
      textMuted: readCssVar("--text-muted", "#8b949e"),
      networkNodeText: readCssVar("--network-node-text", "#e8eaed"),
      networkEdgeColor: readCssVar("--network-edge-color", "#6b7280"),
    }),
    [resolvedTheme]
  );
}
