import { useMemo } from "react";
import { useMutation } from "@tanstack/react-query";
import { bulkDeleteObjects, updateGroupMembers } from "../api";
import type { ObjectSummary } from "../types";
import { parseTreeRowKey } from "../utils/treeRowKey";

export interface TreeBulkActionsConfig {
  visibleRowKeys: string[];
  selectedKeys: Set<string>;
  objects: ObjectSummary[];
  onSelectionChange: (keys: Set<string>) => void;
  onDeleted?: () => void;
  onMembersChanged?: () => void;
}

export function useTreeBulkActions({
  visibleRowKeys,
  selectedKeys,
  objects,
  onSelectionChange,
  onDeleted,
  onMembersChanged,
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

  const visualGroups = useMemo(
    () => objects.filter((obj) => !obj.groupRef && obj.type === "VISUAL_GROUP"),
    [objects],
  );

  const deleteMutation = useMutation({
    mutationFn: () => bulkDeleteObjects(canonicalPaths),
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
    const msg =
      `Удалить ${canonicalPaths.length} объект(ов)?\n\n`
      + "Для контейнеров будет удалено всё поддерево. Ссылки в группах удалятся автоматически.";
    if (window.confirm(msg)) {
      deleteMutation.mutate();
    }
  };

  return {
    hasSelection,
    hasGroupRefs,
    canonicalPaths,
    visualGroups,
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
