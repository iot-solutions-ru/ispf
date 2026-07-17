import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import ModalPortal from "../ui/ModalPortal";
import type { ObjectSummary } from "../types";

export type CommandPaletteWorkspace = "explorer" | "system" | "ai-studio";

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
  objects: ObjectSummary[];
  canConfigure: boolean;
  isAdmin: boolean;
  onSelectPath: (path: string) => void;
  onOpenWorkspace: (tab: CommandPaletteWorkspace) => void;
  onCreate?: () => void;
}

type PaletteItem = {
  id: string;
  label: string;
  hint?: string;
  group: "actions" | "objects";
  run: () => void;
};

export default function CommandPalette({
  open,
  onClose,
  objects,
  canConfigure,
  isAdmin,
  onSelectPath,
  onOpenWorkspace,
  onCreate,
}: CommandPaletteProps) {
  const { t } = useTranslation(["shell", "common", "explorer"]);
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    setQuery("");
    setActiveIndex(0);
    const id = window.requestAnimationFrame(() => inputRef.current?.focus());
    return () => window.cancelAnimationFrame(id);
  }, [open]);

  const items = useMemo(() => {
    const q = query.trim().toLowerCase();
    const actions: PaletteItem[] = [
      {
        id: "ws-explorer",
        label: t("shell:admin.tab.explorer"),
        hint: t("shell:commandPalette.hintExplorer"),
        group: "actions",
        run: () => onOpenWorkspace("explorer"),
      },
    ];
    if (isAdmin) {
      actions.push({
        id: "ws-system",
        label: t("shell:admin.tab.system"),
        hint: t("shell:commandPalette.hintSystem"),
        group: "actions",
        run: () => onOpenWorkspace("system"),
      });
    }
    if (canConfigure) {
      actions.push({
        id: "ws-ai",
        label: t("shell:admin.tab.aiStudio"),
        hint: t("shell:commandPalette.hintAi"),
        group: "actions",
        run: () => onOpenWorkspace("ai-studio"),
      });
      if (onCreate) {
        actions.push({
          id: "create",
          label: t("shell:commandPalette.createObject"),
          hint: t("shell:commandPalette.hintCreate"),
          group: "actions",
          run: () => onCreate(),
        });
      }
    }

    const filteredActions = q
      ? actions.filter(
          (item) =>
            item.label.toLowerCase().includes(q)
            || (item.hint ?? "").toLowerCase().includes(q),
        )
      : actions;

    const objectMatches = objects
      .filter((obj) => {
        if (!q) {
          return obj.type === "DASHBOARD" || obj.path.split(".").length <= 4;
        }
        return (
          obj.path.toLowerCase().includes(q)
          || obj.displayName.toLowerCase().includes(q)
          || obj.type.toLowerCase().includes(q)
        );
      })
      .slice(0, 24)
      .map((obj) => ({
        id: `obj-${obj.path}`,
        label: obj.displayName || obj.path,
        hint: `${obj.type} · ${obj.path}`,
        group: "objects" as const,
        run: () => onSelectPath(obj.path),
      }));

    return [...filteredActions, ...objectMatches];
  }, [canConfigure, isAdmin, objects, onCreate, onOpenWorkspace, onSelectPath, query, t]);

  useEffect(() => {
    setActiveIndex(0);
  }, [query]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key === "ArrowDown") {
        event.preventDefault();
        setActiveIndex((i) => Math.min(i + 1, Math.max(0, items.length - 1)));
        return;
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        setActiveIndex((i) => Math.max(i - 1, 0));
        return;
      }
      if (event.key === "Enter" && items[activeIndex]) {
        event.preventDefault();
        items[activeIndex].run();
        onClose();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [activeIndex, items, onClose, open]);

  if (!open) {
    return null;
  }

  return (
    <ModalPortal>
      <div className="command-palette-backdrop" role="presentation" onClick={onClose}>
        <div
          className="command-palette"
          role="dialog"
          aria-modal="true"
          aria-label={t("shell:commandPalette.title")}
          onClick={(e) => e.stopPropagation()}
        >
          <input
            ref={inputRef}
            type="search"
            className="command-palette-input"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t("shell:commandPalette.placeholder")}
            aria-label={t("shell:commandPalette.placeholder")}
          />
          <ul className="command-palette-list" role="listbox">
            {items.length === 0 && (
              <li className="command-palette-empty op-muted">{t("common:empty.noMatches")}</li>
            )}
            {items.map((item, index) => (
              <li key={item.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={index === activeIndex}
                  className={`command-palette-item${index === activeIndex ? " active" : ""}`}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => {
                    item.run();
                    onClose();
                  }}
                >
                  <span className="command-palette-item-label">{item.label}</span>
                  {item.hint && <span className="command-palette-item-hint">{item.hint}</span>}
                </button>
              </li>
            ))}
          </ul>
          <footer className="command-palette-footer op-muted">
            {t("shell:commandPalette.footer")}
          </footer>
        </div>
      </div>
    </ModalPortal>
  );
}
