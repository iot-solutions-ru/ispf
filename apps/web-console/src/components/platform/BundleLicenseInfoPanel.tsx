import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchPlatformLicense } from "../../api/platformLicense";
import { parseManifestLicense } from "../../utils/bundleLicenseUi";

interface BundleLicenseInfoPanelProps {
  appId?: string;
  manifest?: unknown;
  compact?: boolean;
}

export default function BundleLicenseInfoPanel({
  appId,
  manifest,
  compact = false,
}: BundleLicenseInfoPanelProps) {
  const { t } = useTranslation(["platform", "common"]);
  const licenseQuery = useQuery({
    queryKey: ["platform-license"],
    queryFn: fetchPlatformLicense,
    staleTime: 60_000,
  });

  const manifestLicense = useMemo(() => parseManifestLicense(manifest), [manifest]);

  const copyInstallationId = async () => {
    const id = licenseQuery.data?.installationId;
    if (!id) {
      return;
    }
    try {
      await navigator.clipboard.writeText(id);
    } catch {
      // Clipboard may be unavailable outside secure context.
    }
  };

  if (licenseQuery.isLoading) {
    return <p className="hint">{t("platform:bundle.license.loading")}</p>;
  }

  if (licenseQuery.error) {
    return <div className="op-alert op-alert-error">{t("platform:bundle.license.loadError")}</div>;
  }

  const serverInstallationId = licenseQuery.data?.installationId ?? "";
  const installationMismatch = Boolean(
    manifestLicense.present
    && manifestLicense.installationId
    && serverInstallationId
    && manifestLicense.installationId.toLowerCase() !== serverInstallationId.toLowerCase(),
  );
  const appIdMismatch = Boolean(
    appId
    && manifestLicense.bundleId
    && manifestLicense.bundleId !== appId,
  );

  return (
    <section className={`bundle-license-info${compact ? " bundle-license-info-compact" : ""}`}>
      <h4>{t("platform:bundle.license.title")}</h4>
      <p className="op-muted">{t("platform:bundle.license.hint")}</p>

      <dl className="solution-catalog-kv bundle-license-kv">
        <div>
          <dt>{t("platform:bundle.license.installationId")}</dt>
          <dd className="mono">
            <span>{serverInstallationId}</span>
            <button type="button" className="btn btn-sm" onClick={() => void copyInstallationId()}>
              {t("platform:bundle.license.copyInstallationId")}
            </button>
          </dd>
        </div>
        <div>
          <dt>{t("platform:bundle.license.enforce")}</dt>
          <dd>
            {licenseQuery.data?.enforce ? t("common:action.yes") : t("common:action.no")}
          </dd>
        </div>
        <div>
          <dt>{t("platform:bundle.license.manifestBlock")}</dt>
          <dd>
            {manifestLicense.present
              ? t("platform:bundle.license.manifestPresent")
              : t("platform:bundle.license.manifestAbsent")}
          </dd>
        </div>
        {manifestLicense.present && manifestLicense.bundleId && (
          <div>
            <dt>{t("platform:bundle.license.bundleId")}</dt>
            <dd>
              <code>{manifestLicense.bundleId}</code>
              {appIdMismatch && (
                <span className="bundle-license-warn">
                  {" "}
                  {t("platform:bundle.license.mismatchAppId", { appId })}
                </span>
              )}
            </dd>
          </div>
        )}
        {manifestLicense.present && manifestLicense.expiresAt && (
          <div>
            <dt>{t("platform:bundle.license.expiresAt")}</dt>
            <dd>
              <time dateTime={manifestLicense.expiresAt}>
                {new Date(manifestLicense.expiresAt).toLocaleString()}
              </time>
            </dd>
          </div>
        )}
      </dl>

      {installationMismatch && (
        <div className="op-alert op-alert-info">
          {t("platform:bundle.license.mismatchInstallation", {
            licensed: manifestLicense.installationId,
            server: serverInstallationId,
          })}
        </div>
      )}
    </section>
  );
}
