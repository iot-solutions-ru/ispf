export const PROCESS_PROGRAMS_ROOT = "root.platform.process-programs";

export function isProcessProgramsRoot(path: string): boolean {
  return path === PROCESS_PROGRAMS_ROOT;
}

export function isProcessProgramPath(path: string): boolean {
  return path.startsWith(`${PROCESS_PROGRAMS_ROOT}.`);
}
