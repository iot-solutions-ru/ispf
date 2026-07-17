interface PathBreadcrumbProps {
  path: string;
  onSelectPath?: (path: string) => void;
  className?: string;
}

export default function PathBreadcrumb({ path, onSelectPath, className = "" }: PathBreadcrumbProps) {
  const crumbs = path.split(".").filter(Boolean);
  if (crumbs.length === 0) {
    return null;
  }

  return (
    <nav className={`breadcrumb ${className}`.trim()} aria-label="Breadcrumb">
      {crumbs.map((part, index) => {
        const crumbPath = crumbs.slice(0, index + 1).join(".");
        const isLast = index === crumbs.length - 1;
        const clickable = Boolean(onSelectPath) && !isLast;
        return (
          <span
            key={crumbPath}
            className={`crumb ${isLast ? "crumb--current" : ""}${clickable ? " crumb--link" : ""}`}
            onClick={() => {
              if (clickable) {
                onSelectPath?.(crumbPath);
              }
            }}
            onKeyDown={(e) => {
              if (clickable && (e.key === "Enter" || e.key === " ")) {
                e.preventDefault();
                onSelectPath?.(crumbPath);
              }
            }}
            role={clickable ? "button" : undefined}
            tabIndex={clickable ? 0 : undefined}
            aria-current={isLast ? "page" : undefined}
            title={crumbPath}
          >
            {index > 0 && <span className="crumb-sep">/</span>}
            <span>{part}</span>
          </span>
        );
      })}
    </nav>
  );
}
