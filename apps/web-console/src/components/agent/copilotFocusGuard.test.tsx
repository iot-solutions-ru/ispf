/** @vitest-environment jsdom */
import { describe, expect, it, vi } from "vitest";
import { installCopilotFocusGuard } from "./copilotFocusGuard";

describe("installCopilotFocusGuard", () => {
  it("keeps copilot focus and Escape from reaching a modal listener", () => {
    const focusLock = vi.fn();
    const modalKey = vi.fn();
    window.addEventListener("focusin", focusLock);
    document.addEventListener("keydown", modalKey);
    const release = installCopilotFocusGuard();

    const drawer = document.createElement("div");
    drawer.className = "admin-copilot-drawer";
    const textarea = document.createElement("textarea");
    drawer.append(textarea);
    document.body.append(drawer);

    textarea.dispatchEvent(new FocusEvent("focusin", { bubbles: true }));
    textarea.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    textarea.dispatchEvent(new KeyboardEvent("keydown", { key: "a", bubbles: true }));

    const outside = document.createElement("button");
    document.body.append(outside);
    outside.dispatchEvent(new FocusEvent("focusin", { bubbles: true }));

    expect(focusLock).toHaveBeenCalledTimes(1);
    expect(modalKey).toHaveBeenCalledTimes(1);
    expect(modalKey.mock.calls[0][0].key).toBe("a");

    release();
    drawer.remove();
    outside.remove();
    window.removeEventListener("focusin", focusLock);
    document.removeEventListener("keydown", modalKey);
  });
});
