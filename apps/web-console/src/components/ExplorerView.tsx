import ObjectInspector from "./ObjectInspector";
import SecurityUsersPanel from "./SecurityUsersPanel";
import { isModelsPath } from "../types/models";

interface ExplorerViewProps {
  selectedPath: string | null;
  onOpenEditor: (path: string) => void;
  onDeleted: () => void;
  isAdmin: boolean;
}

export default function ExplorerView({
  selectedPath,
  onOpenEditor,
  onDeleted,
  isAdmin,
}: ExplorerViewProps) {
  if (!selectedPath) {
    return <div className="inspector-empty">Выберите объект в дереве</div>;
  }

  return (
    <div className="explorer-view">
      <div className="explorer-toolbar">
        <button type="button" className="btn primary" onClick={() => onOpenEditor(selectedPath)}>
          Открыть в редакторе
        </button>
        <span className="hint">
          {isModelsPath(selectedPath)
            ? "Полное определение модели — в редакторе (кнопка выше или двойной щелчок)"
            : "Двойной щелчок по узлу также открывает редактор"}
        </span>
      </div>
      <ObjectInspector path={selectedPath} onDeleted={onDeleted} />
      {selectedPath === "root.platform.security.users" && (
        <SecurityUsersPanel canManage={isAdmin} />
      )}
    </div>
  );
}
