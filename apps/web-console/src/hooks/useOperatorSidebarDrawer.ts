import { useCallback, useEffect, useState } from "react";
import { shouldLockBodyForOperatorSidebar } from "../utils/operator/operatorShellLayout";

const SIDEBAR_OPEN_STORAGE_KEY = "ispf-operator-sidebar-open";

function readStoredOpen(): boolean {
  try {
    const raw = localStorage.getItem(SIDEBAR_OPEN_STORAGE_KEY);
    if (raw === "0") {
      return false;
    }
    if (raw === "1") {
      return true;
    }
  } catch {
    // ignore
  }
  // Default open on wide screens so existing operator layouts keep the journal visible.
  return typeof window !== "undefined" ? window.innerWidth > 900 : true;
}

export function useOperatorSidebarDrawer(closeWhen: readonly unknown[]) {
  const [open, setOpen] = useState(readStoredOpen);

  const close = useCallback(() => setOpen(false), []);
  const toggle = useCallback(() => setOpen((value) => !value), []);

  useEffect(() => {
    try {
      localStorage.setItem(SIDEBAR_OPEN_STORAGE_KEY, open ? "1" : "0");
    } catch {
      // ignore
    }
  }, [open]);

  useEffect(() => {
    // Close on navigation so a new dashboard starts clean on mobile; keep preference on desktop.
    if (shouldLockBodyForOperatorSidebar(window.innerWidth)) {
      close();
    }
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
    if (!open || !shouldLockBodyForOperatorSidebar(window.innerWidth)) {
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
