/** Viewport width at which operator sidebar is docked (desktop) vs drawer (mobile/tablet). */
export const OPERATOR_SIDEBAR_DESKTOP_MIN_PX = 901;

export function shouldUseOperatorSidebarDrawer(viewportWidth: number): boolean {
  return viewportWidth < OPERATOR_SIDEBAR_DESKTOP_MIN_PX;
}
