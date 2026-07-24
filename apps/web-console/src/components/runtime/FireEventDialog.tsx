import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Form, Input, Modal, Space, Typography } from "antd";
import { fireEvent } from "../../api";
import type { EventDescriptor } from "../../types";
import type { DataRecord, DataSchema } from "../../types";

interface FireEventDialogProps {
  objectPath: string;
  event: EventDescriptor;
  onClose: () => void;
  onFired: () => void;
}

function defaultPayloadJson(schema: DataSchema): string {
  return JSON.stringify({ schema, rows: [] }, null, 2);
}

function isEmptyPayload(payload: DataRecord): boolean {
  if (!payload.rows || payload.rows.length === 0) {
    return true;
  }
  return payload.rows.every((row) => Object.keys(row).length === 0);
}

export default function FireEventDialog({ objectPath, event, onClose, onFired }: FireEventDialogProps) {
  const { t } = useTranslation(["runtime", "common"]);
  const [payloadJson, setPayloadJson] = useState(() => defaultPayloadJson(event.payloadSchema));
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => {
      let payload: DataRecord | undefined;
      const trimmed = payloadJson.trim();
      if (trimmed) {
        try {
          payload = JSON.parse(trimmed) as DataRecord;
        } catch {
          throw new Error(t("common:error.invalidJsonPayload"));
        }
        if (isEmptyPayload(payload)) {
          payload = undefined;
        }
      }
      return fireEvent(objectPath, event.name, payload);
    },
    onSuccess: () => {
      onFired();
      onClose();
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <Modal
      title={t("runtime:fireEvent.title")}
      open
      onCancel={onClose}
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={onClose}>
          {t("common:action.cancel")}
        </Button>,
        <Button
          key="publish"
          type="primary"
          disabled={mutation.isPending}
          loading={mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? t("runtime:fireEvent.publishing") : t("runtime:fireEvent.publish")}
        </Button>,
      ]}
    >
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {t("runtime:fireEvent.object")} <Typography.Text code>{objectPath}</Typography.Text>
        </Typography.Paragraph>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {t("runtime:fireEvent.event")} <Typography.Text code>{event.name}</Typography.Text> ({event.level})
        </Typography.Paragraph>
        <Form layout="vertical">
          <Form.Item label={t("runtime:fireEvent.payload")} style={{ marginBottom: 0 }}>
            <Input.TextArea
              rows={8}
              value={payloadJson}
              onChange={(e) => setPayloadJson(e.target.value)}
              spellCheck={false}
            />
          </Form.Item>
        </Form>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {t("runtime:fireEvent.payloadHint")}
        </Typography.Paragraph>
        {error && <Alert type="error" showIcon message={error} />}
      </Space>
    </Modal>
  );
}
