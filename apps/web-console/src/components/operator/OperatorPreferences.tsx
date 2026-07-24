import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Button, Dropdown, Space, Switch, Typography } from "antd";
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

  const content = (
    <Space direction="vertical" size="middle" style={{ width: 280 }}>
      <div>
        <Typography.Text strong>{t("preferences.alarmsLegend")}</Typography.Text>
        <Space direction="vertical" size="small" style={{ width: "100%", marginTop: 8 }}>
          <Space style={{ width: "100%", justifyContent: "space-between" }}>
            <Typography.Text>{t("preferences.sound")}</Typography.Text>
            <Switch checked={soundEnabled} onChange={toggleSound} size="small" />
          </Space>
          <Space style={{ width: "100%", justifyContent: "space-between" }}>
            <Typography.Text>{t("preferences.browserNotify")}</Typography.Text>
            <Switch
              checked={browserNotify}
              disabled={notifyPermission === "unsupported"}
              onChange={(checked) => void toggleBrowserNotify(checked)}
              size="small"
            />
          </Space>
        </Space>
        {notifyPermission === "denied" && (
          <Alert
            type="warning"
            showIcon
            style={{ marginTop: 8 }}
            message={t("preferences.browserNotifyDenied")}
          />
        )}
        {notifyPermission === "unsupported" && (
          <Alert
            type="info"
            showIcon
            style={{ marginTop: 8 }}
            message={t("preferences.browserNotifyUnsupported")}
          />
        )}
      </div>
      <div className="operator-preferences-shell">
        <ShellPreferences />
      </div>
    </Space>
  );

  return (
    <Dropdown
      trigger={["click"]}
      popupRender={() => content}
      menu={{ items: [] }}
      placement="bottomRight"
    >
      <Button size="small" className="operator-preferences-trigger" title={t("preferences.title")}>
        {t("preferences.title")}
      </Button>
    </Dropdown>
  );
}
