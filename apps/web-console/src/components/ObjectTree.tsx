import { useMemo, useState } from "react";
import type { TreeNode } from "../types";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";

interface ObjectTreeProps {
  nodes: TreeNode[];
  selectedPath: string | null;
  onSelect: (path: string) => void;
  onOpenEditor?: (path: string) => void;
}

function TreeItem({
  node,
  selectedPath,
  onSelect,
  onOpenEditor,
  depth,
}: {
  node: TreeNode;
  selectedPath: string | null;
  onSelect: (path: string) => void;
  onOpenEditor?: (path: string) => void;
  depth: number;
}) {
  const [expanded, setExpanded] = useState(depth < 2);
  const hasChildren = node.children.length > 0;
  const isSelected = selectedPath === node.object.path;

  return (
    <div className="tree-item">
      <button
        type="button"
        className={`tree-row ${isSelected ? "selected" : ""}`}
        style={{ paddingLeft: `${depth * 16 + 8}px` }}
        onClick={() => onSelect(node.object.path)}
        onDoubleClick={() => onOpenEditor?.(node.object.path)}
      >
        <span
          className="tree-toggle"
          onClick={(e) => {
            if (hasChildren) {
              e.stopPropagation();
              setExpanded((v) => !v);
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
          />
        ))}
    </div>
  );
}

export default function ObjectTree({ nodes, selectedPath, onSelect, onOpenEditor }: ObjectTreeProps) {
  const roots = useMemo(() => nodes, [nodes]);

  return (
    <div className="object-tree">
      {roots.map((node) => (
        <TreeItem
          key={node.object.path}
          node={node}
          selectedPath={selectedPath}
          onSelect={onSelect}
          onOpenEditor={onOpenEditor}
          depth={0}
        />
      ))}
    </div>
  );
}
