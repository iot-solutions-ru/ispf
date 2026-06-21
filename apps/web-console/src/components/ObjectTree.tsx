import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { TreeNode } from "../types";
import { parentObjectPath } from "../utils/tree";
import {
  ancestorPaths,
  defaultExpandedPaths,
  readExpandedPaths,
  writeExpandedPaths,
} from "../utils/treeExpanded";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";

interface ObjectTreeProps {
  nodes: TreeNode[];
  selectedPath: string | null;
  onSelect: (path: string) => void;
  onOpenEditor?: (path: string) => void;
  canReorder?: boolean;
  onReorder?: (parentPath: string, orderedPaths: string[]) => void;
}

interface TreeExpandedContextValue {
  isExpanded: (path: string) => boolean;
  toggle: (path: string) => void;
}

interface TreeDragContextValue {
  canReorder: boolean;
  draggingPath: string | null;
  dropTargetPath: string | null;
  startDrag: (path: string) => void;
  endDrag: () => void;
  setDropTarget: (path: string | null) => void;
  reorder: (parentPath: string, orderedPaths: string[]) => void;
  siblingPaths: (parentPath: string, nodes: TreeNode[]) => string[];
}

const TreeExpandedContext = createContext<TreeExpandedContextValue | null>(null);
const TreeDragContext = createContext<TreeDragContextValue | null>(null);

function useTreeExpanded(): TreeExpandedContextValue {
  const ctx = useContext(TreeExpandedContext);
  if (!ctx) {
    throw new Error("TreeExpandedContext missing");
  }
  return ctx;
}

function useTreeDrag(): TreeDragContextValue | null {
  return useContext(TreeDragContext);
}

function collectSiblingPaths(parentPath: string, nodes: TreeNode[]): string[] {
  if (parentPath === "") {
    return nodes.map((node) => node.object.path);
  }
  for (const node of nodes) {
    if (node.object.path === parentPath) {
      return node.children.map((child) => child.object.path);
    }
    const nested = collectSiblingPaths(parentPath, node.children);
    if (nested.length > 0) {
      return nested;
    }
  }
  return [];
}

function TreeItem({
  node,
  selectedPath,
  onSelect,
  onOpenEditor,
  depth,
  rootNodes,
}: {
  node: TreeNode;
  selectedPath: string | null;
  onSelect: (path: string) => void;
  onOpenEditor?: (path: string) => void;
  depth: number;
  rootNodes: TreeNode[];
}) {
  const { isExpanded, toggle } = useTreeExpanded();
  const drag = useTreeDrag();
  const path = node.object.path;
  const parentPath = parentObjectPath(path);
  const hasChildren = node.children.length > 0;
  const expanded = isExpanded(path);
  const isSelected = selectedPath === path;
  const draggable = Boolean(
    drag?.canReorder
    && path !== "root"
    && parentPath
    && drag.siblingPaths(parentPath, rootNodes).length > 1
  );
  const isDragging = drag?.draggingPath === path;
  const isDropTarget = drag?.dropTargetPath === path && drag.draggingPath !== path;

  const handleDrop = (event: React.DragEvent) => {
    event.preventDefault();
    if (!drag?.draggingPath || !parentPath || drag.draggingPath === path) {
      drag?.endDrag();
      return;
    }
    if (parentObjectPath(drag.draggingPath) !== parentPath) {
      drag.endDrag();
      return;
    }
    const siblings = drag.siblingPaths(parentPath, rootNodes);
    const fromIndex = siblings.indexOf(drag.draggingPath);
    const toIndex = siblings.indexOf(path);
    if (fromIndex === -1 || toIndex === -1 || fromIndex === toIndex) {
      drag.endDrag();
      return;
    }
    const next = [...siblings];
    next.splice(fromIndex, 1);
    next.splice(toIndex, 0, drag.draggingPath);
    drag.reorder(parentPath, next);
    drag.endDrag();
  };

  return (
    <div className="tree-item">
      <button
        type="button"
        className={`tree-row ${isSelected ? "selected" : ""} ${isDragging ? "dragging" : ""} ${isDropTarget ? "drop-target" : ""}`}
        style={{ paddingLeft: `${depth * 16 + 8}px` }}
        draggable={draggable}
        onClick={() => onSelect(path)}
        onDoubleClick={() => onOpenEditor?.(path)}
        onDragStart={(event) => {
          if (!draggable || !drag) {
            event.preventDefault();
            return;
          }
          event.dataTransfer.setData("text/plain", path);
          event.dataTransfer.effectAllowed = "move";
          drag.startDrag(path);
        }}
        onDragOver={(event) => {
          if (!drag?.draggingPath || !parentPath) {
            return;
          }
          if (parentObjectPath(drag.draggingPath) !== parentPath) {
            return;
          }
          event.preventDefault();
          event.dataTransfer.dropEffect = "move";
          drag.setDropTarget(path);
        }}
        onDragLeave={() => {
          if (drag?.dropTargetPath === path) {
            drag.setDropTarget(null);
          }
        }}
        onDrop={handleDrop}
        onDragEnd={() => drag?.endDrag()}
      >
        <span
          className="tree-toggle"
          onClick={(e) => {
            if (hasChildren) {
              e.stopPropagation();
              toggle(path);
            }
          }}
        >
          {hasChildren ? (expanded ? "▾" : "▸") : "·"}
        </span>
        <span className="tree-icon">
          <ObjectTreeIcon
            path={node.object.path}
            type={node.object.type}
            iconId={node.object.iconId}
            federated={node.object.federated}
          />
        </span>
        <span className="tree-label">{node.object.displayName}</span>
        <span className="tree-type">{node.object.type}</span>
      </button>
      {expanded &&
        node.children.map((child) => (
          <TreeItem
            key={child.object.path}
            node={child}
            selectedPath={selectedPath}
            onSelect={onSelect}
            onOpenEditor={onOpenEditor}
            depth={depth + 1}
            rootNodes={rootNodes}
          />
        ))}
    </div>
  );
}

export default function ObjectTree({
  nodes,
  selectedPath,
  onSelect,
  onOpenEditor,
  canReorder = false,
  onReorder,
}: ObjectTreeProps) {
  const [expandedPaths, setExpandedPaths] = useState<Set<string>>(() => readExpandedPaths());
  const [defaultsSeeded, setDefaultsSeeded] = useState(() => readExpandedPaths().size > 0);
  const [draggingPath, setDraggingPath] = useState<string | null>(null);
  const [dropTargetPath, setDropTargetPath] = useState<string | null>(null);

  const persist = useCallback((next: Set<string>) => {
    setExpandedPaths(next);
    writeExpandedPaths(next);
  }, []);

  useEffect(() => {
    if (defaultsSeeded || nodes.length === 0) {
      return;
    }
    const defaults = defaultExpandedPaths(nodes);
    if (defaults.length === 0) {
      setDefaultsSeeded(true);
      return;
    }
    persist(new Set(defaults));
    setDefaultsSeeded(true);
  }, [nodes, defaultsSeeded, persist]);

  useEffect(() => {
    if (!selectedPath || selectedPath === "root") {
      return;
    }
    const ancestors = ancestorPaths(selectedPath);
    if (ancestors.length === 0) {
      return;
    }
    setExpandedPaths((current) => {
      let changed = false;
      const next = new Set(current);
      for (const path of ancestors) {
        if (!next.has(path)) {
          next.add(path);
          changed = true;
        }
      }
      if (changed) {
        writeExpandedPaths(next);
        return next;
      }
      return current;
    });
  }, [selectedPath]);

  const expandedApi = useMemo<TreeExpandedContextValue>(
    () => ({
      isExpanded: (path: string) => expandedPaths.has(path),
      toggle: (path: string) => {
        setExpandedPaths((current) => {
          const next = new Set(current);
          if (next.has(path)) {
            next.delete(path);
          } else {
            next.add(path);
          }
          writeExpandedPaths(next);
          return next;
        });
      },
    }),
    [expandedPaths]
  );

  const dragApi = useMemo<TreeDragContextValue>(
    () => ({
      canReorder: canReorder && Boolean(onReorder),
      draggingPath,
      dropTargetPath,
      startDrag: setDraggingPath,
      endDrag: () => {
        setDraggingPath(null);
        setDropTargetPath(null);
      },
      setDropTarget: setDropTargetPath,
      reorder: (parentPath, orderedPaths) => onReorder?.(parentPath, orderedPaths),
      siblingPaths: (parentPath, rootNodes) => collectSiblingPaths(parentPath, rootNodes),
    }),
    [canReorder, draggingPath, dropTargetPath, onReorder]
  );

  return (
    <TreeExpandedContext.Provider value={expandedApi}>
      <TreeDragContext.Provider value={dragApi}>
        <div className={`object-tree ${draggingPath ? "is-dragging" : ""}`}>
          {nodes.map((node) => (
            <TreeItem
              key={node.object.path}
              node={node}
              selectedPath={selectedPath}
              onSelect={onSelect}
              onOpenEditor={onOpenEditor}
              depth={0}
              rootNodes={nodes}
            />
          ))}
        </div>
      </TreeDragContext.Provider>
    </TreeExpandedContext.Provider>
  );
}
