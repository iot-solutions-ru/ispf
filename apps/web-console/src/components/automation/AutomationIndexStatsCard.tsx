import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchAutomationIndexStats } from "../../api/automationIndex";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";

export default function AutomationIndexStatsCard() {
  const { t } = useTranslation(["system", "common"]);
  const { formatDate } = useUserTimeZone();
  const statsQuery = useQuery({
    queryKey: ["automation-index-stats"],
    queryFn: fetchAutomationIndexStats,
    refetchInterval: 30_000,
  });

  return (
    <section className="system-metrics-card automation-index-card">
      <h3>{t("automationIndex.title")}</h3>
      {statsQuery.isLoading && <p className="hint">{t("automationIndex.loading")}</p>}
      {statsQuery.error && (
        <div className="op-alert op-alert-error">{t("automationIndex.loadError")}</div>
      )}
      {statsQuery.data && (
        <div className="automation-index-stats">
          <div className="automation-index-stat">
            <span className="automation-index-stat-label">{t("automationIndex.alertRulesIndexed")}</span>
            <span className="automation-index-stat-value">{statsQuery.data.alertRulesIndexed}</span>
          </div>
          <div className="automation-index-stat">
            <span className="automation-index-stat-label">{t("automationIndex.correlatorsIndexed")}</span>
            <span className="automation-index-stat-value">{statsQuery.data.correlatorsIndexed}</span>
          </div>
          <div className="automation-index-stat">
            <span className="automation-index-stat-label">{t("automationIndex.workflowTriggersIndexed")}</span>
            <span className="automation-index-stat-value">{statsQuery.data.workflowTriggersIndexed}</span>
          </div>
          <div className="automation-index-stat">
            <span className="automation-index-stat-label">{t("automationIndex.lastRebuildAt")}</span>
            <span className="automation-index-stat-value" style={{ fontSize: "0.95rem" }}>
              {statsQuery.data.lastRebuildAt ? (
                <time dateTime={statsQuery.data.lastRebuildAt}>
                  {formatDate(statsQuery.data.lastRebuildAt)}
                </time>
              ) : (
                t("common:empty.dash")
              )}
            </span>
          </div>
        </div>
      )}
    </section>
  );
}
