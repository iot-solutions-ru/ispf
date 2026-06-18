import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { createObject } from "../api";
import type { ObjectType } from "../types";

const OBJECT_TYPES: ObjectType[] = [
  "CUSTOM",
  "DEVICE",
  "MODEL",
  "DASHBOARD",
  "WORKFLOW",
  "ALERT",
  "AGENT",
  "USER",
  "TENANT",
  "DRIVER",
];

interface CreateObjectDialogProps {
  parentPath: string;
  onClose: () => void;
  onCreated: (path: string) => void;
}

export default function CreateObjectDialog({
  parentPath,
  onClose,
  onCreated,
}: CreateObjectDialogProps) {
  const [name, setName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [type, setType] = useState<ObjectType>("CUSTOM");

  const mutation = useMutation({
    mutationFn: () =>
      createObject({
        parentPath,
        name,
        type,
        displayName: displayName || name,
        description,
        templateId:
          type === "DASHBOARD"
            ? "dashboard-v1"
            : type === "WORKFLOW"
              ? "workflow-v1"
              : undefined,
      }),
    onSuccess: (obj) => onCreated(obj.path),
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>Новый объект</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>
        <p className="hint">Родитель: <code>{parentPath}</code></p>
        <form
          className="form-grid"
          onSubmit={(e) => {
            e.preventDefault();
            mutation.mutate();
          }}
        >
          <label>
            Имя (сегмент пути) *
            <input value={name} onChange={(e) => setName(e.target.value)} required pattern="[a-zA-Z0-9_-]+" />
          </label>
          <label>
            Отображаемое имя
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
          </label>
          <label>
            Тип
            <select value={type} onChange={(e) => setType(e.target.value as ObjectType)}>
              {OBJECT_TYPES.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </label>
          <label className="full">
            Описание
            <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
          </label>
          {mutation.error && (
            <p className="hint error full">{(mutation.error as Error).message}</p>
          )}
          <footer className="full form-actions">
            <button type="button" className="btn" onClick={onClose}>Отмена</button>
            <button type="submit" className="btn primary" disabled={mutation.isPending || !name}>
              Создать
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
