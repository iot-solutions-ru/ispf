import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { invokeFunction } from "../../api";
import type { FunctionDescriptor } from "../../types";

interface InvokeFunctionDialogProps {
  objectPath: string;
  fn: FunctionDescriptor;
  onClose: () => void;
  onInvoked: () => void;
}

export default function InvokeFunctionDialog({
  objectPath,
  fn,
  onClose,
  onInvoked,
}: InvokeFunctionDialogProps) {
  const [inputJson, setInputJson] = useState("{\n  \"rows\": [{}]\n}");
  const [resultJson, setResultJson] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => {
      let input: unknown;
      const trimmed = inputJson.trim();
      if (trimmed) {
        try {
          input = JSON.parse(trimmed);
        } catch {
          throw new Error("Некорректный JSON input");
        }
      }
      return invokeFunction(objectPath, fn.name, input);
    },
    onSuccess: (result) => {
      setResultJson(JSON.stringify(result, null, 2));
      setError(null);
      onInvoked();
    },
    onError: (err: Error) => {
      setError(err.message);
      setResultJson(null);
    },
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
        <header className="modal-head">
          <h3>Вызов функции</h3>
          <button type="button" className="btn small" onClick={onClose}>×</button>
        </header>
        <div className="modal-body">
          <p className="hint">
            <code>{objectPath}</code> → <code>{fn.name}</code>
          </p>
          {fn.description && <p className="hint">{fn.description}</p>}
          <label className="full">
            Input (JSON DataRecord)
            <textarea
              rows={6}
              value={inputJson}
              onChange={(e) => setInputJson(e.target.value)}
              spellCheck={false}
            />
          </label>
          {error && <p className="hint error">{error}</p>}
          {resultJson && (
            <label className="full">
              Результат
              <textarea rows={8} readOnly value={resultJson} spellCheck={false} />
            </label>
          )}
        </div>
        <footer className="modal-foot">
          <button type="button" className="btn" onClick={onClose}>Закрыть</button>
          <button
            type="button"
            className="btn primary"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? "Вызов…" : "Вызвать"}
          </button>
        </footer>
      </div>
    </div>
  );
}
