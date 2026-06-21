import {
  createContext,
  memo,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
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

interface FlatRow {
  node: TreeNode;
  depth: number;
}

const ROW_HEIGHT = 34;
const OVERSCAN = 8;

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

function flattenVisibleNodes(
  nodes: TreeNode[],
  isExpanded: (path: string) => boolean,
  depth = 0,
): FlatRow[] {
  const rows: FlatRow[] = [];
  for (const node of nodes) {
    rows.push({ node, depth });
    const path = node.object.path;
    if (node.children.length > 0 && isExpanded(path)) {
      rows.push(...flattenVisibleNodes(node.children, isExpanded, depth + 1));
    }
  }
  return rows;
}

interface TreeRowProps {
  node: TreeNode;
  depth: number;
  expanded: boolean;
  selectedPath: string | null;
  draggingPath: string | null;
  dropTargetPath: string | null;
  onSelect: (path: string) => void;
  onOpenEditor?: (path: string) => void;
  rootNodes: TreeNode[];
}

const TreeRow = memo(function TreeRow({
  node,
  depth,
  expanded,
  selectedPath,
  draggingPath,
  dropTargetPath,
  onSelect,
  onOpenEditor,
  rootNodes,
}: TreeRowProps) {
  const { toggle } = useTreeExpanded();
  const drag = useTreeDrag();
  const path = node.object.path;
  const parentPath = parentObjectPath(path);
  const hasChildren = node.children.length > 0;
  const isSelected = selectedPath === path;
  const draggable = Boolean(
    drag?.canReorder
    && path !== "root"
    && parentPath
    && drag.siblingPaths(parentPath, rootNodes).length > 1
  );
  const isDragging = draggingPath === path;
  const isDropTarget = dropTargetPath === path && draggingPath !== path;

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
  );
}, (prev, next) =>
  prev.node.object.path === next.node.object.path
  && prev.node.object.displayName === next.node.object.displayName
  && prev.node.object.type === next.node.object.type
  && prev.node.object.iconId === next.node.object.iconId
  && prev.node.object.federated === next.node.object.federated
  && prev.node.children.length === next.node.children.length
  && prev.depth === next.depth
  && prev.expanded === next.expanded
  && prev.selectedPath === next.selectedPath
  && prev.draggingPath === next.draggingPath
  && prev.dropTargetPath === next.dropTargetPath);

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
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(400);
  const containerRef = useRef<HTMLDivElement>(null);

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

  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return;
    }
    const updateHeight = () => setViewportHeight(el.clientHeight);
    updateHeight();
    const observer = new ResizeObserver(updateHeight);
    observer.observe(el);
    return () => observer.disconnect();
  }, [nodes.length]);

  const isExpanded = useCallback((path: string) => expandedPaths.has(path), [expandedPaths]);

  const flatRows = useMemo(
    () => flattenVisibleNodes(nodes, isExpanded),
    [nodes, isExpanded],
  );

  const startIndex = Math.max(0, Math.floor(scrollTop / ROW_HEIGHT) - OVERSCAN);
  const endIndex = Math.min(
    flatRows.length,
    Math.ceil((scrollTop + viewportHeight) / ROW_HEIGHT) + OVERSCAN,
  );
  const visibleRows = flatRows.slice(startIndex, endIndex);
  const offsetY = startIndex * ROW_HEIGHT;
  const totalHeight = flatRows.length * ROW_HEIGHT;

  const expandedApi = useMemo<TreeExpandedContextValue>(
    () => ({
      isExpanded,
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
    [isExpanded],
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
    [canReorder, draggingPath, dropTargetPath, onReorder],
  );

  return (
    <TreeExpandedContext.Provider value={expandedApi}>
      <TreeDragContext.Provider value={dragApi}>
        <div
          ref={containerRef}
          className={`object-tree ${draggingPath ? "is-dragging" : ""}`}
          onScroll={(event) => setScrollTop(event.currentTarget.scrollTop)}
        >
          {flatRows.length > 0 && (
            <div className="object-tree-virtual" style={{ height: totalHeight }}>
              <div className="object-tree-window" style={{ transform: `translateY(${offsetY}px)` }}>
                {visibleRows.map(({ node, depth }) => (
                  <TreeRow
                    key={node.object.path}
                    node={node}
                    depth={depth}
                    expanded={isExpanded(node.object.path)}
                    selectedPath={selectedPath}
                    draggingPath={draggingPath}
                    dropTargetPath={dropTargetPath}
                    onSelect={onSelect}
                    onOpenEditor={onOpenEditor}
                    rootNodes={nodes}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      </TreeDragContext.Provider>
    </TreeExpandedContext.Provider>
  );
}
