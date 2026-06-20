interface BffDataTableProps {
  rows: Array<Record<string, unknown>>;
  labels?: Record<string, string>;
  emptyMessage?: string;
  selectable?: boolean;
  selectionKey?: string;
  selectedKey?: string | null;
  onSelect?: (row: Record<string, unknown> | null) => void;
}

export default function BffDataTable({
  rows,
  labels,
  emptyMessage,
  selectable = false,
  selectionKey = "order_id",
  selectedKey = null,
  onSelect,
}: BffDataTableProps) {
  if (rows.length === 0) {
    return <p className="op-muted">{emptyMessage ?? "Нет данных."}</p>;
  }

  const columns = Object.keys(rows[0]);

  return (
    <div className="op-table-wrap">
      <table className="op-table">
        <thead>
          <tr>
            {selectable && <th className="op-col-select"> </th>}
            {columns.map((column) => (
              <th key={column}>{labels?.[column] ?? column}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => {
            const rowKey = String(row[selectionKey] ?? index);
            const selected = selectable && selectedKey === rowKey;
            return (
              <tr
                key={rowKey}
                className={selected ? "op-row-selected" : selectable ? "op-row-clickable" : undefined}
                onClick={
                  selectable
                    ? () => {
                        onSelect?.(selected ? null : row);
                      }
                    : undefined
                }
              >
                {selectable && (
                  <td className="op-col-select">
                    <input type="radio" checked={selected} readOnly aria-label={`Выбрать ${rowKey}`} />
                  </td>
                )}
                {columns.map((column) => (
                  <td key={column}>{formatCell(row[column])}</td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function formatCell(value: unknown): string {
  if (value === null || value === undefined) {
    return "—";
  }
  if (typeof value === "boolean") {
    return value ? "да" : "нет";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}
