import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "antd";
import VariableHistoryModal from "../objectEditor/VariableHistoryModal";

interface WidgetHistoryControlsProps {
  objectPath: string;
  variableName: string;
  valueField?: string;
  title: string;
  historyEnabled: boolean;
  historyRangeLabel?: string;
}

export default function WidgetHistoryControls({
  objectPath,
  variableName,
  valueField,
  title,
  historyEnabled,
  historyRangeLabel,
}: WidgetHistoryControlsProps) {
  const { t } = useTranslation("widgets");
  const [showHistory, setShowHistory] = useState(false);

  if (!historyEnabled || !objectPath || !variableName) {
    return historyRangeLabel ? (
      <span className="dash-widget-history-meta">{historyRangeLabel}</span>
    ) : null;
  }

  return (
    <div className="dash-widget-history-controls">
      {historyRangeLabel && (
        <span className="dash-widget-history-meta">{historyRangeLabel}</span>
      )}
      <Button
        size="small"
        className="btn tiny dash-widget-history-btn"
        onClick={() => setShowHistory(true)}
      >
        {t("history.label")}
      </Button>
      {showHistory && (
        <VariableHistoryModal
          objectPath={objectPath}
          variableName={variableName}
          valueField={valueField}
          title={title}
          onClose={() => setShowHistory(false)}
        />
      )}
    </div>
  );
}
