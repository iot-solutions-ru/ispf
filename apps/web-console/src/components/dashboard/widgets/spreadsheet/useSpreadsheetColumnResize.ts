import { useCallback, useRef, useState, type PointerEvent } from "react";

/** Default column width in px (BL-150). */
export const SPREADSHEET_DEFAULT_COL_WIDTH = 96;
const MIN_COL_WIDTH = 48;
const MAX_COL_WIDTH = 480;

export function useSpreadsheetColumnResize(_colCount: number) {
  const [widths, setWidths] = useState<Record<number, number>>({});
  const dragRef = useRef<{ colIndex: number; startX: number; startWidth: number } | null>(null);

  const getColWidth = useCallback(
    (colIndex: number) => widths[colIndex] ?? SPREADSHEET_DEFAULT_COL_WIDTH,
    [widths]
  );

  const onResizePointerDown = useCallback(
    (colIndex: number, event: PointerEvent<HTMLSpanElement>) => {
      event.preventDefault();
      event.stopPropagation();
      const startWidth = widths[colIndex] ?? SPREADSHEET_DEFAULT_COL_WIDTH;
      dragRef.current = { colIndex, startX: event.clientX, startWidth };
      event.currentTarget.setPointerCapture(event.pointerId);
    },
    [widths]
  );

  const onResizePointerMove = useCallback((event: PointerEvent<HTMLSpanElement>) => {
    const drag = dragRef.current;
    if (!drag) {
      return;
    }
    const delta = event.clientX - drag.startX;
    const next = Math.max(MIN_COL_WIDTH, Math.min(MAX_COL_WIDTH, drag.startWidth + delta));
    setWidths((prev) => ({ ...prev, [drag.colIndex]: next }));
  }, []);

  const onResizePointerUp = useCallback((event: PointerEvent<HTMLSpanElement>) => {
    if (dragRef.current) {
      dragRef.current = null;
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  }, []);

  return {
    getColWidth,
    resizeHandleProps: (colIndex: number) => ({
      className: "dash-sheet-col-resize-handle",
      role: "separator" as const,
      "aria-orientation": "vertical" as const,
      "aria-label": `Resize column ${colIndex + 1}`,
      onPointerDown: (event: PointerEvent<HTMLSpanElement>) =>
        onResizePointerDown(colIndex, event),
      onPointerMove: onResizePointerMove,
      onPointerUp: onResizePointerUp,
      onPointerCancel: onResizePointerUp,
    }),
  };
}
