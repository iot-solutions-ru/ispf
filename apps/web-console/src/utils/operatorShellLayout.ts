/**
 * Operator sidebar is always an overlay drawer (slides over the dashboard).
 * Narrow viewports also lock body scroll while open.
 */
export const OPERATOR_SIDEBAR_BODY_LOCK_MAX_PX = 900;

/** @deprecated Prefer always-on drawer; kept for call-site compatibility. */
export const OPERATOR_SIDEBAR_DESKTOP_MIN_PX = OPERATOR_SIDEBAR_BODY_LOCK_MAX_PX + 1;

/** Sidebar always overlays the dashboard (does not shrink the grid). */
export function shouldUseOperatorSidebarDrawer(_viewportWidth?: number): boolean {
  return true;
}

/** Lock document scroll only on compact viewports while the drawer is open. */
export function shouldLockBodyForOperatorSidebar(viewportWidth: number): boolean {
  return viewportWidth <= OPERATOR_SIDEBAR_BODY_LOCK_MAX_PX;
}
