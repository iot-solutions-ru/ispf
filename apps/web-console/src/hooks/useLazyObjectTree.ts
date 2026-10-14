import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { fetchObjects } from "../api";
import i18n from "../i18n";
import type { ObjectSummary, TreeNode } from "../types";
import { buildObjectTree, parentObjectPath } from "../utils/tree/tree";
import { objectTreeKey } from "../utils/tree/treeRowKey";
import type { ObjectWsMessage } from "./useObjectWebSocket";
import { OBJECT_WS_EVENT, useObjectPathsSubscription } from "./useObjectWebSocket";

const BOOTSTRAP_PARENTS = ["root", "root.platform"];
/**
 * Path-wide WS interest fans VARIABLE_UPDATED for every descendant (ADR-0024).
 * Never subscribe bootstrap roots — that would stream the whole platform.
 * Cap expanded folders; skip very large folders (HTTP refresh on expand / invalidate).
 */
const MAX_TREE_WS_INTEREST = 24;
const TREE_WS_CHILD_CAP = 64;

/** Full sidebar tree reload (create/delete outside WS interest, agent turns, …). */
export const TREE_INVALIDATE_EVENT = "ispf-tree-invalidate";

export function requestObjectTreeInvalidate(): void {
  window.dispatchEvent(new Event(TREE_INVALIDATE_EVENT));
}

function rememberWsInterest(prev: string[], parentPath: string): string[] {
  const without = prev.filter((path) => path !== parentPath);
  without.push(parentPath);
  if (without.length <= MAX_TREE_WS_INTEREST) {
    return without;
  }
  return without.slice(without.length - MAX_TREE_WS_INTEREST);
}

export function useLazyObjectTree(enabled = true) {
  const objectsRef = useRef(new Map<string, ObjectSummary>());
  const loadedParentsRef = useRef(new Set<string>());
  const loadingParentsRef = useRef(new Set<string>());
  const [version, setVersion] = useState(0);
  /** Path-wide WS interest for recently expanded *non-bootstrap* folders only. */
  const [wsInterestPaths, setWsInterestPaths] = useState<string[]>([]);

  const bump = useCallback(() => setVersion((v) => v + 1), []);

  const removeGroupRefsForParent = useCallback((parentPath: string) => {
    const map = objectsRef.current;
    for (const [key, obj] of map.entries()) {
      if (obj.groupRef && obj.groupContextPath === parentPath) {
        map.delete(key);
      }
    }
  }, []);

  const mergeObjects = useCallback(
    (items: ObjectSummary[], parentPath?: string) => {
      const map = objectsRef.current;
      if (parentPath) {
        removeGroupRefsForParent(parentPath);
      }
      for (const item of items) {
        map.set(objectTreeKey(item), item);
      }
      bump();
    },
    [bump, removeGroupRefsForParent],
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
        mergeObjects(children, parentPath);
        loadedParentsRef.current.add(parentPath);
        const isBootstrap = BOOTSTRAP_PARENTS.includes(parentPath);
        if (!isBootstrap && children.length <= TREE_WS_CHILD_CAP) {
          setWsInterestPaths((prev) => rememberWsInterest(prev, parentPath));
        }
      } catch (error) {
        loadedParentsRef.current.delete(parentPath);
        console.error(`Failed to load tree children for ${parentPath}`, error);
        throw error;
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

  const [treeLoadError, setTreeLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    void (async () => {
      try {
        setTreeLoadError(null);
        for (const parent of BOOTSTRAP_PARENTS) {
          await loadChildren(parent);
        }
      } catch (error) {
        setTreeLoadError(error instanceof Error ? error.message : i18n.t("common:error.treeLoadFailed"));
      }
    })();
  }, [loadChildren, enabled]);

  useEffect(() => {
    const onStructureChange = (event: Event) => {
      const message = (event as CustomEvent<ObjectWsMessage>).detail;
      if (!message?.path) {
        return;
      }
      void refreshParent(message.path);
      const parent = parentObjectPath(message.path);
      if (parent) {
        void refreshParent(parent);
      }
      if (message.type === "DELETED") {
        const map = objectsRef.current;
        for (const [key, obj] of [...map.entries()]) {
          if (obj.path === message.path) {
            map.delete(key);
          }
        }
        map.delete(message.path);
        bump();
      }
    };
    window.addEventListener("ispf-tree-structure-change", onStructureChange);
    return () => window.removeEventListener("ispf-tree-structure-change", onStructureChange);
  }, [refreshParent, bump]);

  useEffect(() => {
    const onDriverVariable = (event: Event) => {
      const message = (event as CustomEvent<ObjectWsMessage>).detail;
      if (message.type !== "VARIABLE_UPDATED" || message.variableName !== "driverStatus") {
        return;
      }
      const parent = parentObjectPath(message.path);
      if (parent) {
        void refreshParent(parent);
      }
    };
    window.addEventListener(OBJECT_WS_EVENT, onDriverVariable);
    return () => window.removeEventListener(OBJECT_WS_EVENT, onDriverVariable);
  }, [refreshParent]);

  const invalidateAll = useCallback(async () => {
    loadedParentsRef.current.clear();
    objectsRef.current.clear();
    setWsInterestPaths([]);
    bump();
    for (const parent of BOOTSTRAP_PARENTS) {
      await loadChildren(parent, true);
    }
  }, [loadChildren, bump]);

  useEffect(() => {
    const onInvalidate = () => {
      void invalidateAll();
    };
    window.addEventListener(TREE_INVALIDATE_EVENT, onInvalidate);
    return () => window.removeEventListener(TREE_INVALIDATE_EVENT, onInvalidate);
  }, [invalidateAll]);

  // Structure live-updates only for capped expanded folders (not whole platform).
  useObjectPathsSubscription(enabled ? wsInterestPaths : []);

  // `version` is the change counter of the ref-backed store; it is the real dependency here.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const objects = useMemo(() => [...objectsRef.current.values()], [version]);

  const tree: TreeNode[] = useMemo(() => buildObjectTree(objects), [objects]);

  return {
    tree,
    objects,
    loadChildren,
    refreshParent,
    invalidateAll,
    treeLoadError,
    loadingParents: loadingParentsRef.current,
  };
}
