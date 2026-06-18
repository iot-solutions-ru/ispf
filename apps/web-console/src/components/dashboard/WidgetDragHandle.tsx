export default function WidgetDragHandle({ visible = true }: { visible?: boolean }) {
  if (!visible) return null;
  return (
    <div className="dash-widget-drag-handle" title="Область перетаскивания" aria-hidden>
      ⋮⋮
    </div>
  );
}
