import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Switch } from "antd";
import { createCorrelator } from "../../api";
import type { CreateCorrelatorPayload, CorrelatorPatternType } from "../../types/automation";
import {
  CORRELATOR_ACTION_LABEL_KEYS,
  CORRELATOR_ACTION_TYPES,
  correlatorActionTargetLabel,
  correlatorActionTargetPlaceholder,
} from "../../utils/automation/correlatorAction";
import { ObjectPathField } from "../../ui";

interface CreateCorrelatorDialogProps {
  onClose: () => void;
  onCreated: () => void;
}

const DEFAULT: CreateCorrelatorPayload = {
  name: "",
  objectPath: "root.platform.devices.demo-sensor-01",
  patternType: "COUNT",
  eventName: "thresholdExceeded",
  secondEventName: "",
  windowSeconds: 0,
  minOccurrences: 1,
  cooldownSeconds: 120,
  sequenceGapSeconds: 0,
  actionType: "RUN_WORKFLOW",
  actionTarget: "root.platform.workflows.demo-alarm-handler",
  enabled: true,
};

export default function CreateCorrelatorDialog({ onClose, onCreated }: CreateCorrelatorDialogProps) {
  const { t } = useTranslation(["automation", "common"]);
  const [form, setForm] = useState<CreateCorrelatorPayload>({ ...DEFAULT });
  const isSequence = form.patternType === "SEQUENCE";
  const isEventChain = form.patternType === "EVENT_CHAIN";
  const needsSecondEvent = isSequence || isEventChain;

  const mutation = useMutation({
    mutationFn: () =>
      createCorrelator({
        ...form,
        objectPath: form.objectPath?.trim() || undefined,
        secondEventName: needsSecondEvent ? form.secondEventName?.trim() || undefined : undefined,
      }),
    onSuccess: () => onCreated(),
  });

  return (
    <Modal
      title={t("automation:correlator.newTitle")}
      open
      onCancel={onClose}
      destroyOnHidden
      width={760}
      footer={null}
    >
      <Form
        layout="vertical"
        onFinish={() => {
            mutation.mutate();
        }}
      >
        <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
          <Space size="middle" align="start" wrap>
            <Form.Item label={`${t("common:table.name")} *`} required style={{ minWidth: 240, marginBottom: 0 }}>
              <Input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              required
            />
            </Form.Item>
            <Form.Item label={t("automation:correlator.pattern")} style={{ minWidth: 240, marginBottom: 0 }}>
              <Select
              value={form.patternType ?? "COUNT"}
                onChange={(value: CorrelatorPatternType) =>
                setForm((f) => ({
                  ...f,
                    patternType: value,
                    windowSeconds: value === "SEQUENCE" && f.windowSeconds <= 0 ? 300 : f.windowSeconds,
                }))
              }
                options={[
                  { value: "COUNT", label: t("automation:correlator.patternCount") },
                  { value: "SEQUENCE", label: t("automation:correlator.patternSequence") },
                  { value: "EVENT_CHAIN", label: t("automation:correlator.patternEventChain") },
                ]}
              />
            </Form.Item>
            <Form.Item
              label={isSequence ? t("automation:correlator.eventA") : t("automation:correlator.event")}
              required
              style={{ minWidth: 240, marginBottom: 0 }}
            >
              <Input
              value={form.eventName}
              onChange={(e) => setForm((f) => ({ ...f, eventName: e.target.value }))}
              required
            />
            </Form.Item>
          {needsSecondEvent && (
              <Form.Item
                label={isEventChain ? t("automation:correlator.eventChain") : t("automation:correlator.eventB")}
                required
                style={{ minWidth: 240, marginBottom: 0 }}
              >
                <Input
                value={form.secondEventName ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, secondEventName: e.target.value }))}
                required
                placeholder={isEventChain ? "eventA,eventB,eventC" : "alarmActive"}
              />
              </Form.Item>
          )}
          </Space>
          <ObjectPathField
            className="full"
            label={t("automation:correlator.objectPath")}
            value={form.objectPath ?? ""}
            onChange={(objectPath) => setForm((f) => ({ ...f, objectPath }))}
            placeholder={t("automation:correlator.objectPathPlaceholder")}
          />
          <Space size="middle" align="start" wrap>
            <Form.Item label={t("automation:correlator.windowSec")} style={{ minWidth: 180, marginBottom: 0 }}>
              <InputNumber
                min={isSequence ? 1 : 0}
                value={form.windowSeconds}
                onChange={(value) => setForm((f) => ({ ...f, windowSeconds: Number(value ?? 0) }))}
                style={{ width: "100%" }}
              />
            </Form.Item>
          {!needsSecondEvent && (
              <Form.Item label={t("automation:correlator.minRepetitions")} style={{ minWidth: 180, marginBottom: 0 }}>
                <InputNumber
                min={1}
                value={form.minOccurrences}
                  onChange={(value) => setForm((f) => ({ ...f, minOccurrences: Number(value ?? 1) }))}
                  style={{ width: "100%" }}
              />
              </Form.Item>
          )}
            <Form.Item label={t("automation:correlator.cooldownSec")} style={{ minWidth: 180, marginBottom: 0 }}>
              <InputNumber
                min={0}
                value={form.cooldownSeconds}
                onChange={(value) => setForm((f) => ({ ...f, cooldownSeconds: Number(value ?? 0) }))}
                style={{ width: "100%" }}
              />
            </Form.Item>
          {needsSecondEvent && (
              <Form.Item label={t("automation:correlator.maxGapSec")} style={{ minWidth: 180, marginBottom: 0 }}>
                <InputNumber
                min={0}
                value={form.sequenceGapSeconds ?? 0}
                  onChange={(value) => setForm((f) => ({ ...f, sequenceGapSeconds: Number(value ?? 0) }))}
                  style={{ width: "100%" }}
              />
              </Form.Item>
          )}
          </Space>
          <Form.Item label={t("automation:correlator.action")} style={{ marginBottom: 0 }}>
            <Select
              value={form.actionType}
              onChange={(value: CreateCorrelatorPayload["actionType"]) =>
                setForm((f) => ({ ...f, actionType: value }))
              }
              options={CORRELATOR_ACTION_TYPES.map((type) => ({
                value: type,
                label: t(`automation:${CORRELATOR_ACTION_LABEL_KEYS[type]}`),
              }))}
            />
          </Form.Item>
          <Form.Item label={correlatorActionTargetLabel(form.actionType, t)} required style={{ marginBottom: 0 }}>
            <Input
              value={form.actionTarget}
              onChange={(e) => setForm((f) => ({ ...f, actionTarget: e.target.value }))}
              required
              placeholder={correlatorActionTargetPlaceholder(form.actionType, t)}
            />
          </Form.Item>
          <Form.Item label={t("automation:alertRule.enabled")} style={{ marginBottom: 0 }}>
            <Switch
              checked={form.enabled}
              onChange={(checked) => setForm((f) => ({ ...f, enabled: checked }))}
            />
          </Form.Item>
          {mutation.error && (
            <Alert type="error" showIcon message={(mutation.error as Error).message} />
          )}
          <Space>
            <Button onClick={onClose}>{t("common:action.cancel")}</Button>
            <Button htmlType="submit" type="primary" disabled={mutation.isPending || !form.name} loading={mutation.isPending}>
              {t("common:action.create")}
            </Button>
          </Space>
        </Space>
      </Form>
    </Modal>
  );
}
