import { useQuery } from "@tanstack/react-query";
import { Alert, Card, Col, Row, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { fetchSecurityRoles, type SecurityRoleSummary } from "../../api/securityRoles";
import { localizedRoleDescription } from "../../utils/security/localizedRoleDescription";
import SecurityMfaPanel from "./SecurityMfaPanel";
import { SECURITY_ROLES_ROOT } from "../../utils/security/securityRolePath";
import { SECURITY_USERS_ROOT } from "../../utils/security/securityUserPath";

interface SecurityRootPanelProps {
  canManage: boolean;
  onSelectPath: (path: string) => void;
}

export default function SecurityRootPanel({ canManage, onSelectPath }: SecurityRootPanelProps) {
  const { t } = useTranslation(["security", "common"]);
  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
    enabled: canManage,
  });

  const templates = (rolesQuery.data ?? []).filter((role) => role.template);

  const columns: ColumnsType<SecurityRoleSummary> = useMemo(
    () => [
      {
        title: t("roles.column.name"),
        dataIndex: "name",
        key: "name",
        render: (name: string, role) => (
          <Typography.Link onClick={() => onSelectPath(role.objectPath)}>
            <Typography.Text code>{name}</Typography.Text>
          </Typography.Link>
        ),
      },
      {
        title: t("roles.column.displayName"),
        dataIndex: "displayName",
        key: "displayName",
      },
      {
        title: t("roles.column.description"),
        key: "description",
        render: (_, role) =>
          localizedRoleDescription(t, role.name, role.description) || t("common:empty.dash"),
      },
    ],
    [onSelectPath, t]
  );

  return (
    <section className="security-users-panel">
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        {t("securityRoot.title")}
      </Typography.Title>
      <Typography.Paragraph type="secondary">{t("securityRoot.subtitle")}</Typography.Paragraph>

      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={12}>
          <Card hoverable onClick={() => onSelectPath(SECURITY_USERS_ROOT)}>
            <Typography.Title level={5} style={{ marginTop: 0 }}>
              {t("users.title")}
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t("securityRoot.usersHint")}
            </Typography.Paragraph>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card hoverable onClick={() => onSelectPath(SECURITY_ROLES_ROOT)}>
            <Typography.Title level={5} style={{ marginTop: 0 }}>
              {t("roles.title")}
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t("securityRoot.rolesHint")}
            </Typography.Paragraph>
          </Card>
        </Col>
      </Row>

      <SecurityMfaPanel />

      <Typography.Title level={5}>{t("roleTemplates.title")}</Typography.Title>
      <Typography.Paragraph type="secondary">{t("roleTemplates.subtitle")}</Typography.Paragraph>
      {!canManage && <Typography.Paragraph type="secondary">{t("roles.adminOnly")}</Typography.Paragraph>}
      {canManage && rolesQuery.error && (
        <Alert type="error" showIcon message={String(rolesQuery.error)} style={{ marginBottom: 12 }} />
      )}
      {canManage && (
        <Table<SecurityRoleSummary>
          size="small"
          rowKey="name"
          loading={rolesQuery.isLoading}
          columns={columns}
          dataSource={templates}
          pagination={false}
        />
      )}
    </section>
  );
}
