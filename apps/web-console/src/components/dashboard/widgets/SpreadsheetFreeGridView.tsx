import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
} from "react";
import { useTranslation } from "react-i18next";
import { useVirtualizer } from "@tanstack/react-virtual";
import type { SpreadsheetWidget } from "../../../types/dashboard";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { rowColToA1 } from "../sheet/sheetAddress";
import { resolveConditionalStyle } from "../sheet/sheetConditionalFormat";
import { formatSheetCellValue } from "../sheet/sheetFormat";
import { moveCellAddress } from "../sheet/sheetNavigation";
import { useSheetFormulaEngine } from "../sheet/useSheetFormulaEngine";
import { useSheetBindings } from "../sheet/useSheetBindings";
import { useSheetDataRegion } from "../sheet/useSheetDataRegion";
import { useSpreadsheetPersist } from "../sheet/useSpreadsheetPersist";

const VIRTUALIZE_ROW_THRESHOLD = 50;
const TABLE_ROW_ESTIMATE_PX = 36;

interface SpreadsheetFreeGridViewProps {
  widget: SpreadsheetWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function SpreadsheetFreeGridView({
  widget,
  refreshIntervalMs,
  editable: editMode,
}: SpreadsheetFreeGridViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const tableWrapRef = useRef<HTMLDivElement>(null);
  const formulaBarRef = useRef<HTMLInputElement>(null);
  const inlineInputRef = useRef<HTMLInputElement>(null);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);

  const {
    sheetConfig,
    localContents,
    setLocalContents,
    schedulePersist,
    isLoading,
    canEdit: widgetEditable,
  } = useSpreadsheetPersist(widget, objectPath, refreshIntervalMs);

  const [selectedCell, setSelectedCell] = useState<string | null>(null);
  const [formulaBarDraft, setFormulaBarDraft] = useState("");
  const [formulaBarEditing, setFormulaBarEditing] = useState(false);
  const [inlineCell, setInlineCell] = useState<string | null>(null);
  const [inlineDraft, setInlineDraft] = useState("");

  const { externalByAddr, ispfContext } = useSheetBindings(
    sheetConfig,
    objectPath,
    refreshIntervalMs
  );
  const { regionContents } = useSheetDataRegion(sheetConfig, objectPath, refreshIntervalMs);

  const formula = useSheetFormulaEngine({
    config: sheetConfig,
    mode: "free",
    contents: localContents,
    externalByAddr,
    ispfContext,
  });

  useEffect(() => {
    if (Object.keys(regionContents).length > 0) {
      formula.mergeRegionContents(regionContents);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- merge when region data changes
  }, [regionContents]);

  useEffect(() => {
    if (selectedCell && !formulaBarEditing) {
      setFormulaBarDraft(formula.getCellEditContent(selectedCell));
    }
  }, [selectedCell, formula.revision, formulaBarEditing, formula]);

  useEffect(() => {
    if (inlineCell) {
      inlineInputRef.current?.focus();
      inlineInputRef.current?.select();
    }
  }, [inlineCell]);

  const canEdit = !editMode && widgetEditable;

  const commitCell = useCallback(
    (address: string, raw: string) => {
      if (!canEdit || formula.isBindingCell(address)) {
        return;
      }
      formula.setCellContent(address, raw);
      const nextContents = formula.collectCellContents();
      setLocalContents(nextContents);
      schedulePersist(nextContents);
    },
    [canEdit, formula, schedulePersist, setLocalContents]
  );

  const selectCell = useCallback(
    (address: string) => {
      setSelectedCell(address);
      formula.setSelectionAnchor(address);
      if (!formulaBarEditing) {
        setFormulaBarDraft(formula.getCellEditContent(address));
      }
    },
    [formula, formulaBarEditing]
  );

  const startInlineEdit = useCallback(
    (address: string) => {
      if (!canEdit || formula.isBindingCell(address)) {
        return;
      }
      selectCell(address);
      setInlineCell(address);
      setInlineDraft(formula.getCellEditContent(address));
      setFormulaBarEditing(false);
    },
    [canEdit, formula, selectCell]
  );

  const cancelEdit = useCallback(() => {
    setFormulaBarEditing(false);
    setInlineCell(null);
    if (selectedCell) {
      setFormulaBarDraft(formula.getCellEditContent(selectedCell));
    }
  }, [formula, selectedCell]);

  const commitFormulaBar = useCallback(() => {
    if (!selectedCell || !canEdit) {
      return;
    }
    commitCell(selectedCell, formulaBarDraft);
    setFormulaBarEditing(false);
    setInlineCell(null);
    setFormulaBarDraft(formula.getCellEditContent(selectedCell));
  }, [selectedCell, canEdit, commitCell, formulaBarDraft, formula]);

  const commitInline = useCallback(() => {
    if (!inlineCell) {
      return;
    }
    commitCell(inlineCell, inlineDraft);
    setInlineCell(null);
    if (selectedCell === inlineCell) {
      setFormulaBarDraft(formula.getCellEditContent(inlineCell));
    }
  }, [inlineCell, inlineDraft, commitCell, selectedCell, formula]);

  const moveSelection = useCallback(
    (direction: Parameters<typeof moveCellAddress>[3]) => {
      if (!selectedCell) {
        selectCell(rowColToA1(0, 0));
        return;
      }
      const next = moveCellAddress(
        selectedCell,
        sheetConfig.rows,
        sheetConfig.cols,
        direction
      );
      if (next) {
        selectCell(next);
      }
    },
    [selectedCell, sheetConfig.rows, sheetConfig.cols, selectCell]
  );

  const exportCsv = useCallback(() => {
    const csv = formula.exportCsv();
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${widget.title || "sheet"}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }, [formula, widget.title]);

  const onContainerKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (inlineCell || formulaBarEditing) {
      return;
    }
    const mod = event.ctrlKey || event.metaKey;
    if (mod && event.key === "z") {
      event.preventDefault();
      if (formula.undo()) {
        const next = formula.collectCellContents();
        setLocalContents(next);
        schedulePersist(next);
      }
      return;
    }
    if (mod && event.key === "y") {
      event.preventDefault();
      if (formula.redo()) {
        const next = formula.collectCellContents();
        setLocalContents(next);
        schedulePersist(next);
      }
      return;
    }
    if (mod && event.key === "c" && selectedCell) {
      event.preventDefault();
      formula.copySelection();
      return;
    }
    if (mod && event.key === "v" && selectedCell) {
      event.preventDefault();
      formula.pasteAt(selectedCell);
      const next = formula.collectCellContents();
      setLocalContents(next);
      schedulePersist(next);
      return;
    }
    if (event.key === "F2" && selectedCell) {
      event.preventDefault();
      startInlineEdit(selectedCell);
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      moveSelection("up");
    } else if (event.key === "ArrowDown") {
      event.preventDefault();
      moveSelection("down");
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      moveSelection("left");
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      moveSelection("right");
    } else if (event.key === "Tab") {
      event.preventDefault();
      moveSelection(event.shiftKey ? "prev" : "next");
    } else if (event.key === "Enter" && selectedCell) {
      event.preventDefault();
      startInlineEdit(selectedCell);
    }
  };

  const colLabels =
    sheetConfig.colLabels ??
    Array.from({ length: sheetConfig.cols }, (_, i) =>
      rowColToA1(0, i).replace(/\d+$/, "")
    );

  const getDisplayText = (address: string): string => {
    const seed = sheetConfig.cells[address];
    if (seed?.kind === "label") {
      return seed.text ?? "";
    }
    const raw = formula.getCellValue(address);
    return formatSheetCellValue(raw, seed?.format);
  };

  const visibleRows = useMemo(
    () => Array.from({ length: sheetConfig.rows }, (_, rowIndex) => rowIndex),
    [sheetConfig.rows]
  );

  const shouldVirtualize = visibleRows.length >= VIRTUALIZE_ROW_THRESHOLD;
  const rowVirtualizer = useVirtualizer({
    count: visibleRows.length,
    getScrollElement: () => tableWrapRef.current,
    estimateSize: () => TABLE_ROW_ESTIMATE_PX,
    overscan: 10,
    enabled: shouldVirtualize,
  });

  const virtualRows = shouldVirtualize ? rowVirtualizer.getVirtualItems() : [];
  const paddingTop = virtualRows.length > 0 ? virtualRows[0].start : 0;
  const paddingBottom =
    virtualRows.length > 0
      ? rowVirtualizer.getTotalSize() - virtualRows[virtualRows.length - 1].end
      : 0;

  const renderCell = (rowIndex: number, colIndex: number) => {
    const address = rowColToA1(rowIndex, colIndex);
    const seed = sheetConfig.cells[address];
    const isBinding = formula.isBindingCell(address);
    const isSelected = selectedCell === address;
    const isInline = inlineCell === address;
    const cellStyle = resolveConditionalStyle(
      sheetConfig.conditionalStyles,
      (addr) => formula.getCellValue(addr),
      sheetConfig.cells,
      address
    );
    const mergedStyle = { ...seed?.style, ...cellStyle };

    if (seed?.kind === "label") {
      return (
        <td
          key={address}
          className={`dash-sheet-cell dash-sheet-label${isSelected ? " dash-sheet-selected" : ""}`}
          style={mergedStyle}
          onClick={() => selectCell(address)}
          onDoubleClick={() => startInlineEdit(address)}
        >
          {seed.text ?? ""}
        </td>
      );
    }

    if (isInline && canEdit) {
      return (
        <td
          key={address}
          className={`dash-sheet-cell dash-sheet-input dash-sheet-selected`}
          style={mergedStyle}
        >
          <input
            ref={inlineInputRef}
            data-cell={address}
            value={inlineDraft}
            onChange={(e) => {
              setInlineDraft(e.target.value);
              setFormulaBarDraft(e.target.value);
            }}
            onBlur={commitInline}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                commitInline();
                moveSelection("down");
              } else if (e.key === "Escape") {
                e.preventDefault();
                cancelEdit();
              } else if (e.key === "Tab") {
                e.preventDefault();
                commitInline();
                moveSelection(e.shiftKey ? "prev" : "next");
              }
            }}
          />
        </td>
      );
    }

    const display = getDisplayText(address);
    const alignRight =
      seed?.format?.type === "number" ||
      (typeof display === "string" && /^-?\d/.test(display.trim()));

    return (
      <td
        key={address}
        className={`dash-sheet-cell${isBinding ? " dash-sheet-binding" : ""}${isSelected ? " dash-sheet-selected" : ""}${alignRight ? " dash-sheet-num" : ""}`}
        style={mergedStyle}
        onClick={() => selectCell(address)}
        onDoubleClick={() => startInlineEdit(address)}
      >
        {display || (isBinding ? "—" : "")}
      </td>
    );
  };

  const renderRow = (rowIndex: number) => (
    <tr key={rowIndex}>
      <th className="dash-sheet-row-header">{rowIndex + 1}</th>
      {Array.from({ length: sheetConfig.cols }, (_, colIndex) => renderCell(rowIndex, colIndex))}
    </tr>
  );

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-spreadsheet dash-widget-spreadsheet-free"
      editable={editMode}
    >
      {isLoading ? (
        <p className="hint">{t("common:action.loading")}</p>
      ) : (
        <div
          className="dash-sheet-free-root"
          tabIndex={0}
          onKeyDown={onContainerKeyDown}
        >
          <div className="dash-sheet-toolbar">
            <button
              type="button"
              className="dash-sheet-tool-btn"
              disabled={!canEdit}
              onClick={() => {
                if (formula.undo()) {
                  const next = formula.collectCellContents();
                  setLocalContents(next);
                  schedulePersist(next);
                }
              }}
              title={t("spreadsheet.undo")}
            >
              {t("spreadsheet.undo")}
            </button>
            <button
              type="button"
              className="dash-sheet-tool-btn"
              disabled={!canEdit}
              onClick={() => {
                if (formula.redo()) {
                  const next = formula.collectCellContents();
                  setLocalContents(next);
                  schedulePersist(next);
                }
              }}
              title={t("spreadsheet.redo")}
            >
              {t("spreadsheet.redo")}
            </button>
            <button
              type="button"
              className="dash-sheet-tool-btn"
              disabled={!canEdit || !selectedCell}
              onClick={() => selectedCell && formula.copySelection()}
              title={t("spreadsheet.copy")}
            >
              {t("spreadsheet.copy")}
            </button>
            <button
              type="button"
              className="dash-sheet-tool-btn"
              disabled={!canEdit || !selectedCell}
              onClick={() => {
                if (selectedCell) {
                  formula.pasteAt(selectedCell);
                  const next = formula.collectCellContents();
                  setLocalContents(next);
                  schedulePersist(next);
                }
              }}
              title={t("spreadsheet.paste")}
            >
              {t("spreadsheet.paste")}
            </button>
            <button
              type="button"
              className="dash-sheet-tool-btn"
              onClick={exportCsv}
              title={t("spreadsheet.exportCsv")}
            >
              {t("spreadsheet.exportCsv")}
            </button>
          </div>
          <div className="dash-sheet-formula-bar">
            <span className="dash-sheet-formula-label">{selectedCell ?? ""}</span>
            <input
              ref={formulaBarRef}
              className="dash-sheet-formula-input"
              readOnly={!canEdit || formula.isBindingCell(selectedCell ?? "")}
              value={formulaBarDraft}
              placeholder={t("spreadsheet.formulaBar")}
              onFocus={() => {
                if (canEdit && selectedCell && !formula.isBindingCell(selectedCell)) {
                  setFormulaBarEditing(true);
                }
              }}
              onChange={(e) => {
                setFormulaBarDraft(e.target.value);
                setFormulaBarEditing(true);
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  commitFormulaBar();
                  moveSelection("down");
                } else if (e.key === "Escape") {
                  e.preventDefault();
                  cancelEdit();
                  formulaBarRef.current?.blur();
                } else if (e.key === "Tab") {
                  e.preventDefault();
                  commitFormulaBar();
                  moveSelection(e.shiftKey ? "prev" : "next");
                }
              }}
              onBlur={() => {
                if (formulaBarEditing) {
                  commitFormulaBar();
                }
              }}
            />
          </div>
          <div className="dash-table-wrap dash-sheet-wrap" ref={tableWrapRef} style={styles.body}>
            <table className="dash-object-table dash-sheet-table">
              <thead>
                <tr>
                  <th className="dash-sheet-corner" />
                  {colLabels.map((col) => (
                    <th key={col} className="dash-sheet-col-header">
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {shouldVirtualize && paddingTop > 0 && (
                  <tr aria-hidden="true" className="dash-table-spacer">
                    <td
                      colSpan={sheetConfig.cols + 1}
                      style={{ height: paddingTop, padding: 0, border: 0 }}
                    />
                  </tr>
                )}
                {shouldVirtualize
                  ? virtualRows.map((virtualRow) => renderRow(visibleRows[virtualRow.index]))
                  : visibleRows.map((rowIndex) => renderRow(rowIndex))}
                {shouldVirtualize && paddingBottom > 0 && (
                  <tr aria-hidden="true" className="dash-table-spacer">
                    <td
                      colSpan={sheetConfig.cols + 1}
                      style={{ height: paddingBottom, padding: 0, border: 0 }}
                    />
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </DashWidgetShell>
  );
}
