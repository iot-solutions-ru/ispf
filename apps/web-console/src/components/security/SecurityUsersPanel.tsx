import { PlusOutlined } from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchSecurityUsers, type SecurityUserSummary } from "../../api/securityUsers";
import CreateSecurityUserDialog from "./CreateSecurityUserDialog";

interface SecurityUsersPanelProps {
  canManage: boolean;
  onSelectUser: (path: string) => void;
}

export default function SecurityUsersPanel({ canManage, onSelectUser }: SecurityUsersPanelProps) {
  const { t } = useTranslation(["security", "common"]);
  const [showCreate, setShowCreate] = useState(false);

  const usersQuery = useQuery({
    queryKey: ["security-users"],
    queryFn: fetchSecurityUsers,
  });

  const columns: ColumnsType<SecurityUserSummary> = useMemo(
    () => [
      {
        title: t("users.column.login"),
        dataIndex: "username",
        key: "username",
        render: (username: string, user) => (
          <Button type="link" onClick={() => onSelectUser(user.objectPath)} style={{ paddingInline: 0 }}>
            <Typography.Text code>{username}</Typography.Text>
          </Button>
        ),
      },
      {
        title: t("users.column.displayName"),
        dataIndex: "displayName",
        key: "displayName",
      },
      {
        title: t("users.column.roles"),
        dataIndex: "roles",
        key: "roles",
        render: (roles: string[]) => (
          <Space size={[4, 4]} wrap>
            {roles.map((role) => (
              <Tag key={role}>{role}</Tag>
            ))}
          </Space>
        ),
      },
      {
        title: t("users.column.active"),
        dataIndex: "enabled",
        key: "enabled",
        width: 100,
        render: (enabled: boolean) => (
          <Tag color={enabled ? "success" : "default"}>
            {enabled ? t("common:action.yes") : t("common:action.no")}
          </Tag>
        ),
      },
    ],
    [onSelectUser, t]
  );

  if (!canManage) {
    return <Typography.Paragraph type="secondary">{t("users.adminOnly")}</Typography.Paragraph>;
  }

  return (
    <section className="security-users-panel">
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <header className="security-users-header" style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {t("users.title")}
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t("users.subtitle")}
            </Typography.Paragraph>
          </div>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setShowCreate(true)}>
            {t("users.create")}
          </Button>
        </header>

        {usersQuery.error && (
          <Alert type="error" showIcon message={String(usersQuery.error)} />
        )}

        <Table<SecurityUserSummary>
          size="small"
          rowKey="username"
          loading={usersQuery.isLoading}
          columns={columns}
          dataSource={usersQuery.data ?? []}
          pagination={false}
        />
      </Space>

      <CreateSecurityUserDialog
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={(objectPath) => {
          setShowCreate(false);
          onSelectUser(objectPath);
        }}
      />
    </section>
  );
}
