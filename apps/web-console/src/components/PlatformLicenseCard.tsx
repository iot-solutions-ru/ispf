import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchPlatformLicense } from "../api/platformLicense";
import { useOptionalUserTimeZone } from "../context/UserTimeZoneContext";
import { formatUserDateTime } from "../utils/formatDateTime";

export default function PlatformLicenseCard() {
  const { t } = useTranslation(["system", "common"]);
  const tz = useOptionalUserTimeZone();
  const formatDate = (value: string | number | Date | null | undefined) =>
    tz ? tz.formatDate(value) : formatUserDateTime(value);
  const licenseQuery = useQuery({
    queryKey: ["platform-license"],
    queryFn: fetchPlatformLicense,
    refetchInterval: 60_000,
  });

  const copyInstallationId = async () => {
    if (!licenseQuery.data?.installationId) {
      return;
    }
    try {
      await navigator.clipboard.writeText(licenseQuery.data.installationId);
    } catch {
      // Clipboard may be unavailable outside secure context.
    }
  };

  return (
    <section className="system-metrics-card platform-license-card">
      <h3>{t("licenseHealth.title")}</h3>
      {licenseQuery.isLoading && <p className="hint">{t("licenseHealth.loading")}</p>}
      {licenseQuery.error && (
        <div className="op-alert op-alert-error">{t("licenseHealth.loadError")}</div>
      )}
      {licenseQuery.data && (
        <>
          <table className="op-table system-metrics-table">
            <tbody>
              <tr>
                <th>{t("licenseHealth.mode")}</th>
                <td>{licenseQuery.data.mode}</td>
              </tr>
              {licenseQuery.data.tier && (
                <tr>
                  <th>{t("licenseHealth.tier")}</th>
                  <td>{licenseQuery.data.tier}</td>
                </tr>
              )}
              <tr>
                <th>{t("licenseHealth.valid")}</th>
                <td>
                  <span className={licenseQuery.data.valid ? "system-health-ok" : "system-health-bad"}>
                    {licenseQuery.data.valid ? t("licenseHealth.validYes") : t("licenseHealth.validNo")}
                  </span>
                </td>
              </tr>
              <tr>
                <th>{t("licenseHealth.enforce")}</th>
                <td>
                  {licenseQuery.data.enforce ? t("common:action.yes") : t("common:action.no")}
                </td>
              </tr>
              {licenseQuery.data.expiresAt && (
                <tr>
                  <th>{t("licenseHealth.expiresAt")}</th>
                  <td>
                    <time dateTime={licenseQuery.data.expiresAt}>
                      {formatDate(licenseQuery.data.expiresAt)}
                    </time>
                  </td>
                </tr>
              )}
              <tr>
                <th>{t("licenseHealth.installationId")}</th>
                <td className="mono platform-license-id">
                  <span>{licenseQuery.data.installationId}</span>
                  <button type="button" className="btn btn-sm" onClick={() => void copyInstallationId()}>
                    {t("licenseHealth.copyInstallationId")}
                  </button>
                </td>
              </tr>
              <tr>
                <th>{t("licenseHealth.message")}</th>
                <td>{licenseQuery.data.message}</td>
              </tr>
            </tbody>
          </table>
          <p className="hint">{t("licenseHealth.hint")}</p>
          {licenseQuery.data.enforce && !licenseQuery.data.valid && (
            <p className="hint warning">{t("licenseHealth.enforceInvalidWarning")}</p>
          )}
        </>
      )}
    </section>
  );
}
