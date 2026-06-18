import ObjectInspector from "./ObjectInspector";

interface ExplorerViewProps {
  selectedPath: string | null;
  onOpenEditor: (path: string) => void;
  onDeleted: () => void;
}

export default function ExplorerView({
  selectedPath,
  onOpenEditor,
  onDeleted,
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
        <span className="hint">Двойной щелчок по узлу также открывает редактор</span>
      </div>
      <ObjectInspector path={selectedPath} onDeleted={onDeleted} />
    </div>
  );
}
