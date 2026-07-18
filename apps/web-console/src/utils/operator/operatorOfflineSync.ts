import type { QueryClient } from "@tanstack/react-query";

/**
 * Refetch operator-facing queries after browser reconnect so localStorage / SW caches
 * pick up fresh manifest, UI, dashboards, and screen data (BL-151).
 */
export async function syncOperatorCachesOnReconnect(
  queryClient: QueryClient,
  appId: string
): Promise<void> {
  await Promise.all([
    queryClient.refetchQueries({ queryKey: ["operator-manifest", appId] }),
    queryClient.refetchQueries({ queryKey: ["operator-ui", appId] }),
    queryClient.refetchQueries({ queryKey: ["dashboard"] }),
    queryClient.refetchQueries({ queryKey: ["variables"] }),
    queryClient.refetchQueries({ queryKey: ["bff-table"] }),
    queryClient.refetchQueries({ queryKey: ["app-report", appId] }),
  ]);
}
