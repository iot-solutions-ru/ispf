import { useTranslation } from "react-i18next";
import { Alert, Typography } from "antd";
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

  const description = showHint ? (
    <Typography.Paragraph className="bundle-license-error-hint">
      <Typography.Text strong>{t("bundle.license.errorHint.title")}</Typography.Text>{" "}
      {t(`bundle.license.errorHint.${hintKey}`)}
    </Typography.Paragraph>
  ) : undefined;

  return (
    <Alert
      className="bundle-license-error"
      type="error"
      showIcon
      message={message}
      description={description}
    />
  );
}
