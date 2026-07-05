import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchClusterHealth } from "../api/clusterHealth";
import ClusterHealthPanel from "./ClusterHealthCard";

export default function ClusterView() {
  const { t } = useTranslation("system");
  const healthQuery = useQuery({
    queryKey: ["cluster-health"],
    queryFn: fetchClusterHealth,
    refetchInterval: 15_000,
  });

  return (
    <div className="system-cluster-view">
      <div className="system-embedded-toolbar">
        <button
          type="button"
          className="btn"
          disabled={healthQuery.isFetching}
          onClick={() => healthQuery.refetch()}
        >
          {t("clusterHealth.refresh")}
        </button>
        {healthQuery.data && (
          <p className="system-metrics-updated hint">
            {t("clusterHealth.updatedAt", {
              time: new Date(healthQuery.data.timestamp).toLocaleString(),
            })}
          </p>
        )}
      </div>

      <p className="op-muted cluster-view-intro">{t("clusterHealth.subtitle")}</p>

      <ClusterHealthPanel showTitle={false} />
    </div>
  );
}
