const COPILOT_SELECTOR = ".admin-copilot-drawer, .admin-copilot-fab";

function isCopilotTarget(target: EventTarget | null): boolean {
  return target instanceof Element && Boolean(target.closest(COPILOT_SELECTOR));
}

/**
 * Ant Design modals lock focus (window `focusin`) and close on Escape.
 * The copilot is portaled outside the dialog, so that lock swallows typing.
 * Stop those events once focus is already inside the copilot.
 */
export function installCopilotFocusGuard(): () => void {
  const onFocusIn = (event: FocusEvent) => {
    if (isCopilotTarget(event.target)) {
      event.stopPropagation();
    }
  };
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === "Escape" && isCopilotTarget(event.target)) {
      event.stopPropagation();
    }
  };
  window.addEventListener("focusin", onFocusIn, true);
  window.addEventListener("keydown", onKeyDown, true);
  return () => {
    window.removeEventListener("focusin", onFocusIn, true);
    window.removeEventListener("keydown", onKeyDown, true);
  };
}
