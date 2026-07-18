import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";

export interface WorkspaceTabItem {
  id: string;
  label: ReactNode;
  /** Plain-text label for aria/title when `label` is not a string. */
  title?: string;
  active: boolean;
  onClick: () => void;
  onClose?: () => void;
  testId?: string;
}

interface WorkspaceTabsProps {
  tabs: WorkspaceTabItem[];
}

export default function WorkspaceTabs({ tabs }: WorkspaceTabsProps) {
  const { t } = useTranslation("shell");
  const containerRef = useRef<HTMLElement | null>(null);
  const overflowRef = useRef<HTMLDivElement>(null);
  const [overflowIndex, setOverflowIndex] = useState(-1);
  const [overflowOpen, setOverflowOpen] = useState(false);
  const tabSignature = tabs.map((tab) => tab.id).join("|");

  const measure = useCallback(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const items = Array.from(container.querySelectorAll<HTMLElement>("[data-workspace-tab]"));
    if (items.length === 0) {
      setOverflowIndex(-1);
      return;
    }
    const moreReserve = 72;
    const available = container.clientWidth - moreReserve;
    let accumulated = 0;
    let found = -1;
    for (let i = 0; i < items.length; i++) {
      accumulated += items[i].getBoundingClientRect().width;
      if (accumulated > available) {
        found = Math.max(1, i);
        break;
      }
    }
    setOverflowIndex((prev) => (prev === found ? prev : found));
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const raf = requestAnimationFrame(measure);
    const observer = new ResizeObserver(() => {
      requestAnimationFrame(measure);
    });
    observer.observe(container);
    return () => {
      cancelAnimationFrame(raf);
      observer.disconnect();
    };
  }, [measure, tabSignature]);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (overflowRef.current && !overflowRef.current.contains(event.target as Node)) {
        setOverflowOpen(false);
      }
    }
    if (overflowOpen) {
      window.addEventListener("mousedown", handleClickOutside);
      return () => window.removeEventListener("mousedown", handleClickOutside);
    }
  }, [overflowOpen]);

  useEffect(() => {
    setOverflowOpen(false);
  }, [tabSignature]);

  const titleOf = (tab: WorkspaceTabItem) =>
    tab.title ?? (typeof tab.label === "string" ? tab.label : tab.id);

  return (
    <nav className="workspace-tabs" ref={containerRef}>
      {tabs.map((tab, index) => {
        const inOverflow = overflowIndex >= 0 && index >= overflowIndex;
        return (
          <button
            key={tab.id}
            type="button"
            data-workspace-tab
            data-testid={tab.testId}
            className={`${tab.onClose ? "editor-tab " : ""}${tab.active ? "active" : ""}${inOverflow ? " workspace-tab--overflow" : ""}`}
            onClick={tab.onClick}
            aria-label={titleOf(tab)}
            title={titleOf(tab)}
            aria-hidden={inOverflow || undefined}
            tabIndex={inOverflow ? -1 : undefined}
          >
            <span>{tab.label}</span>
            {tab.onClose && (
              <span
                className="tab-close"
                role="button"
                aria-label={t("admin.tab.close", { title: titleOf(tab) })}
                onClick={(e) => {
                  e.stopPropagation();
                  tab.onClose?.();
                }}
              >
                ×
              </span>
            )}
          </button>
        );
      })}
      {overflowIndex >= 0 && (
        <div className="tab-overflow" ref={overflowRef}>
          <button
            type="button"
            className={`tab-overflow-toggle ${overflowOpen ? "active" : ""}`}
            onClick={() => setOverflowOpen((v) => !v)}
            aria-label={t("admin.tab.overflow")}
            aria-expanded={overflowOpen}
          >
            {t("admin.tab.more")}{" "}
            <span className="tab-overflow-count">{tabs.length - overflowIndex}</span>
          </button>
          {overflowOpen && (
            <div className="tab-overflow-menu" role="menu">
              {tabs.slice(overflowIndex).map((tab) => (
                <div
                  key={tab.id}
                  data-testid={tab.testId}
                  className={`tab-overflow-item ${tab.active ? "active" : ""}`}
                  onClick={() => {
                    tab.onClick();
                    setOverflowOpen(false);
                  }}
                  role="menuitem"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      tab.onClick();
                      setOverflowOpen(false);
                    }
                  }}
                >
                  <span>{tab.label}</span>
                  {tab.onClose && (
                    <span
                      className="tab-overflow-close"
                      role="button"
                      aria-label={t("admin.tab.close", { title: titleOf(tab) })}
                      onClick={(e) => {
                        e.stopPropagation();
                        tab.onClose?.();
                      }}
                    >
                      ×
                    </span>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </nav>
  );
}
