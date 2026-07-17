import { useTranslation } from "react-i18next";

interface OperatorAppMissingProps {
  appId: string;
  onPickApp?: () => void;
  onSwitchAdmin?: () => void;
}

/** Shown when ?app= points at an unknown / deleted operator application. */
export default function OperatorAppMissing({
  appId,
  onPickApp,
  onSwitchAdmin,
}: OperatorAppMissingProps) {
  const { t } = useTranslation(["operator", "common"]);
  return (
    <div className="operator-shell op-loading" data-testid="operator-app-missing">
      <div className="op-alert op-alert-error" role="alert">
        {t("operator:appMissing", { appId })}
      </div>
      <p className="hint">{t("operator:appMissingHint")}</p>
      <div className="btn-row">
        {onPickApp && (
          <button type="button" className="btn primary" onClick={onPickApp}>
            {t("operator:appMissingPick")}
          </button>
        )}
        {onSwitchAdmin && (
          <button type="button" className="btn" onClick={onSwitchAdmin}>
            {t("operator:launcher.switchAdmin")}
          </button>
        )}
      </div>
    </div>
  );
}
