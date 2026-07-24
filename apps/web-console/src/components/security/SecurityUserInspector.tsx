import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Modal, Select, Space, Switch, Tag, Typography } from "antd";
import { useTranslation } from "react-i18next";
import {
  fetchSecurityUsers,
  setSecurityUserPassword,
  updateSecurityUser,
} from "../../api/securityUsers";
import { fetchSecurityRoles } from "../../api/securityRoles";
import { fetchOperatorApps, type OperatorAppEntry } from "../../api/operatorApps";
import { deleteObject } from "../../api";
import { usernameFromSecurityUserPath } from "../../utils/security/securityUserPath";
import { localizedRoleDescription } from "../../utils/security/localizedRoleDescription";
import SecurityUserAutoStartFields from "./SecurityUserAutoStartFields";
import ObjectTreeIcon from "../icons/ObjectTreeIcon";

async function loadOperatorApps(): Promise<OperatorAppEntry[]> {
  return fetchOperatorApps();
}

function serverSupportsAutoStart(users: Awaited<ReturnType<typeof fetchSecurityUsers>> | undefined): boolean {
  return Boolean(users?.some((user) => Object.prototype.hasOwnProperty.call(user, "autoStartEnabled")));
}

interface SecurityUserInspectorProps {
  path: string;
  canManage: boolean;
  onDeleted: () => void;
}

export default function SecurityUserInspector({
  path,
  canManage,
  onDeleted,
}: SecurityUserInspectorProps) {
  const { t } = useTranslation(["security", "common"]);
  const username = usernameFromSecurityUserPath(path);
  const queryClient = useQueryClient();
  const [displayName, setDisplayName] = useState("");
  const [role, setRole] = useState("operator");
  const [enabled, setEnabled] = useState(true);
  const [showPasswordDialog, setShowPasswordDialog] = useState(false);
  const [newPassword, setNewPassword] = useState("");

  const usersQuery = useQuery({
    queryKey: ["security-users"],
    queryFn: fetchSecurityUsers,
  });

  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
  });

  const user = usersQuery.data?.find((item) => item.username === username);

  const appsQuery = useQuery({
    queryKey: ["operator-apps"],
    queryFn: loadOperatorApps,
  });

  useEffect(() => {
    if (!user) {
      return;
    }
    setDisplayName(user.displayName);
    setRole(user.roles[0] ?? "operator");
    setEnabled(user.enabled);
  }, [user]);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateSecurityUser(username, {
        displayName: displayName.trim(),
        roles: [role],
        enabled,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-users"] });
      queryClient.invalidateQueries({ queryKey: ["object", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
    },
  });

  const passwordMutation = useMutation({
    mutationFn: () => setSecurityUserPassword(username, newPassword),
    onSuccess: () => {
      setShowPasswordDialog(false);
      setNewPassword("");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteObject(path),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-users"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onDeleted();
    },
  });

  if (usersQuery.isLoading) {
    return <div className="inspector-empty">{t("user.loading")}</div>;
  }

  if (usersQuery.error || !user) {
    return <div className="inspector-empty error">{t("user.notFound")}</div>;
  }

  const serverReady = serverSupportsAutoStart(usersQuery.data);
  const dirty =
    displayName !== user.displayName
    || role !== (user.roles[0] ?? "operator")
    || enabled !== user.enabled;

  return (
    <div className="inspector security-user-inspector">
      <header className="inspector-header security-user-header">
        <div className="inspector-title-row">
          <ObjectTreeIcon path={path} type="USER" size={28} />
          <div className="security-user-heading">
            <div className="security-user-title-line">
              <Typography.Title level={3} style={{ margin: 0 }}>
                {displayName.trim() || user.username}
              </Typography.Title>
              <Tag>{role}</Tag>
              <Tag color={enabled ? "success" : "default"}>
                {enabled ? t("user.statusActive") : t("user.statusDisabled")}
              </Tag>
            </div>
            <p className="security-user-meta">
              <Typography.Text code>@{user.username}</Typography.Text>
              <span className="security-user-meta-sep">·</span>
              <Typography.Text code className="path-code">{path}</Typography.Text>
            </p>
          </div>
        </div>
        {canManage && (
          <div className="inspector-actions">
            <Button
              danger
              loading={deleteMutation.isPending}
              onClick={() => {
                if (confirm(t("common:action.confirmDeleteUser", { name: user.username }))) {
                  deleteMutation.mutate();
                }
              }}
            >
              {t("common:action.delete")}
            </Button>
          </div>
        )}
      </header>

      {!canManage && (
        <Alert type="info" showIcon message={t("role.readonlyHint")} style={{ marginBottom: 12 }} />
      )}

      <div className="security-user-cards">
        <section className="security-user-card">
          <Typography.Title level={5}>{t("user.account")}</Typography.Title>
          <Form
            layout="vertical"
            onFinish={() => {
              if (canManage && dirty) {
                saveMutation.mutate();
              }
            }}
          >
            <Form.Item label={t("users.column.login")}>
              <Input value={user.username} readOnly />
            </Form.Item>
            <Form.Item label={t("common:field.displayName")}>
              <Input
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                disabled={!canManage}
                placeholder={t("user.displayNamePlaceholder")}
              />
            </Form.Item>
            <Form.Item label={t("user.role")}>
              <Select
                value={role}
                onChange={setRole}
                disabled={!canManage || (rolesQuery.data ?? []).length === 0}
                options={(rolesQuery.data ?? []).map((item) => {
                  const desc = localizedRoleDescription(t, item.name, item.description);
                  return {
                    value: item.name,
                    label: desc ? `${item.name} — ${desc}` : item.name,
                  };
                })}
              />
            </Form.Item>
            <Form.Item label={t("user.status")}>
              <Space>
                <Switch checked={enabled} disabled={!canManage} onChange={setEnabled} />
                <Typography.Text type="secondary">
                  {enabled ? t("user.statusActiveHint") : t("user.statusInactiveHint")}
                </Typography.Text>
              </Space>
            </Form.Item>

            {canManage && (
              <Space wrap style={{ marginTop: 8 }}>
                <Button type="primary" htmlType="submit" disabled={!dirty} loading={saveMutation.isPending}>
                  {saveMutation.isPending ? t("common:action.saving") : t("common:action.save")}
                </Button>
                <Button onClick={() => setShowPasswordDialog(true)}>{t("user.changePassword")}</Button>
                {saveMutation.isSuccess && !dirty && (
                  <Typography.Text type="success">{t("user.changesSaved")}</Typography.Text>
                )}
                {saveMutation.error && (
                  <Typography.Text type="danger">{String(saveMutation.error)}</Typography.Text>
                )}
              </Space>
            )}
          </Form>
        </section>

        <section className="security-user-card">
          <Typography.Title level={5}>{t("user.autoStart")}</Typography.Title>
          <Typography.Paragraph type="secondary">{t("user.autoStartDesc")}</Typography.Paragraph>
          <SecurityUserAutoStartFields
            user={user}
            apps={appsQuery.data ?? []}
            serverReady={serverReady}
            disabled={!canManage}
          />
        </section>
      </div>

      <Modal
        title={t("user.passwordDialogTitle")}
        open={showPasswordDialog}
        onCancel={() => setShowPasswordDialog(false)}
        okText={t("common:action.save")}
        cancelText={t("common:action.cancel")}
        confirmLoading={passwordMutation.isPending}
        destroyOnHidden
        onOk={() => {
          if (newPassword.length < 4) return;
          passwordMutation.mutate();
        }}
      >
        <Typography.Paragraph type="secondary">
          {t("user.passwordDialogHint")} <Typography.Text code>{user.username}</Typography.Text>
        </Typography.Paragraph>
        <Form layout="vertical">
          <Form.Item label={t("user.newPassword")} required>
            <Input.Password
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoFocus
            />
          </Form.Item>
          {passwordMutation.error && (
            <Alert type="error" showIcon message={String(passwordMutation.error)} />
          )}
        </Form>
      </Modal>

      {deleteMutation.error && (
        <Alert type="error" showIcon message={String(deleteMutation.error)} style={{ marginTop: 12 }} />
      )}
    </div>
  );
}
