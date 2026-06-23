import {
  isDataSourcePath,
  isMigrationPath,
  isSqlBindingPath,
} from "../../utils/platformSqlPath";

interface PlatformSqlObjectPanelProps {
  path: string;
  onOpenEditor: (path: string) => void;
}

function titleForPath(path: string): string {
  if (isDataSourcePath(path)) {
    return "Источник данных";
  }
  if (isMigrationPath(path)) {
    return "Миграция";
  }
  if (isSqlBindingPath(path)) {
    return "SQL-привязка";
  }
  return "SQL-объект";
}

function descriptionForPath(path: string): string {
  if (isDataSourcePath(path)) {
    return "Ссылка на PostgreSQL-схему для отчётов, миграций и bindings.";
  }
  if (isMigrationPath(path)) {
    return "DDL/DML скрипт, применяемый в схему data source.";
  }
  if (isSqlBindingPath(path)) {
    return "Синхронизация результата SELECT с переменной объекта.";
  }
  return "";
}

export default function PlatformSqlObjectPanel({ path, onOpenEditor }: PlatformSqlObjectPanelProps) {
  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>{titleForPath(path)}</h3>
          <p className="op-muted">{descriptionForPath(path)}</p>
          <p className="hint mono small">{path}</p>
        </div>
        <button type="button" className="btn primary" onClick={() => onOpenEditor(path)}>
          Открыть в редакторе
        </button>
      </header>
      <p className="op-muted">
        Редактор откроется во вкладке workspace. Двойной щелчок по узлу в дереве — тот же эффект.
      </p>
    </section>
  );
}
