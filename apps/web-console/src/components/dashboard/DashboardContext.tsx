import { createContext, useContext, useMemo, useState, type ReactNode } from "react";

export interface DashboardContextValue {
  selection: Record<string, string>;
  setSelection: (key: string, path: string) => void;
}

const DashboardContext = createContext<DashboardContextValue | null>(null);

export function DashboardProvider({ children }: { children: ReactNode }) {
  const [selection, setSelectionState] = useState<Record<string, string>>({});

  const value = useMemo(
    () => ({
      selection,
      setSelection: (key: string, path: string) => {
        setSelectionState((prev) => ({ ...prev, [key]: path }));
      },
    }),
    [selection]
  );

  return <DashboardContext.Provider value={value}>{children}</DashboardContext.Provider>;
}

export function useDashboardContext(): DashboardContextValue {
  const ctx = useContext(DashboardContext);
  if (!ctx) {
    return {
      selection: {},
      setSelection: () => {},
    };
  }
  return ctx;
}
