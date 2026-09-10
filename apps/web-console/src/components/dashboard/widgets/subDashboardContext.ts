/** Default is share parent session. Only `false` isolates nested selection/params. */
export function subDashboardSharesParentContext(inheritContext?: boolean): boolean {
  return inheritContext !== false;
}
