import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

interface PlatformSqlEditorShellProps {
  title: string;
  path: string;
  subtitle?: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
  toolbar?: ReactNode;
  children: ReactNode;
  /** Fill available editor viewport and scroll inside the body. */
  fillHeight?: boolean;
  className?: string;
}

export default function PlatformSqlEditorShell({
  title,
  path,
  subtitle,
  onClose,
  onOpenProperties,
  toolbar,
  children,
  fillHeight = false,
  className = "",
}: PlatformSqlEditorShellProps) {
  const { t } = useTranslation(["common", "inspector"]);

  const rootClass = [
    "platform-sql-editor",
    "report-builder",
    fillHeight ? "platform-sql-editor--fill" : "",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div className={rootClass}>
      <header className="platform-sql-editor-header">
        <div className="platform-sql-editor-header-text">
          <h2 className="platform-sql-editor-title">{title}</h2>
          {subtitle && <p className="platform-sql-editor-subtitle">{subtitle}</p>}
          <p className="platform-sql-editor-path mono" title={path}>
            {path}
          </p>
        </div>
        <div className="platform-sql-editor-actions">
          {toolbar}
          {onOpenProperties && (
            <button type="button" className="btn" onClick={onOpenProperties}>
              {t("inspector:tab.general")}
            </button>
          )}
          {onClose && (
            <button type="button" className="btn" onClick={onClose}>
              {t("common:action.close")}
            </button>
          )}
        </div>
      </header>
      <div className="platform-sql-editor-body report-builder-body">{children}</div>
    </div>
  );
}
