import type { ReactNode } from "react";

interface EmptyStateProps {
  title: string;
  hint?: string;
  action?: ReactNode;
  secondaryAction?: ReactNode;
  className?: string;
}

export default function EmptyState({
  title,
  hint,
  action,
  secondaryAction,
  className = "",
}: EmptyStateProps) {
  return (
    <div className={`empty-state ${className}`.trim()}>
      <p className="empty-state-title">{title}</p>
      {hint && <p className="empty-state-hint op-muted">{hint}</p>}
      {(action || secondaryAction) && (
        <div className="empty-state-actions">
          {action}
          {secondaryAction}
        </div>
      )}
    </div>
  );
}
