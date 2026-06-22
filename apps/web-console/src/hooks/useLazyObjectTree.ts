import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { fetchObjects } from "../api";
import type { ObjectSummary, TreeNode } from "../types";
import { buildObjectTree, parentObjectPath } from "../utils/tree";
import type { ObjectWsMessage } from "./useObjectWebSocket";
import { OBJECT_WS_EVENT } from "./useObjectWebSocket";

const BOOTSTRAP_PARENTS = ["root", "root.platform"];

export function useLazyObjectTree() {
  const objectsRef = useRef(new Map<string, ObjectSummary>());
  const loadedParentsRef = useRef(new Set<string>());
  const loadingParentsRef = useRef(new Set<string>());
  const [version, setVersion] = useState(0);

  const bump = useCallback(() => setVersion((v) => v + 1), []);

  const mergeObjects = useCallback(
    (items: ObjectSummary[]) => {
      const map = objectsRef.current;
      for (const item of items) {
        map.set(item.path, item);
      }
      bump();
    },
    [bump],
  );

  const loadChildren = useCallback(
    async (parentPath: string, force = false) => {
      if (!force && loadedParentsRef.current.has(parentPath)) {
        return;
      }
      if (loadingParentsRef.current.has(parentPath)) {
        return;
      }
      loadingParentsRef.current.add(parentPath);
      try {
        const children = await fetchObjects(parentPath, true);
        mergeObjects(children);
        loadedParentsRef.current.add(parentPath);
      } finally {
        loadingParentsRef.current.delete(parentPath);
      }
    },
    [mergeObjects],
  );

  const refreshParent = useCallback(
    async (parentPath: string) => {
      loadedParentsRef.current.delete(parentPath);
      await loadChildren(parentPath, true);
    },
    [loadChildren],
  );

  useEffect(() => {
    void (async () => {
      for (const parent of BOOTSTRAP_PARENTS) {
        await loadChildren(parent);
      }
    })();
  }, [loadChildren]);

  useEffect(() => {
    const onStructureChange = (event: Event) => {
      const message = (event as CustomEvent<ObjectWsMessage>).detail;
      if (!message?.path) {
        return;
      }
      const parent = parentObjectPath(message.path);
      if (parent) {
        void refreshParent(parent);
      }
      if (message.type === "DELETED") {
        objectsRef.current.delete(message.path);
        bump();
      }
    };
    window.addEventListener("ispf-tree-structure-change", onStructureChange);
    return () => window.removeEventListener("ispf-tree-structure-change", onStructureChange);
  }, [refreshParent, bump]);

  const objects = useMemo(() => [...objectsRef.current.values()], [version]);

  const tree: TreeNode[] = useMemo(() => buildObjectTree(objects), [objects]);

  const invalidateAll = useCallback(async () => {
    loadedParentsRef.current.clear();
    objectsRef.current.clear();
    bump();
    for (const parent of BOOTSTRAP_PARENTS) {
      await loadChildren(parent, true);
    }
  }, [loadChildren, bump]);

  return {
    tree,
    objects,
    loadChildren,
    refreshParent,
    invalidateAll,
    loadingParents: loadingParentsRef.current,
  };
}
