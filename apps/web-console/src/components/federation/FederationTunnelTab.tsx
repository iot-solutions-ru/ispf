import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useTranslation } from "react-i18next";
import type {
  InboundRegistration,
  OutboundAgent,
  TunnelSession,
} from "../../api/federation";
import { copyToClipboard } from "./federationShared";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";

interface FederationTunnelTabProps {
  inboundQuery: UseQueryResult<InboundRegistration[], Error>;
  outboundQuery: UseQueryResult<OutboundAgent[], Error>;
  tunnelsQuery: UseQueryResult<TunnelSession[], Error>;
  inboundName: string;
  setInboundName: (value: string) => void;
  inboundPathPrefix: string;
  setInboundPathPrefix: (value: string) => void;
  issuedRegistrationCode: string | null;
  outboundForm: {
    name: string;
    hubBaseUrl: string;
    registrationCode: string;
    pathPrefix: string;
  };
  setOutboundForm: React.Dispatch<
    React.SetStateAction<{
      name: string;
      hubBaseUrl: string;
      registrationCode: string;
      pathPrefix: string;
    }>
  >;
  secretsKeyInput: string;
  setSecretsKeyInput: (value: string) => void;
  secretsKeyError: string | null;
  secretsKeyFeedback: string | null;
  secretsKeyConfigured: boolean;
  secretsKeySource: string;
  secretsKeyUiConfigurable: boolean;
  formError: string | null;
  setSyncFeedback: (value: string | null) => void;
  createInboundMutation: UseMutationResult<unknown, Error, void, unknown>;
  deleteInboundMutation: UseMutationResult<unknown, Error, string, unknown>;
  createOutboundMutation: UseMutationResult<unknown, Error, void, unknown>;
  connectOutboundMutation: UseMutationResult<unknown, Error, string, unknown>;
  deleteOutboundMutation: UseMutationResult<unknown, Error, string, unknown>;
  configureSecretsKeyMutation: UseMutationResult<unknown, Error, void, unknown>;
}

export default function FederationTunnelTab({
  inboundQuery,
  outboundQuery,
  tunnelsQuery,
  inboundName,
  setInboundName,
  inboundPathPrefix,
  setInboundPathPrefix,
  issuedRegistrationCode,
  outboundForm,
  setOutboundForm,
  secretsKeyInput,
  setSecretsKeyInput,
  secretsKeyError,
  secretsKeyFeedback,
  secretsKeyConfigured,
  secretsKeySource,
  secretsKeyUiConfigurable,
  formError,
  setSyncFeedback,
  createInboundMutation,
  deleteInboundMutation,
  createOutboundMutation,
  connectOutboundMutation,
  deleteOutboundMutation,
  configureSecretsKeyMutation,
}: FederationTunnelTabProps) {
  const { t } = useTranslation(["federation", "common"]);
  const { formatDate } = useUserTimeZone();
  const inboundRows = inboundQuery.data ?? [];
  const outboundRows = outboundQuery.data ?? [];
  const tunnelRows = tunnelsQuery.data ?? [];

  const inboundColumns: ColumnsType<InboundRegistration> = [
    {
      title: t("tunnel.column.name"),
      dataIndex: "name",
      key: "name",
      render: (name: string) => <Typography.Text code>{name}</Typography.Text>,
    },
    {
      title: t("tunnel.column.prefix"),
      dataIndex: "pathPrefix",
      key: "pathPrefix",
      render: (pathPrefix: string) => <Typography.Text code>{pathPrefix}</Typography.Text>,
    },
    {
      title: t("tunnel.column.expires"),
      dataIndex: "expiresAt",
      key: "expiresAt",
      render: (expiresAt: string) => formatDate(expiresAt),
    },
    {
      title: t("tunnel.column.status"),
      key: "status",
      render: (_value, reg) => (reg.consumedAt ? t("tunnel.registrationUsed") : t("tunnel.registrationPending")),
    },
    {
      title: "",
      key: "actions",
      render: (_value, reg) => (
        <Button
          size="small"
          danger
          disabled={deleteInboundMutation.isPending}
          onClick={() => deleteInboundMutation.mutate(reg.id)}
        >
          {t("common:action.delete")}
        </Button>
      ),
    },
  ];

  const outboundColumns: ColumnsType<OutboundAgent> = [
    {
      title: t("tunnel.column.name"),
      dataIndex: "name",
      key: "name",
      render: (name: string) => <Typography.Text code>{name}</Typography.Text>,
    },
    {
      title: t("tunnel.column.hub"),
      dataIndex: "hubBaseUrl",
      key: "hubBaseUrl",
    },
    {
      title: t("tunnel.column.status"),
      key: "status",
      render: (_value, agent) => (
        <Space size={4} wrap>
          <span>{agent.tunnelStatus}</span>
          {agent.lastError && <Typography.Text type="secondary">— {agent.lastError}</Typography.Text>}
        </Space>
      ),
    },
    {
      title: t("tunnel.column.peer"),
      dataIndex: "linkedPeerId",
      key: "linkedPeerId",
      render: (linkedPeerId: string | undefined) => linkedPeerId ?? t("common:empty.dash"),
    },
    {
      title: "",
      key: "actions",
      render: (_value, agent) => (
        <Space size={4} wrap>
          <Button
            size="small"
            disabled={connectOutboundMutation.isPending}
            onClick={() => connectOutboundMutation.mutate(agent.id)}
          >
            {t("tunnel.connect")}
          </Button>
          <Button
            size="small"
            danger
            disabled={deleteOutboundMutation.isPending}
            onClick={() => deleteOutboundMutation.mutate(agent.id)}
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

      <section className="panel-card driver-config-form">
        <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {t("tunnel.inboundTitle")}
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t("tunnel.inboundHint")}
            </Typography.Paragraph>
          </div>
          <Form layout="vertical">
            <Space size="middle" align="start" wrap>
              <Form.Item label={`${t("tunnel.field.name")} *`} style={{ minWidth: 240, marginBottom: 0 }}>
                <Input value={inboundName} onChange={(e) => setInboundName(e.target.value)} />
              </Form.Item>
              <Form.Item label={t("tunnel.field.prefix")} style={{ minWidth: 240, marginBottom: 0 }}>
                <Input value={inboundPathPrefix} onChange={(e) => setInboundPathPrefix(e.target.value)} />
              </Form.Item>
            </Space>
          </Form>
          <Button
            type="primary"
            disabled={createInboundMutation.isPending}
            loading={createInboundMutation.isPending}
            onClick={() => createInboundMutation.mutate()}
          >
            {t("tunnel.createRegistrationCode")}
          </Button>
          {issuedRegistrationCode && (
            <Space orientation="vertical" size="small" style={{ width: "100%" }}>
              <Alert type="success" showIcon message={t("tunnel.registrationCodeHint")} />
              <pre className="mono federation-code-block">{issuedRegistrationCode}</pre>
              <Button
                size="small"
                onClick={() =>
                  copyToClipboard(issuedRegistrationCode).then(() =>
                    setSyncFeedback(t("tunnel.codeCopied"))
                  )
                }
              >
                {t("tunnel.copyCode")}
              </Button>
            </Space>
          )}
          <Table<InboundRegistration>
            size="small"
            rowKey="id"
            columns={inboundColumns}
            dataSource={inboundRows}
            loading={inboundQuery.isLoading}
            pagination={false}
          />
        </Space>
      </section>

      <section className="panel-card driver-config-form">
        <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {t("tunnel.outboundTitle")}
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t("tunnel.outboundHint")}
            </Typography.Paragraph>
          </div>

          <section className="panel-card federation-secrets-key">
            <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
              <Typography.Title level={5} style={{ margin: 0 }}>
                {t("tunnel.secretsKeyTitle")}
              </Typography.Title>
              {secretsKeyConfigured ? (
                <Alert
                  type="success"
                  showIcon
                  message={
                    <>
                      {t("tunnel.secretsKeyConfigured")}
                      {secretsKeySource === "YAML" && t("tunnel.secretsKeyYaml")}
                      {secretsKeySource === "DATABASE" && t("tunnel.secretsKeyDatabase")}
                      .
                    </>
                  }
                />
              ) : (
                <Alert type="error" showIcon message={t("tunnel.secretsKeyMissing")} />
              )}
              {secretsKeyFeedback && <Alert type="success" showIcon message={secretsKeyFeedback} />}
              {secretsKeyError && <Alert type="error" showIcon message={secretsKeyError} />}
              {secretsKeyUiConfigurable && !secretsKeyConfigured && (
                <>
                  <Form layout="vertical">
                    <Form.Item label={`${t("tunnel.secretsKeyField")} *`} style={{ maxWidth: 360, marginBottom: 0 }}>
                      <Input.Password
                        autoComplete="new-password"
                        placeholder={t("tunnel.secretsKeyPlaceholder")}
                        value={secretsKeyInput}
                        onChange={(e) => setSecretsKeyInput(e.target.value)}
                      />
                    </Form.Item>
                  </Form>
                  <Typography.Text type="secondary">{t("tunnel.secretsKeySaveHint")}</Typography.Text>
                  <Button
                    type="primary"
                    disabled={configureSecretsKeyMutation.isPending}
                    loading={configureSecretsKeyMutation.isPending}
                    onClick={() => configureSecretsKeyMutation.mutate()}
                  >
                    {t("tunnel.secretsKeySave")}
                  </Button>
                </>
              )}
              {secretsKeyUiConfigurable && secretsKeyConfigured && secretsKeySource === "DATABASE" && (
                <Typography.Text type="secondary">{t("tunnel.secretsKeyChangeHint")}</Typography.Text>
              )}
            </Space>
          </section>

          <Form layout="vertical">
            <Space size="middle" align="start" wrap>
              <Form.Item label={`${t("tunnel.field.name")} *`} style={{ minWidth: 240, marginBottom: 0 }}>
                <Input
                  value={outboundForm.name}
                  onChange={(e) => setOutboundForm((prev) => ({ ...prev, name: e.target.value }))}
                />
              </Form.Item>
              <Form.Item label={`${t("tunnel.field.hubUrl")} *`} style={{ minWidth: 280, marginBottom: 0 }}>
                <Input
                  value={outboundForm.hubBaseUrl}
                  onChange={(e) => setOutboundForm((prev) => ({ ...prev, hubBaseUrl: e.target.value }))}
                />
              </Form.Item>
              <Form.Item label={`${t("tunnel.field.registrationCode")} *`} style={{ minWidth: 280, marginBottom: 0 }}>
                <Input.Password
                  value={outboundForm.registrationCode}
                  onChange={(e) => setOutboundForm((prev) => ({ ...prev, registrationCode: e.target.value }))}
                />
              </Form.Item>
              <Form.Item label={t("tunnel.field.prefix")} style={{ minWidth: 240, marginBottom: 0 }}>
                <Input
                  value={outboundForm.pathPrefix}
                  onChange={(e) => setOutboundForm((prev) => ({ ...prev, pathPrefix: e.target.value }))}
                />
              </Form.Item>
            </Space>
          </Form>
          <Button
            type="primary"
            disabled={createOutboundMutation.isPending || !secretsKeyConfigured}
            loading={createOutboundMutation.isPending}
            onClick={() => createOutboundMutation.mutate()}
          >
            {t("tunnel.addAgent")}
          </Button>
          <Table<OutboundAgent>
            size="small"
            rowKey="id"
            columns={outboundColumns}
            dataSource={outboundRows}
            loading={outboundQuery.isLoading}
            pagination={false}
          />
          {tunnelRows.length > 0 && (
            <Typography.Text type="secondary">
              {t("tunnel.activeSessions", { count: tunnelRows.length })}
            </Typography.Text>
          )}
        </Space>
      </section>
    </Space>
  );
}
