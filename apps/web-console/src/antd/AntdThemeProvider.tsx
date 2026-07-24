import { ConfigProvider, theme as antdTheme } from "antd";
import type { ThemeConfig } from "antd";
import type { ReactNode } from "react";
import { useMemo } from "react";
import { useTheme, type ResolvedTheme } from "../theme";

/** Mirrors `tokens-shell.css` — do not read CSS vars at render (theme attr updates after paint). */
const SHELL: Record<
  ResolvedTheme,
  {
    bg: string;
    surface: string;
    surfaceElevated: string;
    surface2: string;
    bgHover: string;
    border: string;
    text: string;
    textMuted: string;
    accent: string;
    accentHover: string;
    accentSoft: string;
    accentFg: string;
    danger: string;
    success: string;
    warn: string;
  }
> = {
  dark: {
    bg: "#0d1117",
    surface: "#161b22",
    surfaceElevated: "#1b212a",
    surface2: "#21262d",
    bgHover: "#1c2128",
    border: "#30363d",
    text: "#e6edf3",
    textMuted: "#9da5af",
    accent: "#2f81f7",
    accentHover: "#58a6ff",
    accentSoft: "rgba(47, 129, 247, 0.12)",
    accentFg: "#ffffff",
    danger: "#f85149",
    success: "#3fb950",
    warn: "#d29922",
  },
  light: {
    bg: "#f3f5f8",
    surface: "#ffffff",
    surfaceElevated: "#ffffff",
    surface2: "#eef2f7",
    bgHover: "#e8edf4",
    border: "#cfd7e3",
    text: "#1f2937",
    textMuted: "#5c6578",
    accent: "#2563eb",
    accentHover: "#1d4ed8",
    accentSoft: "rgba(37, 99, 235, 0.1)",
    accentFg: "#ffffff",
    danger: "#dc2626",
    success: "#15803d",
    warn: "#b45309",
  },
};

function buildTheme(mode: ResolvedTheme): ThemeConfig {
  const c = SHELL[mode];
  const isDark = mode === "dark";

  return {
    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: c.accent,
      colorPrimaryHover: c.accentHover,
      colorPrimaryActive: c.accentHover,
      colorInfo: c.accent,
      colorSuccess: c.success,
      colorWarning: c.warn,
      colorError: c.danger,
      colorText: c.text,
      colorTextSecondary: c.textMuted,
      colorTextTertiary: c.textMuted,
      colorTextQuaternary: c.textMuted,
      colorBgBase: c.bg,
      colorBgContainer: c.surface,
      colorBgElevated: c.surfaceElevated,
      colorBgLayout: c.bg,
      colorBgSpotlight: c.surface2,
      colorFill: c.bgHover,
      colorFillSecondary: c.surface2,
      colorFillTertiary: c.bgHover,
      colorFillQuaternary: c.surface2,
      colorBorder: c.border,
      colorBorderSecondary: c.border,
      colorSplit: c.border,
      colorLink: c.accent,
      colorLinkHover: c.accentHover,
      borderRadius: 6,
      borderRadiusLG: 8,
      borderRadiusSM: 4,
      fontFamily: '"Segoe UI", system-ui, -apple-system, sans-serif',
      fontFamilyCode: 'ui-monospace, "Cascadia Code", Consolas, monospace',
      controlHeight: 32,
      controlHeightSM: 24,
    },
    components: {
      Table: {
        headerBg: c.surface2,
        headerColor: c.textMuted,
        rowHoverBg: c.bgHover,
        borderColor: c.border,
        colorBgContainer: c.surface,
      },
      Button: {
        primaryColor: c.accentFg,
      },
      Modal: {
        contentBg: c.surfaceElevated,
        headerBg: c.surfaceElevated,
        footerBg: c.surfaceElevated,
      },
      Tag: {
        defaultBg: c.surface2,
        defaultColor: c.text,
      },
      Alert: {
        colorInfoBg: c.accentSoft,
        colorInfoBorder: c.accent,
      },
      Input: {
        colorBgContainer: c.surface,
        activeBorderColor: c.accent,
        hoverBorderColor: c.accentHover,
      },
      Select: {
        colorBgContainer: c.surface,
        optionSelectedBg: c.accentSoft,
      },
    },
  };
}

/**
 * POC: Ant Design with ISPF shell palette (keyed by resolvedTheme, not live CSS vars).
 */
export default function AntdThemeProvider({ children }: { children: ReactNode }) {
  const { resolvedTheme } = useTheme();
  const theme = useMemo(() => buildTheme(resolvedTheme), [resolvedTheme]);

  return <ConfigProvider theme={theme}>{children}</ConfigProvider>;
}
