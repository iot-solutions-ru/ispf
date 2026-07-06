import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { DashboardWidget } from "../../types/dashboard";
import DashboardGrid from "./DashboardGrid";
import { useDashboardEditor } from "./DashboardEditorContext";
import type { WidgetSlotRef } from "./widgetLayoutTree";
import { getChildrenAtSlot, slotRefKey } from "./widgetLayoutTree";

interface ContainerChildGridProps {
  slotRef: WidgetSlotRef;
  className?: string;
  emptyHint?: string;
}

export default function ContainerChildGrid({
  slotRef,
  className = "",
  emptyHint,
}: ContainerChildGridProps) {
  const { t } = useTranslation("dashboard");
  const editor = useDashboardEditor();
  const children = useMemo(
    () => (editor ? getChildrenAtSlot(editor.layout, slotRef) : []),
    [editor, slotRef]
  );

  if (!editor?.enabled) {
    return null;
  }

  const slotKey = slotRefKey(slotRef);
  const isDropTarget = editor.dropTargetSlotKey === slotKey;

  return (
    <div
      className={`dash-container-drop-zone${isDropTarget ? " dash-container-drop-zone--active" : ""}${className ? ` ${className}` : ""}`}
      data-slot-key={slotKey}
      onMouseEnter={() => editor.setDropTargetSlotKey(slotKey)}
      onMouseLeave={() => {
        if (editor.dropTargetSlotKey === slotKey) {
          editor.setDropTargetSlotKey(null);
        }
      }}
    >
      {children.length === 0 && (
        <p className="hint dash-container-drop-hint">
          {emptyHint ?? t("editor.containerDropHint")}
        </p>
      )}
      <DashboardGrid
        layout={{
          columns: editor.layout.columns,
          rowHeight: editor.layout.rowHeight,
          theme: editor.layout.theme,
          widgets: children,
        }}
        refreshIntervalMs={editor.refreshIntervalMs}
        editable
        nested
        slotRef={slotRef}
        selectedWidgetId={editor.selectedWidgetId}
        onSelectWidget={editor.selectWidget}
        onLayoutChange={(widgets) => editor.setChildrenAtSlot(slotRef, widgets)}
      />
    </div>
  );
}

export function ContainerChildGridOrList({
  slotRef,
  children,
  renderList,
  emptyHint,
  bodyClassName,
}: {
  slotRef: WidgetSlotRef;
  children: DashboardWidget[];
  renderList: () => React.ReactNode;
  emptyHint?: string;
  bodyClassName?: string;
}) {
  const editor = useDashboardEditor();
  if (editor?.enabled) {
    return (
      <ContainerChildGrid slotRef={slotRef} className={bodyClassName} emptyHint={emptyHint} />
    );
  }
  if (children.length === 0) {
    return emptyHint ? <p className="hint">{emptyHint}</p> : null;
  }
  return <>{renderList()}</>;
}
