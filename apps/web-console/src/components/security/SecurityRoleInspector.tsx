import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Space, Tag, Typography } from "antd";
import { useTranslation } from "react-i18next";
import { fetchSecurityRoles, updateSecurityRole } from "../../api/securityRoles";
import { deleteObject } from "../../api";
import { roleNameFromSecurityRolePath } from "../../utils/security/securityRolePath";
import { localizedRoleDescription } from "../../utils/security/localizedRoleDescription";
import ObjectTreeIcon from "../icons/ObjectTreeIcon";

interface SecurityRoleInspectorProps {
  path: string;
  canManage: boolean;
  onDeleted: () => void;
}

export default function SecurityRoleInspector({
  path,
  canManage,
  onDeleted,
}: SecurityRoleInspectorProps) {
  const { t } = useTranslation(["security", "common"]);
  const roleName = roleNameFromSecurityRolePath(path);
  const queryClient = useQueryClient();
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");

  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
  });

  const role = rolesQuery.data?.find((item) => item.name === roleName);
  const baselineDescription = role
    ? localizedRoleDescription(t, role.name, role.description)
    : "";

  useEffect(() => {
    if (!role) {
      return;
    }
    setDisplayName(role.displayName);
    setDescription(baselineDescription);
  }, [role, baselineDescription]);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateSecurityRole(roleName, {
        displayName: displayName.trim(),
        description: description.trim(),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-roles"] });
      queryClient.invalidateQueries({ queryKey: ["object", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteObject(path),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-roles"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onDeleted();
    },
  });

  if (rolesQuery.isLoading) {
    return <div className="inspector-empty">{t("role.loading")}</div>;
  }

  if (rolesQuery.error || !role) {
    return <div className="inspector-empty error">{t("role.notFound")}</div>;
  }

  const dirty = displayName !== role.displayName || description !== baselineDescription;

  return (
    <div className="inspector security-user-inspector">
      <header className="inspector-header security-user-header">
        <div className="inspector-title-row">
          <ObjectTreeIcon path={path} type="ROLE" size={28} />
          <div className="security-user-heading">
            <div className="security-user-title-line">
              <Typography.Title level={3} style={{ margin: 0 }}>
                {displayName.trim() || role.name}
              </Typography.Title>
              <Tag>{role.name}</Tag>
              {role.builtIn && <Tag color="success">{t("role.builtIn")}</Tag>}
            </div>
            <p className="security-user-meta">
              <Typography.Text code className="path-code">{path}</Typography.Text>
            </p>
          </div>
        </div>
        {canManage && !role.builtIn && (
          <div className="inspector-actions">
            <Button
              danger
              loading={deleteMutation.isPending}
              onClick={() => {
                if (confirm(t("common:action.confirmDeleteRole", { name: role.name }))) {
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
          <Typography.Title level={5}>{t("role.properties")}</Typography.Title>
          <Form
            layout="vertical"
            onFinish={() => {
              if (canManage && dirty) {
                saveMutation.mutate();
              }
            }}
          >
            <Form.Item label={t("roles.column.name")}>
              <Input value={role.name} readOnly />
            </Form.Item>
            <Form.Item label={t("common:field.displayName")}>
              <Input
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                disabled={!canManage}
                placeholder={t("role.displayNamePlaceholder")}
              />
            </Form.Item>
            <Form.Item label={t("common:field.description")}>
              <Input.TextArea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                disabled={!canManage}
                rows={3}
                placeholder={t("role.descriptionPlaceholder")}
              />
            </Form.Item>

            {canManage && (
              <Space wrap>
                <Button type="primary" htmlType="submit" disabled={!dirty} loading={saveMutation.isPending}>
                  {saveMutation.isPending ? t("common:action.saving") : t("common:action.save")}
                </Button>
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
      </div>

      {deleteMutation.error && (
        <Alert type="error" showIcon message={String(deleteMutation.error)} style={{ marginTop: 12 }} />
      )}
    </div>
  );
}
