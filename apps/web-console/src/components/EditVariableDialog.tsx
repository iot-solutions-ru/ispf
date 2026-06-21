import { useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { setVariable, updateVariableDefinition, updateVariableHistory } from "../api";
import type { DataRecord, VariableDto } from "../types";
import BindingExpressionField from "./BindingExpressionField";
import VariableHistoryFields, {
  type VariableHistoryState,
} from "./VariableHistoryFields";

interface EditVariableDialogProps {
  objectPath: string;
  variable: VariableDto;
  canManageHistory?: boolean;
  canEditDefinition?: boolean;
  onClose: () => void;
  onSaved: () => void;
}

function historyFromVariable(variable: VariableDto): VariableHistoryState {
  return {
    historyEnabled: variable.historyEnabled ?? false,
    historyRetentionDays: variable.historyRetentionDays ?? null,
  };
}

function historyEqual(a: VariableHistoryState, b: VariableHistoryState): boolean {
  return (
    a.historyEnabled === b.historyEnabled &&
    a.historyRetentionDays === b.historyRetentionDays
  );
}

export default function EditVariableDialog({
  objectPath,
  variable,
  canManageHistory = true,
  canEditDefinition = false,
  onClose,
  onSaved,
}: EditVariableDialogProps) {
  const [jsonText, setJsonText] = useState("{}");
  const [parseError, setParseError] = useState<string | null>(null);
  const [history, setHistory] = useState<VariableHistoryState>(() => historyFromVariable(variable));
  const [bindingExpression, setBindingExpression] = useState(variable.bindingExpression ?? "");
  const initialHistory = useMemo(() => historyFromVariable(variable), [variable]);
  const initialBinding = variable.bindingExpression ?? "";

  const canEditValue = variable.writable && !variable.bindingExpression;
  const historyDirty = !historyEqual(history, initialHistory);
  const bindingDirty = bindingExpression.trim() !== initialBinding.trim();

  useEffect(() => {
    if (variable.value) {
      setJsonText(JSON.stringify(variable.value, null, 2));
    } else if (variable.value === null) {
      setJsonText('{"schema":{"name":"value","fields":[]},"rows":[]}');
    }
    setHistory(historyFromVariable(variable));
    setBindingExpression(variable.bindingExpression ?? "");
  }, [variable]);

  const mutation = useMutation({
    mutationFn: async () => {
      if (canManageHistory && historyDirty) {
        await updateVariableHistory(objectPath, variable.name, history);
      }
      if (canEditDefinition && bindingDirty) {
        await updateVariableDefinition(objectPath, variable.name, {
          bindingExpression: bindingExpression.trim() || "",
        });
      }
      if (canEditValue) {
        const parsed = JSON.parse(jsonText) as DataRecord;
        await setVariable(objectPath, variable.name, parsed);
      }
    },
    onSuccess: onSaved,
  });

  function handleSave() {
    const hasChanges =
      (canEditValue) ||
      (canManageHistory && historyDirty) ||
      (canEditDefinition && bindingDirty);
    if (!hasChanges) {
      onClose();
      return;
    }
    try {
      if (canEditValue) {
        JSON.parse(jsonText) as DataRecord;
      }
      setParseError(null);
      mutation.mutate();
    } catch {
      setParseError("Некорректный JSON");
    }
  }

  const title = canEditValue ? `Редактировать: ${variable.name}` : `Настройки: ${variable.name}`;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{title}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>

        {canEditDefinition && (
          <section className="modal-section">
            <h4>Привязка</h4>
            <BindingExpressionField
              value={bindingExpression}
              onChange={setBindingExpression}
            />
            <p className="hint">Пустое значение удаляет привязку. Поддерживаются CEL и counterRate(...).</p>
          </section>
        )}

        {canManageHistory && (
          <section className="modal-section">
            <h4>История</h4>
            <VariableHistoryFields
              idPrefix={`edit-${variable.name}`}
              value={history}
              onChange={setHistory}
            />
          </section>
        )}

        {canEditValue && (
          <section className="modal-section">
            <p className="hint">DataRecord (JSON). Схема и строки значений.</p>
            <textarea
              className="json-editor"
              rows={14}
              value={jsonText}
              onChange={(e) => setJsonText(e.target.value)}
            />
          </section>
        )}

        {!canEditValue && !canManageHistory && !canEditDefinition && (
          <p className="hint">Нет доступных действий для этой переменной.</p>
        )}

        {parseError && <p className="hint error">{parseError}</p>}
        {mutation.error && <p className="hint error">{(mutation.error as Error).message}</p>}
        <footer>
          <button type="button" className="btn" onClick={onClose}>Отмена</button>
          <button
            type="button"
            className="btn primary"
            onClick={handleSave}
            disabled={
              mutation.isPending ||
              (!canEditValue && !historyDirty && !(canEditDefinition && bindingDirty))
            }
          >
            Сохранить
          </button>
        </footer>
      </div>
    </div>
  );
}
