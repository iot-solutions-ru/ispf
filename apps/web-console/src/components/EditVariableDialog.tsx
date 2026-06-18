import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { setVariable } from "../api";
import type { DataRecord, VariableDto } from "../types";

interface EditVariableDialogProps {
  objectPath: string;
  variable: VariableDto;
  onClose: () => void;
  onSaved: () => void;
}

export default function EditVariableDialog({
  objectPath,
  variable,
  onClose,
  onSaved,
}: EditVariableDialogProps) {
  const [jsonText, setJsonText] = useState("{}");
  const [parseError, setParseError] = useState<string | null>(null);

  useEffect(() => {
    if (variable.value) {
      setJsonText(JSON.stringify(variable.value, null, 2));
    } else if (variable.value === null) {
      setJsonText('{"schema":{"name":"value","fields":[]},"rows":[]}');
    }
  }, [variable]);

  const mutation = useMutation({
    mutationFn: (record: DataRecord) => setVariable(objectPath, variable.name, record),
    onSuccess: onSaved,
  });

  function handleSave() {
    try {
      const parsed = JSON.parse(jsonText) as DataRecord;
      setParseError(null);
      mutation.mutate(parsed);
    } catch {
      setParseError("Некорректный JSON");
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>Редактировать: {variable.name}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>
        <p className="hint">DataRecord (JSON). Схема и строки значений.</p>
        <textarea
          className="json-editor"
          rows={14}
          value={jsonText}
          onChange={(e) => setJsonText(e.target.value)}
        />
        {parseError && <p className="hint error">{parseError}</p>}
        {mutation.error && <p className="hint error">{(mutation.error as Error).message}</p>}
        <footer>
          <button type="button" className="btn" onClick={onClose}>Отмена</button>
          <button type="button" className="btn primary" onClick={handleSave} disabled={mutation.isPending}>
            Сохранить
          </button>
        </footer>
      </div>
    </div>
  );
}
