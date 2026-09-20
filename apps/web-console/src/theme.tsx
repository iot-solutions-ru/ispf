import type { ReactNode } from "react";
import { ThemeContext } from "./useTheme";
import type { ThemeContextValue } from "./useTheme";

export function ThemeProvider({
  value,
  children,
}: {
  value: ThemeContextValue;
  children: ReactNode;
}) {
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

