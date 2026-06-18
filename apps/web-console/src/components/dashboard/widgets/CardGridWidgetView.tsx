import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchObjects, fetchVariables } from "../../../api";
import type { CardGridWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import WidgetDragHandle from "../WidgetDragHandle";

interface CardGridWidgetViewProps {
  widget: CardGridWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function CardGridWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: CardGridWidgetViewProps) {
  const variables = useMemo(() => {
    try {
      return widget.variablesJson ? (JSON.parse(widget.variablesJson) as string[]) : [];
    } catch {
      return [] as string[];
    }
  }, [widget.variablesJson]);

  const children = useQuery({
    queryKey: ["objects", widget.parentPath],
    queryFn: () => fetchObjects(widget.parentPath),
    enabled: Boolean(widget.parentPath),
    refetchInterval: refreshIntervalMs,
  });

  return (
    <div className="dash-widget dash-widget-card-grid">
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-title">{widget.title}</div>
      {!widget.parentPath ? (
        <p className="hint">Укажите parentPath</p>
      ) : (
        <div className="dash-card-grid">
          {(children.data ?? []).map((obj) => (
            <ObjectCard
              key={obj.path}
              path={obj.path}
              title={obj.displayName}
              variables={variables}
              refreshIntervalMs={refreshIntervalMs}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function ObjectCard({
  path,
  title,
  variables,
  refreshIntervalMs,
}: {
  path: string;
  title: string;
  variables: string[];
  refreshIntervalMs: number;
}) {
  const vars = useQuery({
    queryKey: ["variables", path],
    queryFn: () => fetchVariables(path),
    refetchInterval: refreshIntervalMs,
  });

  return (
    <article className="dash-object-card">
      <h4>{title}</h4>
      <dl>
        {variables.map((name) => {
          const variable = vars.data?.find((v) => v.name === name);
          const raw = readFieldValue(variable?.value?.rows[0], "value");
          return (
            <div key={name} className="dash-card-row">
              <dt>{name}</dt>
              <dd>{raw != null ? String(raw) : "—"}</dd>
            </div>
          );
        })}
      </dl>
    </article>
  );
}
