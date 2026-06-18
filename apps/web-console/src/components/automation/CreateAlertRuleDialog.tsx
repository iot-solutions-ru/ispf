import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { createAlertRule, validateExpression } from "../../api";
import type { CreateAlertRulePayload } from "../../types/automation";

interface CreateAlertRuleDialogProps {
  onClose: () => void;
  onCreated: () => void;
}

const DEFAULT: CreateAlertRulePayload = {
  name: "",
  objectPath: "root.platform.devices.demo-sensor-01",
  watchVariable: "temperature",
  conditionExpr: "temperature.value > threshold.value",
  eventName: "thresholdExceeded",
  payloadVariable: "",
  enabled: true,
  edgeTrigger: true,
};

export default function CreateAlertRuleDialog({ onClose, onCreated }: CreateAlertRuleDialogProps) {
  const [form, setForm] = useState<CreateAlertRulePayload>({ ...DEFAULT });
  const [exprError, setExprError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      createAlertRule({
        ...form,
        payloadVariable: form.payloadVariable?.trim() || undefined,
      }),
    onSuccess: () => onCreated(),
  });

  const validateMutation = useMutation({
    mutationFn: () => validateExpression(form.conditionExpr),
    onSuccess: (result) => setExprError(result.valid ? null : result.error),
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>Новое правило алерта</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>
        <form
          className="form-grid"
          onSubmit={(e) => {
            e.preventDefault();
            mutation.mutate();
          }}
        >
          <label>
            Имя *
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              required
            />
          </label>
          <label>
            Событие *
            <input
              value={form.eventName}
              onChange={(e) => setForm((f) => ({ ...f, eventName: e.target.value }))}
              required
            />
          </label>
          <label className="full">
            Путь объекта *
            <input
              value={form.objectPath}
              onChange={(e) => setForm((f) => ({ ...f, objectPath: e.target.value }))}
              required
            />
          </label>
          <label>
            Переменная *
            <input
              value={form.watchVariable}
              onChange={(e) => setForm((f) => ({ ...f, watchVariable: e.target.value }))}
              required
            />
          </label>
          <label>
            Payload variable
            <input
              value={form.payloadVariable ?? ""}
              onChange={(e) => setForm((f) => ({ ...f, payloadVariable: e.target.value }))}
              placeholder="опционально"
            />
          </label>
          <label className="full">
            CEL-условие *
            <textarea
              rows={3}
              value={form.conditionExpr}
              onChange={(e) => {
                setForm((f) => ({ ...f, conditionExpr: e.target.value }));
                setExprError(null);
              }}
              onBlur={() => validateMutation.mutate()}
              required
            />
            {exprError && <span className="hint error">{exprError}</span>}
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
            />
            Включено
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={form.edgeTrigger}
              onChange={(e) => setForm((f) => ({ ...f, edgeTrigger: e.target.checked }))}
            />
            Edge trigger (только при переходе false→true)
          </label>
          {mutation.error && (
            <p className="hint error full">{(mutation.error as Error).message}</p>
          )}
          <footer className="full form-actions">
            <button type="button" className="btn" onClick={onClose}>Отмена</button>
            <button
              type="submit"
              className="btn primary"
              disabled={mutation.isPending || !form.name || !!exprError}
            >
              Создать
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
