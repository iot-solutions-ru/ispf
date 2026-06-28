import { useCallback, useEffect, useState } from "react";
import {
  OPERATOR_SIDEBAR_DESKTOP_MIN_PX,
  shouldUseOperatorSidebarDrawer,
} from "../utils/operatorShellLayout";

export function useOperatorSidebarDrawer(closeWhen: readonly unknown[]) {
  const [open, setOpen] = useState(false);

  const close = useCallback(() => setOpen(false), []);
  const toggle = useCallback(() => setOpen((value) => !value), []);

  useEffect(() => {
    close();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- close drawer on navigation/context change
  }, closeWhen);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        close();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, close]);

  useEffect(() => {
    const media = window.matchMedia(`(min-width: ${OPERATOR_SIDEBAR_DESKTOP_MIN_PX}px)`);
    const onChange = () => {
      if (media.matches) {
        setOpen(false);
      }
    };
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);

  useEffect(() => {
    if (!open || !shouldUseOperatorSidebarDrawer(window.innerWidth)) {
      return;
    }
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open]);

  return { open, setOpen, toggle, close };
}
