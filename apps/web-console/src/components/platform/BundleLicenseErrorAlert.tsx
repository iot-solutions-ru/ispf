import { useTranslation } from "react-i18next";
import { isLicenseRelatedError, licenseErrorHintKey } from "../../utils/platform/bundleLicenseUi";

interface BundleLicenseErrorAlertProps {
  error: unknown;
}

export default function BundleLicenseErrorAlert({ error }: BundleLicenseErrorAlertProps) {
  const { t } = useTranslation("platform");
  const message = String(error);

  if (!message) {
    return null;
  }

  const showHint = isLicenseRelatedError(message);
  const hintKey = licenseErrorHintKey(message);

  return (
    <div className="op-alert op-alert-error bundle-license-error">
      <p>{message}</p>
      {showHint && (
        <p className="op-muted bundle-license-error-hint">
          <strong>{t("bundle.license.errorHint.title")}</strong>{" "}
          {t(`bundle.license.errorHint.${hintKey}`)}
        </p>
      )}
    </div>
  );
}
