import { useEffect, useRef } from "react";
import {
  useAdminFocusOptional,
  type AdminClientFocus,
} from "../context/AdminFocusContext";

/**
 * Publishes a focus layer while mounted / while {@code active} is true.
 * Clears the layer on unmount or when inactive.
 *
 * Depends on stable publish/clear callbacks (not the whole context value) so
 * unrelated layer updates do not clear/republish this layer in a race.
 */
export function usePublishAdminFocus(
  layerId: string,
  focus: AdminClientFocus | null,
  active = true
): void {
  const registry = useAdminFocusOptional();
  const publishFocus = registry?.publishFocus;
  const clearFocus = registry?.clearFocus;
  const focusRef = useRef(focus);
  focusRef.current = focus;

  const detailKey = JSON.stringify(focus?.detail ?? null);

  useEffect(() => {
    if (!publishFocus || !clearFocus || !active || !focusRef.current) {
      clearFocus?.(layerId);
      return;
    }
    publishFocus(layerId, focusRef.current);
    return () => clearFocus(layerId);
  }, [
    publishFocus,
    clearFocus,
    layerId,
    active,
    focus?.surface,
    focus?.objectPath,
    focus?.objectType,
    focus?.editorTabId,
    focus?.priority,
    detailKey,
  ]);
}
