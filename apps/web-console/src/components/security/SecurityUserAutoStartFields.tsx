import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, Select, Space, Switch, Typography } from "antd";
import { useTranslation } from "react-i18next";
import { updateSecurityUser, type SecurityUserSummary } from "../../api/securityUsers";
import type { OperatorAppEntry } from "../../api/operatorApps";

interface SecurityUserAutoStartFieldsProps {
  user: SecurityUserSummary;
  apps: OperatorAppEntry[];
  serverReady: boolean;
  disabled?: boolean;
}

export default function SecurityUserAutoStartFields({
  user,
  apps,
  serverReady,
  disabled = false,
}: SecurityUserAutoStartFieldsProps) {
  const { t } = useTranslation(["security", "common"]);
  const queryClient = useQueryClient();
  const defaultApp = apps[0]?.appId ?? "platform";
  const enabled = user.autoStartEnabled === true;
  const selectedApp = user.autoStartApp || defaultApp;

  const mutation = useMutation({
    mutationFn: async (payload: Parameters<typeof updateSecurityUser>[1]) => {
      const updated = await updateSecurityUser(user.username, payload);
      if (payload.autoStartEnabled === true && updated.autoStartEnabled !== true) {
        throw new Error(t("autostart.saveFailed"));
      }
      return updated;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-users"] });
      queryClient.invalidateQueries({ queryKey: ["security-user", user.username] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables"] });
    },
  });

  const controlsDisabled = disabled || !serverReady || mutation.isPending;

  return (
    <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
      <Space align="start" style={{ justifyContent: "space-between", width: "100%" }}>
        <div>
          <Typography.Text strong>{t("autostart.onLogin")}</Typography.Text>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {enabled ? t("autostart.enabledHint") : t("autostart.disabledHint")}
          </Typography.Paragraph>
        </div>
        <Switch
          checked={enabled}
          disabled={controlsDisabled}
          onChange={(nextEnabled) => {
            mutation.mutate({
              autoStartEnabled: nextEnabled,
              autoStartApp: nextEnabled ? selectedApp : user.autoStartApp ?? null,
            });
          }}
        />
      </Space>

      <div>
        <Typography.Text strong style={{ display: "block", marginBottom: 6 }}>
          {t("autostart.app")}
        </Typography.Text>
        <Select
          style={{ width: "100%" }}
          value={user.autoStartApp ?? ""}
          disabled={controlsDisabled || !enabled}
          onChange={(appId) => {
            if (!appId) {
              mutation.mutate({ autoStartEnabled: false, autoStartApp: null });
              return;
            }
            mutation.mutate({ autoStartEnabled: true, autoStartApp: appId });
          }}
          options={[
            { value: "", label: t("autostart.notSelected") },
            ...apps.map((app) => ({
              value: app.appId,
              label: `${app.title} (${app.appId})`,
            })),
          ]}
        />
      </div>

      {!serverReady && <Alert type="info" showIcon message={t("autostart.serverNotReady")} />}
      {mutation.error && <Alert type="error" showIcon message={String(mutation.error)} />}
    </Space>
  );
}
