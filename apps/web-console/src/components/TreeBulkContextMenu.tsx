import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  canCreateChildAt,
  canCreateVisualGroupAt,
  createContextMenuLabel,
  resolveVisualGroupParentPath,
} from "../utils/createObjectMode";
import { useTreeBulkActions, type TreeBulkActionsConfig } from "../hooks/useTreeBulkActions";

export interface TreeContextMenuState {
  x: number;
  y: number;
  contextPath: string | null;
  contextObjectType?: TreeBulkActionsConfig["contextObjectType"];
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
  const { t } = useTranslation("explorer");
  const menuRef = useRef<HTMLDivElement>(null);
  const [selectedGroupPath, setSelectedGroupPath] = useState("");
  const actions = useTreeBulkActions({ ...config, contextPath: menu?.contextPath, contextObjectType: menu?.contextObjectType });

  useEffect(() => {
    setSelectedGroupPath("");
  }, [menu?.x, menu?.y, menu?.contextPath]);

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
  }, [menu, actions.visualGroups.length]);

  if (!menu) {
    return null;
  }

  const canCreateChild = Boolean(
    menu.contextPath
    && config.onCreateChild
    && canCreateChildAt(menu.contextPath, menu.contextObjectType),
  );
  const visualGroupParentPath = menu.contextPath
    ? resolveVisualGroupParentPath(menu.contextPath, menu.contextObjectType)
    : null;
  const canCreateVisualGroup = Boolean(
    visualGroupParentPath
    && config.onCreateVisualGroup
    && canCreateVisualGroupAt(menu.contextPath!, menu.contextObjectType),
  );
  const createLabel = menu.contextPath ? createContextMenuLabel(menu.contextPath) : "";

  const addToGroupDisabledReason = !actions.canAddToGroup
    ? actions.hasMixedCatalogSelection
      ? t("bulk.mixedCatalogSelection")
      : t("bulk.noSelection")
    : actions.visualGroups.length === 0
      ? t("bulk.noVisualGroupsInCatalog")
      : "";

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

  const handleAddToGroup = () => {
    if (!selectedGroupPath || actions.isAddingToGroup) {
      return;
    }
    actions.addToGroup(selectedGroupPath);
    onClose();
  };

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
        {canCreateChild && config.onCreateChild && menu.contextPath && item(
          createLabel,
          () => config.onCreateChild!(menu.contextPath!),
        )}
        {canCreateVisualGroup && config.onCreateVisualGroup && visualGroupParentPath && item(
          t("contextMenu.create.visual-group"),
          () => config.onCreateVisualGroup!(visualGroupParentPath),
        )}
        {(canCreateChild || canCreateVisualGroup) && (
          <div className="tree-context-menu-sep" role="separator" />
        )}
        {item(t("bulk.selectAll"), actions.selectAll)}
        {item(t("bulk.clearSelection"), actions.clearSelection, { disabled: !actions.hasSelection })}
        <div className="tree-context-menu-sep" role="separator" />
        {item(t("bulk.deleteObjects"), actions.deleteSelected, {
          disabled:
            !actions.hasSelection
            || actions.isDeleting
            || actions.deletablePaths.length === 0
            || actions.hasNonDeletableSelection,
          danger: true,
        })}
        {item(t("bulk.removeFromGroup"), actions.removeFromGroup, {
          disabled: !actions.hasGroupRefs || actions.isRemovingFromGroup,
        })}
        <div className="tree-context-menu-sep" role="separator" />
        <div className="tree-context-menu-field" role="none">
          <span className="tree-context-menu-label">{t("bulk.addToGroup")}</span>
          <select
            className="tree-context-menu-select"
            value={selectedGroupPath}
            disabled={
              !actions.canAddToGroup
              || actions.visualGroups.length === 0
              || actions.isAddingToGroup
            }
            onChange={(event) => setSelectedGroupPath(event.target.value)}
          >
            <option value="">
              {addToGroupDisabledReason || t("bulk.chooseGroup")}
            </option>
            {actions.visualGroups.map((group) => (
              <option key={group.path} value={group.path}>
                {group.displayName}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="tree-context-menu-item tree-context-menu-apply"
            disabled={
              !selectedGroupPath
              || actions.isAddingToGroup
              || !actions.canAddToGroup
            }
            onClick={handleAddToGroup}
          >
            {actions.isAddingToGroup ? t("bulk.addingToGroup") : t("bulk.addToGroupApply")}
          </button>
        </div>
      </div>
    </>
  );
}
