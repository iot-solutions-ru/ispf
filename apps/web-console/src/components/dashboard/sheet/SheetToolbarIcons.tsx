import type { ReactNode } from "react";

function SheetIcon({ children }: { children: ReactNode }) {
  return (
    <svg
      className="dash-sheet-tool-icon"
      viewBox="0 0 16 16"
      width="16"
      height="16"
      aria-hidden
      fill="none"
      stroke="currentColor"
      strokeWidth="1.2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {children}
    </svg>
  );
}

export function IconUndo() {
  return (
    <SheetIcon>
      <path d="M3 4.5H9.5a3 3 0 0 1 0 6H7" />
      <path d="M5.5 2.5 3 4.5l2.5 2" />
    </SheetIcon>
  );
}

export function IconRedo() {
  return (
    <SheetIcon>
      <path d="M13 4.5H6.5a3 3 0 0 0 0 6H9" />
      <path d="M10.5 2.5 13 4.5l-2.5 2" />
    </SheetIcon>
  );
}

export function IconCopy() {
  return (
    <SheetIcon>
      <rect x="5.5" y="5.5" width="7" height="7" rx="1" />
      <path d="M3.5 10.5V3.5a1 1 0 0 1 1-1H10" />
    </SheetIcon>
  );
}

export function IconPaste() {
  return (
    <SheetIcon>
      <rect x="4.5" y="2.5" width="7" height="3" rx="0.8" />
      <rect x="3.5" y="5.5" width="9" height="8" rx="1" />
    </SheetIcon>
  );
}

export function IconImport() {
  return (
    <SheetIcon>
      <path d="M8 2.5v7" />
      <path d="M5.5 7 8 9.5 10.5 7" />
      <path d="M3 12.5h10" />
    </SheetIcon>
  );
}

export function IconExport() {
  return (
    <SheetIcon>
      <path d="M8 9.5V2.5" />
      <path d="M5.5 5 8 2.5 10.5 5" />
      <path d="M3 12.5h10" />
    </SheetIcon>
  );
}

export function IconCsv() {
  return (
    <SheetIcon>
      <rect x="2.5" y="2.5" width="11" height="11" rx="1" />
      <path d="M5 5.5h6M5 8h4M5 10.5h5" />
    </SheetIcon>
  );
}

export function IconInsertRowAbove() {
  return (
    <SheetIcon>
      <path d="M2.5 6.5h11" />
      <path d="M8 3.5v6" />
      <path d="M6 5.5 8 3.5l2 2" />
      <path d="M2.5 10.5h11" />
    </SheetIcon>
  );
}

export function IconInsertRowBelow() {
  return (
    <SheetIcon>
      <path d="M2.5 5.5h11" />
      <path d="M2.5 9.5h11" />
      <path d="M8 7.5v3" />
      <path d="M6 9.5 8 11.5l2-2" />
    </SheetIcon>
  );
}

export function IconDeleteRow() {
  return (
    <SheetIcon>
      <path d="M2.5 6.5h11" />
      <path d="M2.5 10.5h11" />
      <path d="M6 8.5h4" />
    </SheetIcon>
  );
}

export function IconInsertColLeft() {
  return (
    <SheetIcon>
      <path d="M6.5 2.5v11" />
      <path d="M3.5 8h6" />
      <path d="M5.5 6 3.5 8l2 2" />
      <path d="M10.5 2.5v11" />
    </SheetIcon>
  );
}

export function IconInsertColRight() {
  return (
    <SheetIcon>
      <path d="M5.5 2.5v11" />
      <path d="M9.5 2.5v11" />
      <path d="M7.5 6v6" />
      <path d="M9.5 10 11.5 8l-2-2" />
    </SheetIcon>
  );
}

export function IconDeleteCol() {
  return (
    <SheetIcon>
      <path d="M5.5 2.5v11" />
      <path d="M10.5 2.5v11" />
      <path d="M7.5 6.5v3" />
    </SheetIcon>
  );
}

interface SheetToolButtonProps {
  label: string;
  disabled?: boolean;
  onClick?: () => void;
  children: ReactNode;
}

export function SheetToolButton({ label, disabled, onClick, children }: SheetToolButtonProps) {
  return (
    <button
      type="button"
      className="dash-sheet-tool-btn"
      disabled={disabled}
      onClick={onClick}
      title={label}
      aria-label={label}
    >
      {children}
    </button>
  );
}

export function SheetToolDivider() {
  return <span className="dash-sheet-tool-divider" aria-hidden />;
}
