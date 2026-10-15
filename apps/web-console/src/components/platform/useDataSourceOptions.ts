import { useQuery } from "@tanstack/react-query";
import { loadDataSourceOptions } from "../../utils/platform/dataSourceOptions";

export function useDataSourceOptions(enabled = true) {
  return useQuery({
    queryKey: ["data-sources-list"],
    queryFn: () => loadDataSourceOptions(),
    enabled,
  });
}
