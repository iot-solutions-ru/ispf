import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import { Button, Form, Input, Select, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import type { FederationPeer } from "../../api/federation";

interface FederationProbeTabProps {
  peersQuery: UseQueryResult<FederationPeer[], Error>;
  probePeerId: string;
  setProbePeerId: (value: string) => void;
  probePath: string;
  setProbePath: (value: string) => void;
  probeResult: string | null;
  probeMutation: UseMutationResult<unknown, Error, void, unknown>;
}

export default function FederationProbeTab({
  peersQuery,
  probePeerId,
  setProbePeerId,
  probePath,
  setProbePath,
  probeResult,
  probeMutation,
}: FederationProbeTabProps) {
  const { t } = useTranslation("federation");

  return (
    <section className="panel-card federation-probe">
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t("probe.title")}
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t("probe.subtitle")}
          </Typography.Paragraph>
        </div>
        <Form layout="vertical">
          <Space size="middle" align="start" wrap>
            <Form.Item label={t("probe.field.peer")} style={{ minWidth: 240, marginBottom: 0 }}>
              <Select
                value={probePeerId}
                onChange={setProbePeerId}
                options={[
                  { value: "", label: t("bind.selectPeer") },
                  ...(peersQuery.data ?? []).map((peer) => ({ value: peer.id, label: peer.name })),
                ]}
              />
            </Form.Item>
            <Form.Item label={t("probe.field.path")} style={{ minWidth: 280, marginBottom: 0 }}>
              <Input value={probePath} onChange={(e) => setProbePath(e.target.value)} />
            </Form.Item>
          </Space>
        </Form>
        <Button
          type="primary"
          disabled={probeMutation.isPending || !probePeerId}
          loading={probeMutation.isPending}
          onClick={() => probeMutation.mutate()}
        >
          {probeMutation.isPending ? t("probe.running") : t("probe.run")}
        </Button>
        {probeResult && (
          <pre className="mono federation-probe-result">{probeResult}</pre>
        )}
      </Space>
    </section>
  );
}
