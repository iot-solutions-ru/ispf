import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Button, Typography } from "antd";
import { fetchClusterHealth } from "../../api/clusterHealth";
import ClusterHealthPanel from "./ClusterHealthCard";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";

export default function ClusterView() {
  const { t } = useTranslation("system");
  const { formatDate } = useUserTimeZone();
  const healthQuery = useQuery({
    queryKey: ["cluster-health"],
    queryFn: fetchClusterHealth,
    refetchInterval: 15_000,
  });

  return (
    <div className="system-cluster-view">
      <div className="system-embedded-toolbar">
        <Button
          disabled={healthQuery.isFetching}
          onClick={() => healthQuery.refetch()}
        >
          {t("clusterHealth.refresh")}
        </Button>
        {healthQuery.data && (
          <Typography.Paragraph type="secondary" className="system-metrics-updated hint">
            {t("clusterHealth.updatedAt", {
              time: formatDate(healthQuery.data.timestamp),
            })}
          </Typography.Paragraph>
        )}
      </div>

      <Typography.Paragraph type="secondary" className="cluster-view-intro">
        {t("clusterHealth.subtitle")}
      </Typography.Paragraph>

      <ClusterHealthPanel showTitle={false} />
    </div>
  );
}
