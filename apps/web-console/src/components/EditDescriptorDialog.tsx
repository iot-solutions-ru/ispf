import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { upsertEvent, upsertFunction } from "../api";
import type { DataSchema, EventDescriptor, FunctionDescriptor } from "../types";

type DescriptorKind = "function" | "event";

interface EditDescriptorDialogProps {
  objectPath: string;
  kind: DescriptorKind;
  initial?: FunctionDescriptor | EventDescriptor;
  onClose: () => void;
  onSaved: () => void;
}

const EMPTY_SCHEMA: DataSchema = { name: "empty", fields: [] };

function defaultFunction(name = ""): FunctionDescriptor {
  return {
    name,
    description: "",
    inputSchema: { ...EMPTY_SCHEMA, name: `${name || "fn"}Input` },
    outputSchema: {
      name: `${name || "fn"}Output`,
      fields: [{ name: "ok", type: "BOOLEAN" }],
    },
  };
}

function defaultEvent(name = ""): EventDescriptor {
  return {
    name,
    description: "",
    payloadSchema: {
      name: `${name || "event"}Payload`,
      fields: [{ name: "message", type: "STRING" }],
    },
    level: "INFO",
  };
}

export default function EditDescriptorDialog({
  objectPath,
  kind,
  initial,
  onClose,
  onSaved,
}: EditDescriptorDialogProps) {
  const isFunction = kind === "function";
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [level, setLevel] = useState(
    !isFunction && initial ? (initial as EventDescriptor).level : "INFO"
  );
  const [schemaJson, setSchemaJson] = useState("{}");
  const [parseError, setParseError] = useState<string | null>(null);

  useEffect(() => {
    if (isFunction) {
      const fn = (initial as FunctionDescriptor | undefined) ?? defaultFunction(name);
      setSchemaJson(
        JSON.stringify(
          {
            inputSchema: fn.inputSchema,
            outputSchema: fn.outputSchema,
          },
          null,
          2
        )
      );
    } else {
      const ev = (initial as EventDescriptor | undefined) ?? defaultEvent(name);
      setSchemaJson(JSON.stringify(ev.payloadSchema, null, 2));
    }
  }, [initial, isFunction, name]);

  const mutation = useMutation({
    mutationFn: async () => {
      if (isFunction) {
        const parsed = JSON.parse(schemaJson) as {
          inputSchema: DataSchema;
          outputSchema: DataSchema;
        };
        const fn: FunctionDescriptor = {
          name: name.trim(),
          description: description.trim(),
          inputSchema: parsed.inputSchema,
          outputSchema: parsed.outputSchema,
        };
        return upsertFunction(objectPath, fn);
      }
      const payloadSchema = JSON.parse(schemaJson) as DataSchema;
      const ev: EventDescriptor = {
        name: name.trim(),
        description: description.trim(),
        payloadSchema,
        level,
      };
      return upsertEvent(objectPath, ev);
    },
    onSuccess: onSaved,
  });

  function handleSave() {
    try {
      if (isFunction) {
        JSON.parse(schemaJson);
      } else {
        JSON.parse(schemaJson);
      }
      setParseError(null);
      mutation.mutate();
    } catch {
      setParseError("Некорректный JSON схемы");
    }
  }

  const title = initial
    ? isFunction
      ? `Функция: ${initial.name}`
      : `Событие: ${initial.name}`
    : isFunction
      ? "Новая функция"
      : "Новое событие";

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal wide" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{title}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>

        <section className="modal-section form-grid">
          <label>
            Имя
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              readOnly={Boolean(initial)}
              pattern="[A-Za-z_][A-Za-z0-9_]*"
              required
            />
          </label>
          {!isFunction && (
            <label>
              Уровень
              <select value={level} onChange={(e) => setLevel(e.target.value)}>
                <option value="DEBUG">DEBUG</option>
                <option value="INFO">INFO</option>
                <option value="WARNING">WARNING</option>
                <option value="ERROR">ERROR</option>
                <option value="CRITICAL">CRITICAL</option>
              </select>
            </label>
          )}
          <label className="full">
            Описание
            <input value={description} onChange={(e) => setDescription(e.target.value)} />
          </label>
          <label className="full">
            {isFunction ? "inputSchema / outputSchema (JSON)" : "payloadSchema (JSON)"}
            <textarea
              className="json-editor"
              rows={12}
              value={schemaJson}
              onChange={(e) => setSchemaJson(e.target.value)}
            />
          </label>
        </section>

        {parseError && <p className="hint error">{parseError}</p>}
        {mutation.error && <p className="hint error">{(mutation.error as Error).message}</p>}

        <footer>
          <button type="button" className="btn" onClick={onClose}>Отмена</button>
          <button
            type="button"
            className="btn primary"
            disabled={!name.trim() || mutation.isPending}
            onClick={handleSave}
          >
            Сохранить
          </button>
        </footer>
      </div>
    </div>
  );
}
