import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useTranslation } from "react-i18next";
import { fetchAuthMe, updateAuthTimeZone } from "../api";
import {
  detectBrowserTimeZone,
  persistTimeZone,
  readStoredTimeZone,
} from "../i18n/timezones";
import { getStoredSession } from "../auth/session";

interface UserTimeZoneContextValue {
  timeZone: string;
  setTimeZone: (timeZone: string) => Promise<void>;
  formatDate: (value: string | number | Date | null | undefined) => string;
}

const UserTimeZoneContext = createContext<UserTimeZoneContextValue | null>(null);

function initialTimeZone(): string {
  return readStoredTimeZone() ?? detectBrowserTimeZone();
}

export function UserTimeZoneProvider({ children }: { children: ReactNode }) {
  const { i18n } = useTranslation();
  const [timeZone, setTimeZoneState] = useState(initialTimeZone);

  useEffect(() => {
    const session = getStoredSession();
    if (!session?.token) {
      return;
    }
    void fetchAuthMe()
      .then((me) => {
        if (me.timeZone) {
          setTimeZoneState(me.timeZone);
          persistTimeZone(me.timeZone);
        }
      })
      .catch(() => {
        // keep local/browser fallback
      });
  }, []);

  const setTimeZone = useCallback(async (next: string) => {
    const normalized = next.trim() || "UTC";
    setTimeZoneState(normalized);
    persistTimeZone(normalized);
    const session = getStoredSession();
    if (session?.token) {
      try {
        const result = await updateAuthTimeZone(normalized);
        setTimeZoneState(result.timeZone);
        persistTimeZone(result.timeZone);
      } catch {
        // local preference still applied for display
      }
    }
  }, []);

  const formatDate = useCallback(
    (value: string | number | Date | null | undefined) => {
      if (value == null || value === "") {
        return "";
      }
      const date = value instanceof Date ? value : new Date(value);
      if (!Number.isFinite(date.getTime())) {
        return "";
      }
      try {
        return new Intl.DateTimeFormat(i18n.language, {
          timeZone,
          dateStyle: "short",
          timeStyle: "medium",
        }).format(date);
      } catch {
        return date.toLocaleString(i18n.language);
      }
    },
    [i18n.language, timeZone]
  );

  const value = useMemo(
    () => ({
      timeZone,
      setTimeZone,
      formatDate,
    }),
    [formatDate, setTimeZone, timeZone]
  );

  return <UserTimeZoneContext.Provider value={value}>{children}</UserTimeZoneContext.Provider>;
}

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
