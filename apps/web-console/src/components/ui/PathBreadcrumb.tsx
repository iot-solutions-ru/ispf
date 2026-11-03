import { Fragment } from "react";
import { useTranslation } from "react-i18next";

interface PathBreadcrumbProps {
  path: string;
  onSelectPath?: (path: string) => void;
  className?: string;
}

export default function PathBreadcrumb({ path, onSelectPath, className = "" }: PathBreadcrumbProps) {
  const { t } = useTranslation("common");
  const crumbs = path.split(".").filter(Boolean);
  if (crumbs.length === 0) {
    return null;
  }

  return (
    <nav
      className={`breadcrumb ${className}`.trim()}
      aria-label={`${t("field.path")}: ${path}`}
    >
      {crumbs.map((part, index) => {
        const crumbPath = crumbs.slice(0, index + 1).join(".");
        const isLast = index === crumbs.length - 1;
        const clickable = Boolean(onSelectPath) && !isLast;
        return (
          <Fragment key={crumbPath}>
            {index > 0 ? "." : null}
            <span
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
              {part}
            </span>
          </Fragment>
        );
      })}
    </nav>
  );
}
