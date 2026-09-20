// Extracted from UserTimeZoneContext.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import { createContext, useContext } from "react";

interface UserTimeZoneContextValue {
  timeZone: string;
  setTimeZone: (timeZone: string) => Promise<void>;
  formatDate: (value: string | number | Date | null | undefined) => string;
}

export const UserTimeZoneContext = createContext<UserTimeZoneContextValue | null>(null);

export function useUserTimeZone(): UserTimeZoneContextValue {
  const ctx = useContext(UserTimeZoneContext);
  if (!ctx) {
    throw new Error("useUserTimeZone must be used within UserTimeZoneProvider");
  }
  return ctx;
}

/** Safe hook for components that may render outside provider (e.g. tests). */
export function useOptionalUserTimeZone(): UserTimeZoneContextValue | null {
  return useContext(UserTimeZoneContext);
}
