import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { createSecurityRole } from "../api/securityRoles";

interface CreateSecurityRoleDialogProps {
  onClose: () => void;
  onCreated: (objectPath: string) => void;
}

export default function CreateSecurityRoleDialog({
  onClose,
  onCreated,
}: CreateSecurityRoleDialogProps) {
  const [name, setName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");

  const mutation = useMutation({
    mutationFn: () =>
      createSecurityRole({
        name: name.trim(),
        displayName: displayName.trim() || name.trim(),
        description: description.trim(),
      }),
    onSuccess: (created) => {
      onCreated(created.objectPath);
      onClose();
    },
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-create-object" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>Новая роль</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>
        <form
          className="modal-body form-grid"
          onSubmit={(event) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          <label className="full">
            Имя роли
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="supervisor"
              required
              pattern="[a-zA-Z0-9._-]{2,64}"
              autoFocus
            />
          </label>
          <label className="full">
            Отображаемое имя
            <input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="Supervisor"
            />
          </label>
          <label className="full">
            Описание
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              placeholder="Краткое описание назначения роли"
            />
          </label>
          {mutation.error && (
            <p className="hint error full">{String(mutation.error)}</p>
          )}
          <footer className="full form-actions">
            <button type="button" className="btn" onClick={onClose}>Отмена</button>
            <button type="submit" className="btn primary" disabled={mutation.isPending}>
              Создать
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
