/** Options for the SQL binding variable select. The pinned name stays in the list so choosing another variable does not rebuild the options while the dropdown is open. */
export function sqlBindingVariableOptions(
  variableNames: readonly string[],
  pinned: string,
): { value: string; label: string }[] {
  const names = [...variableNames];
  const extra = pinned.trim();
  if (extra && !names.includes(extra)) {
    names.unshift(extra);
  }
  return names.map((name) => ({ value: name, label: name }));
}
