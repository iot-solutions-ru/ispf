import type { ReactNode } from "react";

interface PlatformSqlEditorShellProps {
  title: string;
  path: string;
  subtitle?: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
  toolbar?: ReactNode;
  children: ReactNode;
}

export default function PlatformSqlEditorShell({
  title,
  path,
  subtitle,
  onClose,
  onOpenProperties,
  toolbar,
  children,
}: PlatformSqlEditorShellProps) {
  return (
    <div className="dashboard-builder report-builder">
      <header className="dashboard-builder-header">
        <div>
          <h2>{title}</h2>
          {subtitle && <p className="hint">{subtitle}</p>}
          <p className="hint mono small">{path}</p>
        </div>
        <div className="dashboard-builder-actions">
          {toolbar}
          {onOpenProperties && (
            <button type="button" className="btn" onClick={onOpenProperties}>
              Свойства объекта
            </button>
          )}
          {onClose && (
            <button type="button" className="btn" onClick={onClose}>
              Закрыть
            </button>
          )}
        </div>
      </header>
      <div className="report-builder-body">{children}</div>
    </div>
  );
}
