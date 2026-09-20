// Extracted from JournalViewShell.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).

export type JournalViewMode = "live" | "history";

export const JOURNAL_VIEW_MODES: readonly JournalViewMode[] = ["live", "history"];
