import { useQuery } from "@tanstack/react-query";
import { fetchObject, fetchObjectEditor, fetchVariables } from "../api";
import type { ObjectEditorDto, ObjectSummary } from "../types";

const inspectorQueryOptions = {
  /** Avoid refetch churn while the inspector is open. */
  staleTime: 60_000,
  refetchOnWindowFocus: false,
};

export function useInspectorObject(path: string) {
  return useQuery({
    queryKey: ["object", path],
    queryFn: () => fetchObject(path),
    ...inspectorQueryOptions,
  });
}

export function useInspectorVariables(path: string, enabled = true) {
  return useQuery({
    queryKey: ["variables", path],
    queryFn: () => fetchVariables(path),
    enabled,
    ...inspectorQueryOptions,
  });
}

export function useInspectorObjectEditor(path: string, enabled = true) {
  return useQuery({
    queryKey: ["object-editor", path],
    queryFn: () => fetchObjectEditor(path),
    enabled,
    ...inspectorQueryOptions,
  });
}

export function resolveInspectorObject(
  path: string,
  data: ObjectSummary | undefined,
): ObjectSummary | null {
  if (!data || data.path !== path) {
    return null;
  }
  return data;
}

export function resolveInspectorEditor(
  path: string,
  data: ObjectEditorDto | undefined,
): ObjectEditorDto | null {
  if (!data || data.object.path !== path) {
    return null;
  }
  return data;
}

/** Full-screen loading only before the first successful fetch for this path. */
export function inspectorQueryLoading(query: {
  isPending: boolean;
  data: unknown;
}): boolean {
  return query.isPending && query.data == null;
}
