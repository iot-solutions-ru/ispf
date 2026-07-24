import { PlusOutlined } from "@ant-design/icons";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchSecurityRoles, type SecurityRoleSummary } from "../../api/securityRoles";
import { localizedRoleDescription } from "../../utils/security/localizedRoleDescription";
import CreateSecurityRoleDialog from "./CreateSecurityRoleDialog";

interface SecurityRolesPanelProps {
  canManage: boolean;
  onSelectRole: (path: string) => void;
}

export default function SecurityRolesPanel({ canManage, onSelectRole }: SecurityRolesPanelProps) {
  const { t } = useTranslation(["security", "common"]);
  const [showCreate, setShowCreate] = useState(false);
  const queryClient = useQueryClient();

  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
  });

  const columns: ColumnsType<SecurityRoleSummary> = useMemo(
    () => [
      {
        title: t("roles.column.name"),
        dataIndex: "name",
        key: "name",
        render: (name: string, role) => (
          <Button type="link" onClick={() => onSelectRole(role.objectPath)} style={{ paddingInline: 0 }}>
            <Typography.Text code>{name}</Typography.Text>
          </Button>
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
      {
        title: t("roles.column.type"),
        key: "type",
        width: 120,
        render: (_, role) => (
          <Tag>
            {role.builtIn
              ? t("roles.type.builtIn")
              : role.template
                ? t("roles.type.template")
                : t("roles.type.custom")}
          </Tag>
        ),
      },
    ],
    [onSelectRole, t]
  );

  if (!canManage) {
    return <Typography.Paragraph type="secondary">{t("roles.adminOnly")}</Typography.Paragraph>;
  }

  return (
    <section className="security-users-panel">
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <header className="security-users-header" style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {t("roles.title")}
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t("roles.subtitle")}
            </Typography.Paragraph>
          </div>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setShowCreate(true)}>
            {t("roles.create")}
          </Button>
        </header>

        {rolesQuery.error && <Alert type="error" showIcon message={String(rolesQuery.error)} />}

        <Table<SecurityRoleSummary>
          size="small"
          rowKey="name"
          loading={rolesQuery.isLoading}
          columns={columns}
          dataSource={rolesQuery.data ?? []}
          pagination={false}
        />
      </Space>

      <CreateSecurityRoleDialog
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={(objectPath) => {
          queryClient.invalidateQueries({ queryKey: ["security-roles"] });
          queryClient.invalidateQueries({ queryKey: ["objects"] });
          setShowCreate(false);
          onSelectRole(objectPath);
        }}
      />
    </section>
  );
}
