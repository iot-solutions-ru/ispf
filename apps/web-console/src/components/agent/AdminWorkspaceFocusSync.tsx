import { useMemo } from "react";
import {
  objectTypeToFocusSurface,
  type AdminClientFocus,
  type AdminFocusSurface,
} from "../../context/AdminFocusContext";
import { usePublishAdminFocus } from "../../hooks/usePublishAdminFocus";
import type { EditorTab } from "../../types";

interface AdminWorkspaceFocusSyncProps {
  workspaceTab: string;
  selectedPath: string | null;
  activeEditor: EditorTab | undefined;
  showPropertiesEditor: boolean;
}

/**
 * Publishes workspace-level admin focus (explorer / editors / system / AI Studio).
 * Expression-editor and other modal surfaces publish separately with higher priority.
 */
export default function AdminWorkspaceFocusSync({
  workspaceTab,
  selectedPath,
  activeEditor,
  showPropertiesEditor,
}: AdminWorkspaceFocusSyncProps) {
  const focus = useMemo((): AdminClientFocus => {
    if (workspaceTab === "system") {
      return { surface: "system" };
    }
    if (workspaceTab === "ai-studio") {
      return { surface: "ai-studio" };
    }
    if (activeEditor && workspaceTab === activeEditor.id) {
      const surface: AdminFocusSurface = showPropertiesEditor
        ? "properties"
        : objectTypeToFocusSurface(activeEditor.objectType);
      return {
        surface,
        objectPath: activeEditor.path,
        objectType: activeEditor.objectType,
        editorTabId: activeEditor.id,
      };
    }
    return {
      surface: "explorer",
      objectPath: selectedPath ?? undefined,
    };
  }, [workspaceTab, selectedPath, activeEditor, showPropertiesEditor]);

  usePublishAdminFocus("workspace", focus, true);
  return null;
}
