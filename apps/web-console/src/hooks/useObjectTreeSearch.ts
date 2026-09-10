import { useQuery } from "@tanstack/react-query";
import { searchObjects, type ObjectSearchResponse } from "../api/objectsCore";
import { OBJECT_SEARCH_MIN_CHARS } from "../utils/tree/treeSearch";

export function useObjectTreeSearch(
  query: string,
  enabled: boolean,
  options?: { parentPrefix?: string; type?: string; limit?: number },
) {
  const q = query.trim();
  const ready = enabled && q.length >= OBJECT_SEARCH_MIN_CHARS;
  return useQuery<ObjectSearchResponse>({
    queryKey: [
      "object-search",
      q,
      options?.parentPrefix ?? "",
      options?.type ?? "",
      options?.limit ?? 50,
    ],
    queryFn: () =>
      searchObjects({
        q,
        parentPrefix: options?.parentPrefix,
        type: options?.type,
        limit: options?.limit,
      }),
    enabled: ready,
    staleTime: 15_000,
  });
}
