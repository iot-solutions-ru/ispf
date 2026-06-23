import { useQuery } from "@tanstack/react-query";
import { fetchObjects } from "../../api";

export function useDataSourceOptions() {
  return useQuery({
    queryKey: ["data-sources-list"],
    queryFn: () => fetchObjects("root.platform.data-sources"),
    select: (objects) => objects.filter((obj) => obj.type === "DATA_SOURCE"),
  });
}
