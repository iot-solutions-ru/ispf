import type { ReactNode } from "react";
import { EMPTY_MIMIC_HOST_SESSION, MimicHostContext } from "./useMimicHostSession";
import type { MimicHostSession } from "./useMimicHostSession";

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

