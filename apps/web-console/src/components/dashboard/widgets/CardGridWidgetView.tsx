import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchObjects } from "../../../api";
import type { CardGridWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import type { VariableDto } from "../../../types";
import { useVariablesBatchQuery } from "../../../hooks/useVariablesQuery";
import { parseJsonObject } from "../dashboardUtils";
import { triggerDashboardOpen, useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

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
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const { setSelection, navigateToDashboard, openDashboardModal } = useDashboardContext();
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

  const handleCardClick = (path: string) => {
    if (editable) {
      return;
    }
    const openOptions = {
      selection: widget.cardSelectionKey
        ? { [widget.cardSelectionKey]: path }
        : undefined,
      params: parseJsonObject(widget.cardParamsJson),
    };
    if (widget.cardSelectionKey) {
      setSelection(widget.cardSelectionKey, path);
    }
    triggerDashboardOpen(widget.cardOpenMode, widget.cardTargetDashboard, widget.title, {
      navigateToDashboard,
      openDashboardModal,
    }, openOptions);
  };

  const cardObjects = children.data ?? [];
  const cardPaths = useMemo(() => cardObjects.map((obj) => obj.path), [cardObjects]);
  const variablesBatch = useVariablesBatchQuery(
    cardPaths,
    refreshIntervalMs,
    Boolean(widget.parentPath)
  );

  const navigable = Boolean(widget.cardTargetDashboard?.trim()) && !editable;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-card-grid"
      editable={editable}
    >
      {!widget.parentPath ? (
        <p className="hint">{t("view.specifyParentPath")}</p>
      ) : (
        <div className="dash-card-grid" style={styles.body}>
          {cardObjects.map((obj) => (
            <ObjectCard
              key={obj.path}
              title={obj.displayName}
              variables={variables}
              objectVariables={variablesBatch.data?.[obj.path]}
              navigable={navigable}
              onOpen={() => handleCardClick(obj.path)}
            />
          ))}
        </div>
      )}
    </DashWidgetShell>
  );
}

function ObjectCard({
  title,
  variables,
  objectVariables,
  navigable,
  onOpen,
}: {
  title: string;
  variables: string[];
  objectVariables?: VariableDto[];
  navigable: boolean;
  onOpen: () => void;
}) {
  return (
    <article
      className={`dash-object-card ${navigable ? "clickable" : ""}`}
      onClick={navigable ? onOpen : undefined}
      onKeyDown={
        navigable
          ? (event) => {
              if (event.key === "Enter") {
                onOpen();
              }
            }
          : undefined
      }
      role={navigable ? "button" : undefined}
      tabIndex={navigable ? 0 : undefined}
    >
      <h4>{title}</h4>
      <dl>
        {variables.map((name) => {
          const variable = objectVariables?.find((v) => v.name === name);
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
