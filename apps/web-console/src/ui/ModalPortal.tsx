import { createPortal } from "react-dom";
import type { ReactNode } from "react";

/** Render modals on document.body so dashboard grid z-index cannot cover them. */
export default function ModalPortal({ children }: { children: ReactNode }) {
  if (typeof document === "undefined") {
    return null;
  }
  return createPortal(children, document.body);
}
