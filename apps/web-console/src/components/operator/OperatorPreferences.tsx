import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import ShellPreferences from "../ui/ShellPreferences";
import {
  isOperatorAlarmSoundEnabled,
  isOperatorBrowserNotifyEnabled,
  requestBrowserNotificationPermission,
  setOperatorAlarmSoundEnabled,
  setOperatorBrowserNotifyEnabled,
  OPERATOR_PREFERENCES_CHANGED_EVENT,
} from "../../utils/operator/operatorPreferences";

export default function OperatorPreferences() {
  const { t } = useTranslation("operator");
  const [soundEnabled, setSoundEnabled] = useState(isOperatorAlarmSoundEnabled);
  const [browserNotify, setBrowserNotify] = useState(isOperatorBrowserNotifyEnabled);
  const [notifyPermission, setNotifyPermission] = useState<NotificationPermission | "unsupported">(
    () => (typeof Notification === "undefined" ? "unsupported" : Notification.permission)
  );

  const syncFromStorage = useCallback(() => {
    setSoundEnabled(isOperatorAlarmSoundEnabled());
    setBrowserNotify(isOperatorBrowserNotifyEnabled());
    if (typeof Notification !== "undefined") {
      setNotifyPermission(Notification.permission);
    }
  }, []);

  useEffect(() => {
    syncFromStorage();
    const onChanged = () => syncFromStorage();
    window.addEventListener(OPERATOR_PREFERENCES_CHANGED_EVENT, onChanged);
    return () => window.removeEventListener(OPERATOR_PREFERENCES_CHANGED_EVENT, onChanged);
  }, [syncFromStorage]);

  const toggleSound = (enabled: boolean) => {
    setOperatorAlarmSoundEnabled(enabled);
    setSoundEnabled(enabled);
  };

  const toggleBrowserNotify = async (enabled: boolean) => {
    if (enabled && typeof Notification !== "undefined") {
      const permission = await requestBrowserNotificationPermission();
      setNotifyPermission(permission);
      if (permission !== "granted") {
        setOperatorBrowserNotifyEnabled(false);
        setBrowserNotify(false);
        return;
      }
    }
    setOperatorBrowserNotifyEnabled(enabled);
    setBrowserNotify(enabled);
  };

  return (
    <details className="operator-preferences">
      <summary className="btn small operator-preferences-trigger" title={t("preferences.title")}>
        {t("preferences.title")}
      </summary>
      <div className="operator-preferences-panel">
        <fieldset className="operator-preferences-fieldset">
          <legend>{t("preferences.alarmsLegend")}</legend>
          <label className="checkbox-label operator-preferences-option">
            <input
              type="checkbox"
              checked={soundEnabled}
              onChange={(event) => toggleSound(event.target.checked)}
            />
            {t("preferences.sound")}
          </label>
          <label className="checkbox-label operator-preferences-option">
            <input
              type="checkbox"
              checked={browserNotify}
              disabled={notifyPermission === "unsupported"}
              onChange={(event) => void toggleBrowserNotify(event.target.checked)}
            />
            {t("preferences.browserNotify")}
          </label>
          {notifyPermission === "denied" && (
            <p className="hint operator-preferences-hint">{t("preferences.browserNotifyDenied")}</p>
          )}
          {notifyPermission === "unsupported" && (
            <p className="hint operator-preferences-hint">{t("preferences.browserNotifyUnsupported")}</p>
          )}
        </fieldset>
        <div className="operator-preferences-shell">
          <ShellPreferences />
        </div>
      </div>
    </details>
  );
}
