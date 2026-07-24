import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Select, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import type { SecurityUserSummary } from "../../api/securityUsers";
import { copyToClipboard } from "./federationShared";

interface FederationTokensTabProps {
  usersQuery: UseQueryResult<SecurityUserSummary[], Error>;
  tokenUser: string;
  setTokenUser: (value: string) => void;
  tokenTtlHours: string;
  setTokenTtlHours: (value: string) => void;
  issuedToken: string | null;
  issuedTokenMeta: string | null;
  tokenCopyFeedback: string | null;
  setTokenCopyFeedback: (value: string | null) => void;
  tokenPanelError: string | null;
  tokenApiMissing: boolean;
  issueTokenMutation: UseMutationResult<unknown, Error, void, unknown>;
}

export default function FederationTokensTab({
  usersQuery,
  tokenUser,
  setTokenUser,
  tokenTtlHours,
  setTokenTtlHours,
  issuedToken,
  issuedTokenMeta,
  tokenCopyFeedback,
  setTokenCopyFeedback,
  tokenPanelError,
  tokenApiMissing,
  issueTokenMutation,
}: FederationTokensTabProps) {
  const { t } = useTranslation(["federation", "common"]);
  const userOptions = usersQuery.data ?? [];

  return (
    <section className="panel-card driver-config-form">
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t("tokens.title")}
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t("tokens.subtitle")}
          </Typography.Paragraph>
        </div>
        <Form layout="vertical">
          <Space size="middle" align="start" wrap>
            <Form.Item label={t("tokens.field.user")} style={{ minWidth: 240, marginBottom: 0 }}>
              <Select
                value={tokenUser}
                onChange={setTokenUser}
                disabled={userOptions.length === 0}
                options={
                  userOptions.length === 0
                    ? [{ value: tokenUser, label: tokenUser }]
                    : userOptions.map((user) => ({
                        value: user.username,
                        label: `${user.username} (${user.roles.join(", ")})`,
                      }))
                }
              />
            </Form.Item>
            <Form.Item label={t("tokens.field.ttlHours")} style={{ marginBottom: 0 }}>
              <Input
                type="number"
                min={1}
                max={168}
                value={tokenTtlHours}
                onChange={(e) => setTokenTtlHours(e.target.value)}
              />
            </Form.Item>
          </Space>
        </Form>
        <Space wrap>
          <Button
            type="primary"
            disabled={issueTokenMutation.isPending || tokenApiMissing}
            loading={issueTokenMutation.isPending}
            onClick={() => issueTokenMutation.mutate()}
          >
            {issueTokenMutation.isPending ? t("tokens.issuing") : t("tokens.issue")}
          </Button>
          {issuedToken && (
            <Button
              size="small"
              onClick={() => {
                copyToClipboard(issuedToken)
                  .then(() => setTokenCopyFeedback(t("tokens.copied")))
                  .catch(() => setTokenCopyFeedback(t("tokens.copyFailed")));
              }}
            >
              {t("tokens.copy")}
            </Button>
          )}
        </Space>
        {issuedTokenMeta && <Typography.Text type="secondary">{issuedTokenMeta}</Typography.Text>}
        {tokenCopyFeedback && <Alert type="success" showIcon message={tokenCopyFeedback} />}
        {tokenPanelError && <Alert type="error" showIcon message={tokenPanelError} />}
        {issuedToken && (
          <pre className="mono federation-code-block">{issuedToken}</pre>
        )}
      </Space>
    </section>
  );
}
