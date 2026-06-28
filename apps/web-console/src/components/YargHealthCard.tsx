import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchYargHealth } from "../api/yargHealth";

export default function YargHealthCard() {
  const { t } = useTranslation(["system", "common"]);
  const healthQuery = useQuery({
    queryKey: ["yarg-health"],
    queryFn: fetchYargHealth,
    refetchInterval: 30_000,
  });

  return (
    <section className="system-metrics-card yarg-health-card">
      <h3>{t("yargHealth.title")}</h3>
      {healthQuery.isLoading && <p className="hint">{t("yargHealth.loading")}</p>}
      {healthQuery.error && (
        <div className="op-alert op-alert-error">{t("yargHealth.loadError")}</div>
      )}
      {healthQuery.data && (
        <>
          <table className="op-table system-metrics-table">
            <tbody>
              <tr>
                <th>{t("yargHealth.libreOffice")}</th>
                <td>
                  <span className={healthQuery.data.libreOfficeAvailable ? "system-health-ok" : "system-health-bad"}>
                    {healthQuery.data.libreOfficeAvailable
                      ? t("yargHealth.available")
                      : t("yargHealth.unavailable")}
                  </span>
                </td>
              </tr>
              {healthQuery.data.configuredPath && (
                <tr>
                  <th>{t("yargHealth.configuredPath")}</th>
                  <td className="mono">{healthQuery.data.configuredPath}</td>
                </tr>
              )}
              {healthQuery.data.resolvedPath && (
                <tr>
                  <th>{t("yargHealth.resolvedPath")}</th>
                  <td className="mono">{healthQuery.data.resolvedPath}</td>
                </tr>
              )}
              <tr>
                <th>{t("yargHealth.timeoutSeconds")}</th>
                <td>{healthQuery.data.timeoutSeconds}</td>
              </tr>
            </tbody>
          </table>
          <p className="hint">{t("yargHealth.hint")}</p>
          {!healthQuery.data.libreOfficeAvailable && (
            <p className="hint warning">{healthQuery.data.pdfHint}</p>
          )}
        </>
      )}
    </section>
  );
}
