// Extracted from MimicHostContext.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import { createContext, useContext } from "react";

/** Minimal host session for mimic binding resolution (selection/params only). */
export type MimicHostSession = {
  selection: Record<string, string>;
  params: Record<string, unknown>;
};

export const EMPTY_MIMIC_HOST_SESSION: MimicHostSession = {
  selection: {},
  params: {},
};

export const MimicHostContext = createContext<MimicHostSession>(EMPTY_MIMIC_HOST_SESSION);

export function useMimicHostSession(): MimicHostSession {
  return useContext(MimicHostContext);
}
