import { useMemo } from "react";
import { useMutation } from "@tanstack/react-query";
import { bulkDeleteObjects, updateGroupMembers } from "../api";
import type { ObjectSummary } from "../types";
import { canDeleteObjectPath } from "../utils/platformSystemPaths";
import {
  filterVisualGroupsInCatalog,
  resolveVisualGroupCatalogParent,
} from "../utils/createObjectMode";
import { parseTreeRowKey } from "../utils/treeRowKey";

export interface TreeBulkActionsConfig {
  visibleRowKeys: string[];
  selectedKeys: Set<string>;
  objects: ObjectSummary[];
  onSelectionChange: (keys: Set<string>) => void;
  onDeleted?: () => void;
  onMembersChanged?: () => void;
  contextPath?: string | null;
  contextObjectType?: ObjectSummary["type"];
  onCreateChild?: (parentPath: string) => void;
  onCreateVisualGroup?: (parentPath: string) => void;
}

export function useTreeBulkActions({
  visibleRowKeys,
  selectedKeys,
  objects,
  onSelectionChange,
  onDeleted,
  onMembersChanged,
  contextPath,
  contextObjectType,
}: TreeBulkActionsConfig) {
  const selectedRows = useMemo(
    () => [...selectedKeys].map((key) => parseTreeRowKey(key)),
    [selectedKeys],
  );

  const hasGroupRefs = selectedRows.some((row) => row.viaGroup);
  const hasSelection = selectedKeys.size > 0;

  const canonicalPaths = useMemo(() => {
    const paths = new Set<string>();
    for (const row of selectedRows) {
      paths.add(row.path);
    }
    return [...paths];
  }, [selectedRows]);

  const selectionCatalogParents = useMemo(() => {
    const parents = new Set<string>();
    for (const path of canonicalPaths) {
      const obj = objects.find((item) => item.path === path);
      const catalog = resolveVisualGroupCatalogParent(path, obj?.type);
      if (catalog) {
        parents.add(catalog);
      }
    }
    return parents;
  }, [canonicalPaths, objects]);

  const hasMixedCatalogSelection = selectionCatalogParents.size > 1;

  const targetCatalogParent = useMemo(() => {
    if (hasMixedCatalogSelection) {
      return null;
    }
    if (selectionCatalogParents.size === 1) {
      return [...selectionCatalogParents][0];
    }
    if (contextPath) {
      return resolveVisualGroupCatalogParent(contextPath, contextObjectType);
    }
    return null;
  }, [selectionCatalogParents, hasMixedCatalogSelection, contextPath, contextObjectType]);

  const visualGroups = useMemo(() => {
    if (!targetCatalogParent) {
      return [];
    }
    return filterVisualGroupsInCatalog(objects, targetCatalogParent).sort((a, b) => {
      if (a.sortOrder !== b.sortOrder) {
        return a.sortOrder - b.sortOrder;
      }
      return a.displayName.localeCompare(b.displayName);
    });
  }, [objects, targetCatalogParent]);

  const deletablePaths = useMemo(() => {
    return canonicalPaths.filter((path) => {
      const obj = objects.find((item) => item.path === path);
      return canDeleteObjectPath(path, obj?.type);
    });
  }, [canonicalPaths, objects]);

  const hasNonDeletableSelection = deletablePaths.length !== canonicalPaths.length;

  const deleteMutation = useMutation({
    mutationFn: () => bulkDeleteObjects(deletablePaths),
    onSuccess: () => {
      onSelectionChange(new Set());
      onDeleted?.();
    },
  });

  const removeFromGroupMutation = useMutation({
    mutationFn: async () => {
      const byGroup = new Map<string, string[]>();
      for (const row of selectedRows) {
        if (!row.viaGroup) {
          continue;
        }
        const list = byGroup.get(row.viaGroup) ?? [];
        list.push(row.path);
        byGroup.set(row.viaGroup, list);
      }
      for (const [groupPath, paths] of byGroup.entries()) {
        await updateGroupMembers(groupPath, "remove", { paths });
      }
    },
    onSuccess: () => {
      onSelectionChange(new Set());
      onMembersChanged?.();
    },
  });

  const addToGroupMutation = useMutation({
    mutationFn: (groupPath: string) =>
      updateGroupMembers(groupPath, "add", { paths: canonicalPaths }),
    onSuccess: () => {
      onMembersChanged?.();
    },
  });

  const selectAll = () => onSelectionChange(new Set(visibleRowKeys));
  const clearSelection = () => onSelectionChange(new Set());

  const deleteSelected = () => {
    if (deletablePaths.length === 0) {
      return;
    }
    const msg =
      `Удалить ${deletablePaths.length} объект(ов)?\n\n`
      + "Для контейнеров будет удалено всё поддерево. Ссылки в группах удалятся автоматически.";
    if (window.confirm(msg)) {
      deleteMutation.mutate();
    }
  };

  return {
    hasSelection,
    hasGroupRefs,
    canonicalPaths,
    deletablePaths,
    hasNonDeletableSelection,
    visualGroups,
    targetCatalogParent,
    hasMixedCatalogSelection,
    canAddToGroup: canonicalPaths.length > 0 && !hasMixedCatalogSelection,
    selectAll,
    clearSelection,
    deleteSelected,
    removeFromGroup: () => removeFromGroupMutation.mutate(),
    addToGroup: (groupPath: string) => addToGroupMutation.mutate(groupPath),
    isDeleting: deleteMutation.isPending,
    isRemovingFromGroup: removeFromGroupMutation.isPending,
    isAddingToGroup: addToGroupMutation.isPending,
  };
}
