import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import { Alert, Button, Checkbox, Form, Input, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useTranslation } from "react-i18next";
import type { FederationPeer, FederationPeerPayload, FederationPeerHealthLevel } from "../../api/federation";
import { formatTokenExpiry } from "../../api/federation";
import { getStoredSession } from "../../auth/session";

interface FederationPeersTabProps {
  peersQuery: UseQueryResult<FederationPeer[], Error>;
  form: FederationPeerPayload;
  setForm: React.Dispatch<React.SetStateAction<FederationPeerPayload>>;
  useServiceAccount: boolean;
  setUseServiceAccount: (value: boolean) => void;
  serviceAccountUsername: string;
  setServiceAccountUsername: (value: string) => void;
  serviceAccountPassword: string;
  setServiceAccountPassword: (value: string) => void;
  remoteLoginUsername: string;
  setRemoteLoginUsername: (value: string) => void;
  remoteLoginPassword: string;
  setRemoteLoginPassword: (value: string) => void;
  formError: string | null;
  tokenApiMissing: boolean;
  createMutation: UseMutationResult<unknown, Error, void, unknown>;
  deleteMutation: UseMutationResult<unknown, Error, string, unknown>;
  onSyncCatalog: (peer: FederationPeer) => void;
  onSyncSubtree: (peer: FederationPeer) => void;
  refreshTokenMutation: UseMutationResult<unknown, Error, string, unknown>;
  remoteTokenMutation: UseMutationResult<unknown, Error, void, unknown>;
  setFormError: (value: string | null) => void;
  setSyncFeedback: (value: string | null) => void;
}

export default function FederationPeersTab({
  peersQuery,
  form,
  setForm,
  useServiceAccount,
  setUseServiceAccount,
  serviceAccountUsername,
  setServiceAccountUsername,
  serviceAccountPassword,
  setServiceAccountPassword,
  remoteLoginUsername,
  setRemoteLoginUsername,
  remoteLoginPassword,
  setRemoteLoginPassword,
  formError,
  tokenApiMissing,
  createMutation,
  deleteMutation,
  onSyncCatalog,
  onSyncSubtree,
  refreshTokenMutation,
  remoteTokenMutation,
  setFormError,
  setSyncFeedback,
}: FederationPeersTabProps) {
  const { t } = useTranslation(["federation", "common"]);
  const peers = peersQuery.data ?? [];

  const healthBadgeColor = (level: FederationPeerHealthLevel | undefined): string => {
    if (level === "GREEN") return "success";
    if (level === "YELLOW") return "warning";
    return "error";
  };

  const healthBadge = (peer: FederationPeer): React.ReactNode => {
    const level = peer.healthLevel ?? "YELLOW";
    return (
      <Tag color={healthBadgeColor(level)} title={peer.healthSummary ?? t("peers.healthUnknown")}>
        {t(`peers.health.${level.toLowerCase()}`)}
      </Tag>
    );
  };

  const authBadge = (peer: FederationPeer): React.ReactNode => {
    if (peer.authMode === "SERVICE_ACCOUNT") {
      return (
        <Space size={4} wrap>
          <Tag>{t("peers.authAuto")}</Tag>
          <Typography.Text type="secondary">{formatTokenExpiry(peer.tokenExpiresAt)}</Typography.Text>
          {peer.authStatus === "FAILED" ? (
            <Tag color="error" title={t("peers.authError")}>{t("peers.authError")}</Tag>
          ) : (
            <Tag color="success">OK</Tag>
          )}
        </Space>
      );
    }
    return peer.hasAuthToken ? (
      <Tag color="success">OK</Tag>
    ) : (
      <Tag>{t("peers.noToken")}</Tag>
    );
  };

  const columns: ColumnsType<FederationPeer> = [
    {
      title: t("peers.column.name"),
      dataIndex: "name",
      key: "name",
      render: (_value, peer) => (
        <Space size={4} wrap>
          <Typography.Text code>{peer.name}</Typography.Text>
          {peer.connectionMode === "TUNNEL_INBOUND" && (
            <Tag>
              {t("peers.tunnelBadge")}{peer.tunnelConnected ? "" : t("peers.tunnelOffline")}
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: t("peers.column.url"),
      dataIndex: "baseUrl",
      key: "baseUrl",
    },
    {
      title: t("peers.column.prefix"),
      dataIndex: "pathPrefix",
      key: "pathPrefix",
      render: (value: string | undefined) => <Typography.Text code>{value || t("common:empty.dash")}</Typography.Text>,
    },
    {
      title: t("peers.column.health"),
      key: "health",
      render: (_value, peer) => healthBadge(peer),
    },
    {
      title: t("peers.column.auth"),
      key: "auth",
      render: (_value, peer) => authBadge(peer),
    },
    {
      title: t("peers.column.token"),
      dataIndex: "hasAuthToken",
      key: "hasAuthToken",
      render: (hasAuthToken: boolean) => (
        <Tag color={hasAuthToken ? "success" : "default"}>
          {hasAuthToken ? t("common:action.yes") : t("common:action.no")}
        </Tag>
      ),
    },
    {
      title: t("peers.column.enabled"),
      dataIndex: "enabled",
      key: "enabled",
      render: (enabled: boolean) => (
        <Tag color={enabled ? "success" : "default"}>
          {enabled ? t("common:action.yes") : t("common:action.no")}
        </Tag>
      ),
    },
    {
      title: "",
      key: "actions",
      render: (_value, peer) => (
        <Space size={4} wrap>
          {peer.authMode === "SERVICE_ACCOUNT" && (
            <Button
              size="small"
              disabled={refreshTokenMutation.isPending}
              onClick={() => refreshTokenMutation.mutate(peer.id)}
            >
              {t("peers.refreshToken")}
            </Button>
          )}
          <Button size="small" onClick={() => onSyncCatalog(peer)}>
            {t("peers.syncCatalog")}
          </Button>
          <Button size="small" onClick={() => onSyncSubtree(peer)}>
            {t("peers.syncSubtree")}
          </Button>
          <Button
            size="small"
            danger
            disabled={deleteMutation.isPending}
            onClick={() => deleteMutation.mutate(peer.id)}
          >
            {t("common:action.delete")}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
      {formError && <Alert type="error" showIcon message={formError} />}

      <section className="panel-card">
        <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t("peers.registered")}
          </Typography.Title>
          <Table<FederationPeer>
            size="small"
            rowKey="id"
            columns={columns}
            dataSource={peers}
            loading={peersQuery.isLoading}
            pagination={false}
            locale={{ emptyText: t("peers.empty") }}
          />
        </Space>
      </section>

      <section className="panel-card driver-config-form">
        <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {t("peers.newPeer")}
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t("peers.newPeerHint")}
            </Typography.Paragraph>
          </div>
          <Form
            layout="vertical"
            onFinish={() => {
              createMutation.mutate();
            }}
          >
            <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
              <Space size="middle" align="start" wrap>
                <Form.Item label={`${t("peers.field.name")} *`} style={{ minWidth: 240, marginBottom: 0 }}>
                  <Input
                    value={form.name}
                    onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                  />
                </Form.Item>
                <Form.Item label={`${t("peers.field.url")} *`} style={{ minWidth: 280, marginBottom: 0 }}>
                  <Input
                    value={form.baseUrl}
                    onChange={(e) => setForm((prev) => ({ ...prev, baseUrl: e.target.value }))}
                  />
                </Form.Item>
                <Form.Item label={t("peers.field.prefix")} style={{ minWidth: 240, marginBottom: 0 }}>
                  <Input
                    value={form.pathPrefix ?? ""}
                    onChange={(e) => setForm((prev) => ({ ...prev, pathPrefix: e.target.value }))}
                  />
                </Form.Item>
                <Form.Item label={t("peers.field.authToken")} style={{ minWidth: 240, marginBottom: 0 }}>
                  <Input.Password
                    value={form.authToken ?? ""}
                    onChange={(e) => setForm((prev) => ({ ...prev, authToken: e.target.value }))}
                  />
                </Form.Item>
                <Form.Item label={t("peers.field.description")} style={{ minWidth: 520, marginBottom: 0 }}>
                  <Input
                    value={form.description ?? ""}
                    onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
                  />
                </Form.Item>
              </Space>
              <Checkbox checked={useServiceAccount} onChange={(e) => setUseServiceAccount(e.target.checked)}>
                {t("peers.autoRefreshToken")}
              </Checkbox>
              {useServiceAccount && (
                <Space size="middle" align="start" wrap>
                  <Form.Item label={t("peers.field.username")} style={{ minWidth: 240, marginBottom: 0 }}>
                    <Input
                      value={serviceAccountUsername}
                      onChange={(e) => setServiceAccountUsername(e.target.value)}
                    />
                  </Form.Item>
                  <Form.Item label={t("peers.field.password")} style={{ minWidth: 240, marginBottom: 0 }}>
                    <Input.Password
                      value={serviceAccountPassword}
                      onChange={(e) => setServiceAccountPassword(e.target.value)}
                    />
                  </Form.Item>
                </Space>
              )}

              <section className="federation-remote-token panel-card">
                <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
                  <div>
                    <Typography.Title level={5} style={{ margin: 0 }}>
                      {t("peers.getAuthToken")}
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                      {t("peers.getAuthTokenHint")}
                    </Typography.Paragraph>
                  </div>
                  <Button
                    size="small"
                    onClick={() => {
                      const session = getStoredSession();
                      if (!session?.token) {
                        setFormError(t("peers.noSession"));
                        return;
                      }
                      setForm((prev) => ({ ...prev, authToken: session.token }));
                      setFormError(null);
                      setSyncFeedback(t("peers.sessionTokenApplied"));
                    }}
                  >
                    {t("peers.useSessionToken")}
                  </Button>
                  <Space size="middle" align="start" wrap>
                    <Form.Item label={t("peers.remoteUsername")} style={{ minWidth: 240, marginBottom: 0 }}>
                      <Input
                        value={remoteLoginUsername}
                        onChange={(e) => setRemoteLoginUsername(e.target.value)}
                      />
                    </Form.Item>
                    <Form.Item label={t("peers.remotePassword")} style={{ minWidth: 240, marginBottom: 0 }}>
                      <Input.Password
                        value={remoteLoginPassword}
                        onChange={(e) => setRemoteLoginPassword(e.target.value)}
                      />
                    </Form.Item>
                  </Space>
                  <Button
                    disabled={remoteTokenMutation.isPending || tokenApiMissing}
                    loading={remoteTokenMutation.isPending}
                    onClick={() => remoteTokenMutation.mutate()}
                  >
                    {remoteTokenMutation.isPending ? t("peers.gettingToken") : t("peers.getRemoteToken")}
                  </Button>
                </Space>
              </section>

              <Button htmlType="submit" type="primary" disabled={createMutation.isPending} loading={createMutation.isPending}>
                {createMutation.isPending ? t("peers.adding") : t("peers.add")}
              </Button>
            </Space>
          </Form>
        </Space>
      </section>
    </Space>
  );
}
