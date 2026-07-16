import { createContext, useContext, type ReactNode } from "react";

/** Minimal host session for mimic binding resolution (selection/params only). */
export type MimicHostSession = {
  selection: Record<string, string>;
  params: Record<string, unknown>;
};

export const EMPTY_MIMIC_HOST_SESSION: MimicHostSession = {
  selection: {},
  params: {},
};

const MimicHostContext = createContext<MimicHostSession>(EMPTY_MIMIC_HOST_SESSION);

export function MimicHostProvider({
  session,
  children,
}: {
  session?: MimicHostSession;
  children: ReactNode;
}) {
  return (
    <MimicHostContext.Provider value={session ?? EMPTY_MIMIC_HOST_SESSION}>
      {children}
    </MimicHostContext.Provider>
  );
}

export function useMimicHostSession(): MimicHostSession {
  return useContext(MimicHostContext);
}
