import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Select, Space } from "antd";
import {
  deleteApplicationGitBinding,
  fetchApplicationGitBinding,
  fetchApplicationGitStatus,
  upsertApplicationGitBinding,
} from "../../api/applications";

interface ApplicationGitPanelProps {
  appId: string;
  canManage: boolean;
}

export default function ApplicationGitPanel({ appId, canManage }: ApplicationGitPanelProps) {
  const { t } = useTranslation("platform");
  const queryClient = useQueryClient();
  const [form] = Form.useForm();

  const bindingQuery = useQuery({
    queryKey: ["application-git-binding", appId],
    queryFn: () => fetchApplicationGitBinding(appId),
  });
  const statusQuery = useQuery({
    queryKey: ["application-git-status", appId],
    queryFn: () => fetchApplicationGitStatus(appId),
  });

  useEffect(() => {
    const binding = bindingQuery.data;
    if (!binding || binding.configured === false) {
      form.setFieldsValue({
        provider: "github",
        remoteUrl: "",
        defaultBranch: "main",
        bundlePath: "bundle.json",
        token: "",
      });
      return;
    }
    form.setFieldsValue({
      provider: binding.provider ?? "github",
      remoteUrl: binding.remoteUrl ?? "",
      defaultBranch: binding.defaultBranch ?? "main",
      bundlePath: binding.bundlePath ?? "bundle.json",
      token: "",
    });
  }, [bindingQuery.data, form]);

  const saveMutation = useMutation({
    mutationFn: (values: {
      provider: string;
      remoteUrl: string;
      defaultBranch?: string;
      bundlePath?: string;
      token?: string;
    }) => upsertApplicationGitBinding(appId, values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["application-git-binding", appId] });
      queryClient.invalidateQueries({ queryKey: ["application-git-status", appId] });
    },
  });

  const disconnectMutation = useMutation({
    mutationFn: () => deleteApplicationGitBinding(appId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["application-git-binding", appId] });
      queryClient.invalidateQueries({ queryKey: ["application-git-status", appId] });
    },
  });

  const status = statusQuery.data;
  const configured = Boolean(status?.configured ?? bindingQuery.data?.configured);

  return (
    <div className="application-git-panel">
      <header className="application-bundle-header">
        <h3>{t("git.title")}</h3>
        <p className="op-muted">{t("git.subtitle")}</p>
      </header>

      {(bindingQuery.isLoading || statusQuery.isLoading) && (
        <p className="op-muted">{t("git.loading")}</p>
      )}
      {bindingQuery.error && <Alert type="error" showIcon title={String(bindingQuery.error)} />}
      {statusQuery.error && <Alert type="error" showIcon title={String(statusQuery.error)} />}

      {status && (
        <Alert
          type="info"
          showIcon
          className="application-git-status"
          title={configured ? t("git.statusConnected") : t("git.statusNotConnected")}
          description={
            <ul className="application-git-status-list">
              <li>
                {t("git.statusDeploy", {
                  version: status.deployedVersion ?? t("git.none"),
                })}
              </li>
              <li>
                {t("git.statusCredential", {
                  value: status.credentialConfigured ? t("git.yes") : t("git.no"),
                })}
              </li>
            </ul>
          }
        />
      )}

      <Form
        form={form}
        layout="vertical"
        disabled={!canManage}
        onFinish={(values) => saveMutation.mutate(values)}
      >
        <Form.Item
          name="provider"
          label={t("git.provider")}
          rules={[{ required: true, message: t("git.providerRequired") }]}
        >
          <Select
            options={[
              { value: "github", label: "GitHub" },
              { value: "gitlab", label: "GitLab" },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="remoteUrl"
          label={t("git.remoteUrl")}
          rules={[{ required: true, message: t("git.remoteUrlRequired") }]}
        >
          <Input placeholder="https://github.com/org/solution.git" />
        </Form.Item>
        <Form.Item name="defaultBranch" label={t("git.defaultBranch")}>
          <Input placeholder="main" />
        </Form.Item>
        <Form.Item name="bundlePath" label={t("git.bundlePath")}>
          <Input placeholder="bundle.json" />
        </Form.Item>
        <Form.Item
          name="token"
          label={t("git.token")}
          extra={configured ? t("git.tokenExtraUpdate") : t("git.tokenExtraFirst")}
          rules={configured ? [] : [{ required: true, message: t("git.tokenRequired") }]}
        >
          <Input.Password autoComplete="new-password" placeholder={t("git.tokenPlaceholder")} />
        </Form.Item>
        <Space wrap>
          <Button type="primary" htmlType="submit" loading={saveMutation.isPending} disabled={!canManage}>
            {configured ? t("git.save") : t("git.connect")}
          </Button>
          {configured && (
            <Button
              danger
              disabled={!canManage}
              loading={disconnectMutation.isPending}
              onClick={() => {
                if (window.confirm(t("git.disconnectConfirm"))) {
                  disconnectMutation.mutate();
                }
              }}
            >
              {t("git.disconnect")}
            </Button>
          )}
        </Space>
      </Form>

      {saveMutation.error && <Alert type="error" showIcon title={String(saveMutation.error)} />}
      {saveMutation.isSuccess && <Alert type="success" showIcon title={t("git.saveSuccess")} />}
      {disconnectMutation.error && (
        <Alert type="error" showIcon title={String(disconnectMutation.error)} />
      )}

      <p className="op-muted application-git-roadmap">{t("git.roadmap")}</p>
    </div>
  );
}
