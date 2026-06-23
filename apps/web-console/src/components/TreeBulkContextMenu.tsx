import { useEffect, useRef } from "react";
import type { ObjectSummary } from "../types";
import { useTreeBulkActions, type TreeBulkActionsConfig } from "../hooks/useTreeBulkActions";

export interface TreeContextMenuState {
  x: number;
  y: number;
}

interface TreeBulkContextMenuProps extends TreeBulkActionsConfig {
  menu: TreeContextMenuState | null;
  onClose: () => void;
}

export default function TreeBulkContextMenu({
  menu,
  onClose,
  ...config
}: TreeBulkContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);
  const actions = useTreeBulkActions(config);

  useEffect(() => {
    if (!menu) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [menu, onClose]);

  useEffect(() => {
    if (!menu || !menuRef.current) {
      return;
    }
    const rect = menuRef.current.getBoundingClientRect();
    const padding = 8;
    let x = menu.x;
    let y = menu.y;
    if (rect.right > window.innerWidth - padding) {
      x = Math.max(padding, window.innerWidth - rect.width - padding);
    }
    if (rect.bottom > window.innerHeight - padding) {
      y = Math.max(padding, window.innerHeight - rect.height - padding);
    }
    if (x !== menu.x || y !== menu.y) {
      menuRef.current.style.left = `${x}px`;
      menuRef.current.style.top = `${y}px`;
    }
  }, [menu]);

  if (!menu) {
    return null;
  }

  const item = (
    label: string,
    onClick: () => void,
    options?: { disabled?: boolean; danger?: boolean },
  ) => (
    <button
      type="button"
      role="menuitem"
      className={`tree-context-menu-item${options?.danger ? " danger" : ""}`}
      disabled={options?.disabled}
      onClick={() => {
        onClick();
        onClose();
      }}
    >
      {label}
    </button>
  );

  return (
    <>
      <div
        className="tree-context-menu-backdrop"
        onClick={onClose}
        onContextMenu={(event) => {
          event.preventDefault();
          onClose();
        }}
      />
      <div
        ref={menuRef}
        className="tree-context-menu"
        role="menu"
        style={{ left: menu.x, top: menu.y }}
        onContextMenu={(event) => event.preventDefault()}
      >
        {item("Выделить все", actions.selectAll)}
        {item("Снять выделение", actions.clearSelection, { disabled: !actions.hasSelection })}
        <div className="tree-context-menu-sep" role="separator" />
        {item("Удалить объекты", actions.deleteSelected, {
          disabled: !actions.hasSelection || actions.isDeleting,
          danger: true,
        })}
        {item("Убрать из группы", actions.removeFromGroup, {
          disabled: !actions.hasGroupRefs || actions.isRemovingFromGroup,
        })}
        <div className="tree-context-menu-group">
          <span className="tree-context-menu-label">В группу</span>
          {actions.canonicalPaths.length === 0 ? (
            <button type="button" className="tree-context-menu-item" disabled>
              Нет выделенных объектов
            </button>
          ) : actions.visualGroups.length === 0 ? (
            <button type="button" className="tree-context-menu-item" disabled>
              Нет групп (VISUAL_GROUP)
            </button>
          ) : (
            actions.visualGroups.map((group: ObjectSummary) => (
              <button
                key={group.path}
                type="button"
                role="menuitem"
                className="tree-context-menu-item nested"
                disabled={actions.isAddingToGroup}
                onClick={() => {
                  actions.addToGroup(group.path);
                  onClose();
                }}
              >
                {group.displayName}
              </button>
            ))
          )}
        </div>
      </div>
    </>
  );
}
