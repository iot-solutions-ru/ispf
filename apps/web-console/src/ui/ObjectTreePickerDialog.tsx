import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Button, Input, Space, Typography } from "antd";
import ObjectTree from "../components/objectEditor/ObjectTree";
import { useLazyObjectTree } from "../hooks/useLazyObjectTree";
import { useObjectTreeSearch } from "../hooks/useObjectTreeSearch";
import type { ObjectType } from "../types";
import { buildObjectTree } from "../utils/tree/tree";
import { filterLoadedObjectsForQuery, OBJECT_SEARCH_MIN_CHARS } from "../utils/tree/treeSearch";
import { selectionFromObject } from "../utils/tree/treeRowKey";
import Modal from "./Modal";

export interface ObjectTreePickerDialogProps {
  open: boolean;
  title?: string;
  onClose: () => void;
  onSelect: (path: string) => void;
  filterTypes?: ObjectType[];
  rootPath?: string;
  multi?: boolean;
}

function pathUnderRoot(path: string, rootPath?: string): boolean {
  if (!rootPath?.trim()) {
    return true;
  }
  const root = rootPath.trim();
  return path === root || path.startsWith(`${root}.`);
}

function matchesFilter(type: ObjectType, filterTypes?: ObjectType[]): boolean {
  if (!filterTypes || filterTypes.length === 0) {
    return true;
  }
  return filterTypes.includes(type);
}

export default function ObjectTreePickerDialog({
  open,
  title,
  onClose,
  onSelect,
  filterTypes,
  rootPath,
  multi = false,
}: ObjectTreePickerDialogProps) {
  const { t } = useTranslation(["common", "objectTree"]);
  const { tree, objects, loadChildren, treeLoadError } = useLazyObjectTree(open);
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [filterQuery, setFilterQuery] = useState("");
  const fullTreeSearch = filterQuery.trim().length >= OBJECT_SEARCH_MIN_CHARS;
  const objectSearch = useObjectTreeSearch(filterQuery, open, {
    parentPrefix: rootPath,
    type: filterTypes?.length === 1 ? filterTypes[0] : undefined,
  });

  const filteredTree = useMemo(() => {
    const q = filterQuery.trim().toLowerCase();
    if (!q) {
      if (!rootPath?.trim()) {
        return tree;
      }
      const filterByRoot = (nodes: typeof tree): typeof tree =>
        nodes
          .filter((node) => pathUnderRoot(node.object.path, rootPath))
          .map((node) => ({
            object: node.object,
            children: filterByRoot(node.children),
          }));
      return filterByRoot(tree);
    }
    const source =
      fullTreeSearch && objectSearch.data?.objects
        ? objectSearch.data.objects
        : filterLoadedObjectsForQuery(objects, filterQuery);
    const allowed = source.filter(
      (obj) => pathUnderRoot(obj.path, rootPath) && matchesFilter(obj.type, filterTypes),
    );
    return buildObjectTree(allowed);
  }, [tree, objects, filterQuery, rootPath, filterTypes, fullTreeSearch, objectSearch.data]);

  const treeObjects = fullTreeSearch && objectSearch.data?.objects
    ? objectSearch.data.objects
    : objects;

  const confirmSelection = useCallback(() => {
    if (!selectedPath) {
      return;
    }
    onSelect(selectedPath);
    onClose();
  }, [onSelect, onClose, selectedPath]);

  const handleTreeLoadChildren = useCallback(
    (path: string) => {
      void loadChildren(path);
    },
    [loadChildren],
  );

  const handleRowSelect = useCallback(
    (row: ReturnType<typeof selectionFromObject>, event: { metaKey: boolean; shiftKey: boolean }) => {
      if (!row.path) {
        return;
      }
      const obj = treeObjects.find((item) => item.path === row.path);
      if (obj && filterTypes?.length && !matchesFilter(obj.type, filterTypes)) {
        return;
      }
      if (multi) {
        setSelectedKeys((current) => {
          const next = new Set(current);
          if (next.has(row.key)) {
            next.delete(row.key);
          } else {
            next.add(row.key);
          }
          return next;
        });
      }
      setSelectedPath(row.path);
      if (!multi && event.metaKey === false && event.shiftKey === false) {
        onSelect(row.path);
        onClose();
      }
    },
    [treeObjects, filterTypes, multi, onClose, onSelect],
  );

  return (
    <Modal
      open={open}
      title={title ?? t("objectPath.browseTree")}
      onClose={onClose}
      wide
      className="modal-object-tree-picker"
      footer={
        <Space>
          <Button onClick={onClose}>
            {t("action.cancel")}
          </Button>
          <Button
            type="primary"
            disabled={!selectedPath}
            onClick={confirmSelection}
          >
            {t("modal.select")}
          </Button>
        </Space>
      }
    >
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <Input.Search
          className="object-tree-picker-search"
          placeholder={t("action.search")}
          value={filterQuery}
          onChange={(event) => setFilterQuery(event.target.value)}
          autoFocus
        />
        <Typography.Paragraph type="secondary" className="object-tree-picker-hint" style={{ marginBottom: 0 }}>
          {t("objectPath.searchHint")}
        </Typography.Paragraph>
        {treeLoadError && <Alert type="error" showIcon message={treeLoadError} />}
        {fullTreeSearch && objectSearch.isError && (
          <Alert type="warning" showIcon message={t("objectPath.searchHint")} />
        )}
        <div className="object-tree-picker-body">
          <ObjectTree
            nodes={filteredTree}
            objects={treeObjects}
            selectedPath={selectedPath}
            selectedKeys={selectedKeys}
            onRowSelect={handleRowSelect}
            onLoadChildren={fullTreeSearch ? undefined : handleTreeLoadChildren}
          />
        </div>
        {filterTypes && filterTypes.length > 0 && (
          <Typography.Paragraph type="secondary" className="object-tree-picker-hint" style={{ marginBottom: 0 }}>
            {t("objectPath.filterHint", { types: filterTypes.join(", ") })}
          </Typography.Paragraph>
        )}
      </Space>
    </Modal>
  );
}
