import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { AdminFocusContext } from "./useAdminFocus";
import type {
  AdminFocusSurface,
  AdminClientFocus,
  AdminFocusActivity,
  AdminFocusLayer,
} from "./useAdminFocus";
import {
  focusLayerLabel,
  buildClientFocusPayload,
  MAX_RECENT_ACTIONS,
  compactDetailForTrail,
} from "./adminFocusUtils";

const SURFACE_PRIORITY: Partial<Record<AdminFocusSurface, number>> = {
  "expression-editor": 100,
  "binding-rule": 70,
  properties: 40,
  binding: 45,
  alert: 40,
  system: 55,
  "ai-studio": 60,
  dashboard: 30,
  report: 30,
  workflow: 30,
  mimic: 30,
  blueprint: 30,
  schedule: 30,
  application: 30,
  "data-source": 30,
  migration: 30,
  explorer: 10,
  other: 5,
};

function resolvePriority(focus: AdminClientFocus): number {
  if (typeof focus.priority === "number") {
    return focus.priority;
  }
  return SURFACE_PRIORITY[focus.surface] ?? 0;
}

function sortLayers(layers: AdminFocusLayer[]): AdminFocusLayer[] {
  return [...layers].sort((a, b) => resolvePriority(a.focus) - resolvePriority(b.focus));
}

function pickTop(layers: AdminFocusLayer[]): AdminClientFocus | null {
  const top = sortLayers(layers).at(-1);
  return top ? top.focus : null;
}

export function AdminFocusProvider({ children }: { children: ReactNode }) {
  const [layers, setLayers] = useState<AdminFocusLayer[]>([]);
  const [copilotOpenToken, setCopilotOpenToken] = useState(0);
  const [recentActions, setRecentActions] = useState<AdminFocusActivity[]>([]);
  const lastTopKey = useRef<string>("");
  const pendingCopilotPromptRef = useRef<string | null>(null);

  const publishFocus = useCallback((id: string, focus: AdminClientFocus) => {
    setLayers((prev) => {
      const next = prev.filter((layer) => layer.id !== id);
      next.push({ id, focus });
      return next;
    });
  }, []);

  const clearFocus = useCallback((id: string) => {
    setLayers((prev) => prev.filter((layer) => layer.id !== id));
  }, []);

  const requestOpenCopilot = useCallback((prompt?: string) => {
    const trimmed = prompt?.trim();
    pendingCopilotPromptRef.current = trimmed && trimmed.length > 0 ? trimmed : null;
    setCopilotOpenToken((n) => n + 1);
  }, []);

  const takePendingCopilotPrompt = useCallback(() => {
    const prompt = pendingCopilotPromptRef.current;
    pendingCopilotPromptRef.current = null;
    return prompt;
  }, []);

  const focusStack = useMemo(
    () => sortLayers(layers).map((layer) => layer.focus),
    [layers]
  );
  const focus = useMemo(() => pickTop(layers), [layers]);
  const focusTrail = useMemo(
    () =>
      focusStack.map((step) => ({
        surface: step.surface,
        objectPath: step.objectPath,
        objectType: step.objectType,
        label: focusLayerLabel(step),
        detail: compactDetailForTrail(step.detail),
      })),
    [focusStack]
  );

  // Append to activity ring when the top focus identity changes.
  useEffect(() => {
    if (!focus) {
      lastTopKey.current = "";
      return;
    }
    const key = `${focus.surface}|${focus.objectPath ?? ""}|${focusLayerLabel(focus)}`;
    if (key === lastTopKey.current) {
      return;
    }
    lastTopKey.current = key;
    const entry: AdminFocusActivity = {
      at: new Date().toISOString(),
      surface: focus.surface,
      objectPath: focus.objectPath,
      label: focusLayerLabel(focus),
    };
    setRecentActions((prev) => [...prev, entry].slice(-MAX_RECENT_ACTIONS));
  }, [focus]);

  const toClientFocusPayload = useCallback(
    () => buildClientFocusPayload(focusStack, recentActions),
    [focusStack, recentActions]
  );

  const value = useMemo(
    () => ({
      focus,
      focusStack,
      focusTrail,
      recentActions,
      toClientFocusPayload,
      publishFocus,
      clearFocus,
      copilotOpenToken,
      requestOpenCopilot,
      takePendingCopilotPrompt,
    }),
    [
      focus,
      focusStack,
      focusTrail,
      recentActions,
      toClientFocusPayload,
      publishFocus,
      clearFocus,
      copilotOpenToken,
      requestOpenCopilot,
      takePendingCopilotPrompt,
    ]
  );

  return <AdminFocusContext.Provider value={value}>{children}</AdminFocusContext.Provider>;
}

