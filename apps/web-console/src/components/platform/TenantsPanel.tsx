import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { assignTenantUser, createTenant, deleteTenant, fetchTenants, type TenantSummary } from "../../api/tenants";

interface TenantsPanelProps {
  canManage: boolean;
  onSelectPath: (path: string) => void;
}

interface CreatedAdminCredentials {
  tenantId: string;
  username: string;
  password?: string;
  platformPath: string;
}

export default function TenantsPanel({ canManage, onSelectPath }: TenantsPanelProps) {
  const { t } = useTranslation(["security", "common"]);
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [adminPassword, setAdminPassword] = useState("");
  const [createdAdmin, setCreatedAdmin] = useState<CreatedAdminCredentials | null>(null);
  const [copyHint, setCopyHint] = useState<string | null>(null);
  const [assignUsername, setAssignUsername] = useState("operator");
  const [assignTenantId, setAssignTenantId] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const tenantsQuery = useQuery({
    queryKey: ["tenants"],
    queryFn: fetchTenants,
    enabled: canManage,
  });

  const createMutation = useMutation({
    mutationFn: () => {
      setFormError(null);
      setCopyHint(null);
      if (!tenantId.trim() || !displayName.trim()) {
        throw new Error(t("tenants.error.required"));
      }
      return createTenant({
        tenantId: tenantId.trim().toLowerCase(),
        displayName: displayName.trim(),
        enabled: true,
        adminPassword: adminPassword.trim() || undefined,
      });
    },
    onSuccess: (tenant) => {
      queryClient.invalidateQueries({ queryKey: ["tenants"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      const id = tenant.tenantId;
      setTenantId("");
      setDisplayName("");
      setAdminPassword("");
      // Stay on Tenants panel so one-time credentials remain visible.
      if (tenant.adminUsername) {
        setCreatedAdmin({
          tenantId: id,
          username: tenant.adminUsername,
          password: tenant.adminPassword,
          platformPath: tenant.platformPath,
        });
      } else {
        setCreatedAdmin(null);
      }
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const assignMutation = useMutation({
    mutationFn: () => assignTenantUser(assignTenantId, assignUsername.trim().toLowerCase()),
    onSuccess: () => setFormError(null),
    onError: (error: Error) => setFormError(error.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteTenant(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ["tenants"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      if (createdAdmin?.tenantId === id) {
        setCreatedAdmin(null);
      }
      if (assignTenantId === id) {
        setAssignTenantId("");
      }
      setFormError(null);
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const confirmDelete = (id: string) => {
    if (!window.confirm(t("tenants.deleteConfirm", { tenantId: id }))) {
      return;
    }
    deleteMutation.mutate(id);
  };

  const copyCredentials = async () => {
    if (!createdAdmin) {
      return;
    }
    const text = createdAdmin.password
      ? `${createdAdmin.username}\n${createdAdmin.password}`
      : createdAdmin.username;
    try {
      await navigator.clipboard.writeText(text);
      setCopyHint(t("tenants.credentialsCopied"));
    } catch {
      setCopyHint(t("common:action.copyFailed"));
    }
  };

  if (!canManage) {
    return <Typography.Paragraph type="secondary">{t("tenants.adminOnly")}</Typography.Paragraph>;
  }

  const tenantRows = tenantsQuery.data ?? [];
  const tenantColumns: ColumnsType<TenantSummary> = [
    {
      title: t("tenants.column.tenantId"),
      dataIndex: "tenantId",
      key: "tenantId",
      render: (_tenantId, tenant) => (
        <Button type="link" onClick={() => onSelectPath(tenant.platformPath)} style={{ paddingInline: 0 }}>
          <Typography.Text code>{tenant.tenantId}</Typography.Text>
        </Button>
      ),
    },
    {
      title: t("tenants.column.displayName"),
      dataIndex: "displayName",
      key: "displayName",
    },
    {
      title: t("tenants.column.platformPath"),
      dataIndex: "platformPath",
      key: "platformPath",
      render: (platformPath: string) => <Typography.Text code>{platformPath}</Typography.Text>,
    },
    {
      title: t("tenants.column.enabled"),
      dataIndex: "enabled",
      key: "enabled",
      render: (enabled: boolean) => (
        <Tag color={enabled ? "success" : "default"}>
          {enabled ? t("common:action.yes") : t("common:action.no")}
        </Tag>
      ),
    },
    {
      title: t("tenants.column.actions"),
      key: "actions",
      render: (_value, tenant) => (
        <Button
          size="small"
          disabled={deleteMutation.isPending}
          onClick={() => confirmDelete(tenant.tenantId)}
        >
          {t("tenants.deleteTenant")}
        </Button>
      ),
    },
  ];

  return (
    <section className="tenants-panel">
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
      <header className="security-users-header">
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            {t("tenants.title")}
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t("tenants.subtitle")}
          </Typography.Paragraph>
        </div>
      </header>

      {createdAdmin && (
        <div className="tenants-credentials-banner" role="status">
          <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t("tenants.credentialsTitle", { tenantId: createdAdmin.tenantId })}
          </Typography.Title>
          <Alert type="warning" showIcon message={t("tenants.credentialsWarning")} />
          <dl className="tenants-credentials-fields">
            <div>
              <dt>{t("tenants.field.adminUsername")}</dt>
              <dd><Typography.Text code>{createdAdmin.username}</Typography.Text></dd>
            </div>
            {createdAdmin.password ? (
              <div>
                <dt>{t("tenants.field.adminPasswordOnce")}</dt>
                <dd><Typography.Text code>{createdAdmin.password}</Typography.Text></dd>
              </div>
            ) : null}
          </dl>
          <Space className="tenants-credentials-actions" wrap>
            <Button type="primary" onClick={() => void copyCredentials()}>
              {t("tenants.copyCredentials")}
            </Button>
            <Button
              onClick={() => onSelectPath(createdAdmin.platformPath)}
            >
              {t("tenants.openPlatform")}
            </Button>
            <Button onClick={() => setCreatedAdmin(null)}>
              {t("tenants.dismissCredentials")}
            </Button>
          </Space>
          {copyHint && <Typography.Text type="secondary">{copyHint}</Typography.Text>}
          </Space>
        </div>
      )}

      {formError && <Alert type="error" showIcon message={formError} />}

      <Table<TenantSummary>
        size="small"
        rowKey="tenantId"
        columns={tenantColumns}
        dataSource={tenantRows}
        loading={tenantsQuery.isLoading}
        pagination={false}
      />

      <Form
        layout="vertical"
        className="driver-config-form"
        onFinish={() => {
          createMutation.mutate();
        }}
      >
        <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t("tenants.newTenant")}
        </Typography.Title>
        <Space size="middle" align="start" wrap>
          <Form.Item label={t("tenants.field.tenantId")} style={{ minWidth: 240, marginBottom: 0 }}>
            <Input
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              placeholder={t("tenants.field.tenantIdHint")}
            />
          </Form.Item>
          <Form.Item label={t("tenants.field.displayName")} style={{ minWidth: 260, marginBottom: 0 }}>
            <Input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder={t("tenants.field.displayNameHint")}
            />
          </Form.Item>
          <Form.Item label={t("tenants.field.adminPassword")} style={{ minWidth: 260, marginBottom: 0 }}>
            <Input.Password
              value={adminPassword}
              onChange={(e) => setAdminPassword(e.target.value)}
              placeholder={t("tenants.field.adminPasswordHint")}
              autoComplete="new-password"
            />
          </Form.Item>
        </Space>
        <Typography.Text type="secondary">{t("tenants.localAdminHint")}</Typography.Text>
        <Button htmlType="submit" type="primary" disabled={createMutation.isPending} loading={createMutation.isPending}>
          {t("tenants.createTenant")}
        </Button>
        </Space>
      </Form>

      <section className="federation-probe">
        <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t("tenants.assignUser")}
        </Typography.Title>
        <Form layout="vertical">
          <Space size="middle" align="start" wrap>
            <Form.Item label={t("tenants.field.tenant")} style={{ minWidth: 240, marginBottom: 0 }}>
              <Select
                value={assignTenantId}
                onChange={setAssignTenantId}
                options={[
                  { value: "", label: t("common:empty.dash") },
                  ...tenantRows.map((tenant) => ({ value: tenant.tenantId, label: tenant.tenantId })),
                ]}
              />
            </Form.Item>
            <Form.Item label={t("tenants.field.username")} style={{ minWidth: 240, marginBottom: 0 }}>
              <Input value={assignUsername} onChange={(e) => setAssignUsername(e.target.value)} />
            </Form.Item>
          </Space>
        </Form>
        <Button
          disabled={assignMutation.isPending || !assignTenantId}
          loading={assignMutation.isPending}
          onClick={() => assignMutation.mutate()}
        >
          {t("tenants.assignTenant")}
        </Button>
        </Space>
      </section>
      </Space>
    </section>
  );
}
