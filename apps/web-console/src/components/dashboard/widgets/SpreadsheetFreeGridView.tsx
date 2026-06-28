import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { useVirtualizer } from "@tanstack/react-virtual";
import type { SpreadsheetWidget } from "../../../types/dashboard";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { rowColToA1, a1ToRowCol } from "../sheet/sheetAddress";
import { applyGridOperation, shiftSelectedCell, type GridOperation } from "../sheet/sheetGridOps";
import {
  IconCopy,
  IconCsv,
  IconDeleteCol,
  IconDeleteRow,
  IconExport,
  IconImport,
  IconInsertColLeft,
  IconInsertColRight,
  IconInsertRowAbove,
  IconInsertRowBelow,
  IconPaste,
  IconRedo,
  IconUndo,
  SheetToolButton,
  SheetToolDivider,
} from "../sheet/SheetToolbarIcons";
import { resolveConditionalStyle } from "../sheet/sheetConditionalFormat";
import { formatSheetCellValue } from "../sheet/sheetFormat";
import { moveCellAddress } from "../sheet/sheetNavigation";
import { useSheetFormulaEngine } from "../sheet/useSheetFormulaEngine";
import type { WorkbookFormulaContext } from "../sheet/sheetFormulaEngine";
import { useSheetBindings } from "../sheet/useSheetBindings";
import { useSheetDataRegion } from "../sheet/useSheetDataRegion";
import { useSpreadsheetPersist } from "../sheet/useSpreadsheetPersist";
import {
  exportXlsxWorkbook,
  exportXlsxWorkbookFromTabs,
  importXlsxWorkbook,
} from "../sheet/sheetXlsx";
import {
  buildMergeHiddenSet,
  findMergeAt,
  mergeRuntimeIntoCells,
} from "../sheet/sheetRuntimeMeta";

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
    sheetMode,
    localContents,
    setLocalContents,
    schedulePersist,
    registerPersistSnapshot,
    localMeta,
    hasPersistedContents,
    isLoading,
    canEdit: widgetEditable,
    persistWarning,
    sheetTabs,
    activeSheetIndex,
    switchSheet,
    replaceWorkbook,
    getWorkbookSnapshot,
    commitWorkbook,
  } = useSpreadsheetPersist(widget, objectPath, refreshIntervalMs);

  const [selectedCell, setSelectedCell] = useState<string | null>(null);
  const [formulaBarDraft, setFormulaBarDraft] = useState("");
  const [formulaBarEditing, setFormulaBarEditing] = useState(false);
  const [inlineCell, setInlineCell] = useState<string | null>(null);
  const [inlineDraft, setInlineDraft] = useState("");
  const [importNotice, setImportNotice] = useState<string | null>(null);
  const xlsxInputRef = useRef<HTMLInputElement>(null);

  const effectiveConfig = useMemo(() => {
    const rows = localMeta?.rows ?? sheetConfig.rows;
    const cols = localMeta?.cols ?? sheetConfig.cols;
    const hasRuntimeFreeGrid = sheetMode === "free" && (Boolean(localMeta) || hasPersistedContents);
    const baseCells = hasRuntimeFreeGrid ? {} : sheetConfig.cells;
    return {
      ...sheetConfig,
      rows,
      cols,
      cells: mergeRuntimeIntoCells(baseCells, localMeta?.cellStyles),
      mergedCells: localMeta?.mergedCells ?? sheetConfig.mergedCells,
    };
  }, [sheetConfig, sheetMode, localMeta, hasPersistedContents]);

  const mergeHidden = useMemo(
    () => buildMergeHiddenSet(effectiveConfig.mergedCells),
    [effectiveConfig.mergedCells]
  );

  const { externalByAddr, ispfContext } = useSheetBindings(
    effectiveConfig,
    objectPath,
    refreshIntervalMs,
    localContents
  );
  const { regionContents } = useSheetDataRegion(sheetConfig, objectPath, refreshIntervalMs);

  const workbookFormulaContext = useMemo((): WorkbookFormulaContext | undefined => {
    if (sheetTabs.length <= 1) {
      return undefined;
    }
    const snapshot = getWorkbookSnapshot();
    const activeTab = snapshot.sheets[activeSheetIndex] ?? snapshot.sheets[0];
    return {
      currentSheetName: activeTab?.name ?? "Sheet1",
      sheets: snapshot.sheets.map((tab, index) => ({
        name: tab.name,
        config:
          index === activeSheetIndex
            ? effectiveConfig
            : {
                rows: tab.rows,
                cols: tab.cols,
                cells: mergeRuntimeIntoCells({}, tab.cellStyles),
                mergedCells: tab.mergedCells,
              },
        contents: index === activeSheetIndex ? localContents : tab.contents,
      })),
    };
  }, [
    sheetTabs.length,
    getWorkbookSnapshot,
    activeSheetIndex,
    effectiveConfig,
    localContents,
  ]);

  const formula = useSheetFormulaEngine({
    config: effectiveConfig,
    mode: "free",
    contents: localContents,
    externalByAddr,
    ispfContext,
    workbook: workbookFormulaContext,
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

  const formulaRef = useRef(formula);
  formulaRef.current = formula;
  const localMetaRef = useRef(localMeta);
  localMetaRef.current = localMeta;
  const inlineCellRef = useRef(inlineCell);
  inlineCellRef.current = inlineCell;
  const inlineDraftRef = useRef(inlineDraft);
  inlineDraftRef.current = inlineDraft;
  const formulaBarEditingRef = useRef(formulaBarEditing);
  formulaBarEditingRef.current = formulaBarEditing;
  const formulaBarDraftRef = useRef(formulaBarDraft);
  formulaBarDraftRef.current = formulaBarDraft;
  const selectedCellRef = useRef(selectedCell);
  selectedCellRef.current = selectedCell;
  const canEditRef = useRef(canEdit);
  canEditRef.current = canEdit;

  useEffect(
    () =>
      registerPersistSnapshot(() => {
        const engine = formulaRef.current;
        if (canEditRef.current) {
          if (inlineCellRef.current) {
            engine.setCellContent(inlineCellRef.current, inlineDraftRef.current);
          } else if (formulaBarEditingRef.current && selectedCellRef.current) {
            engine.setCellContent(selectedCellRef.current, formulaBarDraftRef.current);
          }
        }
        return {
          contents: engine.collectCellContents(),
          meta: localMetaRef.current,
        };
      }),
    [registerPersistSnapshot]
  );

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
        effectiveConfig.rows,
        effectiveConfig.cols,
        direction
      );
      if (next) {
        selectCell(next);
      }
    },
    [selectedCell, effectiveConfig.rows, effectiveConfig.cols, selectCell]
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

  const exportXlsx = useCallback(async () => {
    try {
      const snapshot = getWorkbookSnapshot();
      const blob =
        snapshot.sheets.length > 1
          ? await exportXlsxWorkbookFromTabs({
              workbook: snapshot,
              getSheetCellEditContent: (sheetIndex, addr) => {
                if (sheetIndex === activeSheetIndex) {
                  return formula.getCellEditContent(addr);
                }
                return snapshot.sheets[sheetIndex]?.contents[addr] ?? "";
              },
              getSheetCellValue: (sheetIndex, addr) => {
                if (sheetIndex === activeSheetIndex) {
                  return formula.getCellValue(addr);
                }
                return snapshot.sheets[sheetIndex]?.contents[addr] ?? "";
              },
            })
          : await exportXlsxWorkbook({
              rows: effectiveConfig.rows,
              cols: effectiveConfig.cols,
              getCellEditContent: (addr) => formula.getCellEditContent(addr),
              getCellValue: (addr) => formula.getCellValue(addr),
              sheetName: snapshot.sheets[0]?.name ?? (widget.title || "sheet"),
              meta: localMeta ?? undefined,
            });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `${widget.title || "sheet"}.xlsx`;
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      setImportNotice(t("spreadsheet.importXlsxError"));
    }
  }, [
    activeSheetIndex,
    effectiveConfig.cols,
    effectiveConfig.rows,
    formula,
    getWorkbookSnapshot,
    localMeta,
    t,
    widget.title,
  ]);

  const applyXlsxImport = useCallback(
    async (file: File) => {
      const result = await importXlsxWorkbook(file);
      replaceWorkbook(result.workbook);
      setSelectedCell("A1");
      const sheetCount = result.workbook.sheets.length;
      if (result.warnings.length > 0) {
        setImportNotice(
          t("spreadsheet.importXlsxWorkbookWarnings", {
            count: sheetCount,
            warningCount: result.warnings.length,
            details: result.warnings.slice(0, 5).join("; "),
          })
        );
      } else {
        setImportNotice(
          t("spreadsheet.importXlsxWorkbookSuccess", {
            count: sheetCount,
            name: result.workbook.sheets[0]?.name ?? "",
          })
        );
      }
    },
    [replaceWorkbook, t]
  );

  const onXlsxFileSelected = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      event.target.value = "";
      if (!file || !canEdit) {
        return;
      }
      try {
        await applyXlsxImport(file);
      } catch {
        setImportNotice(t("spreadsheet.importXlsxError"));
      }
    },
    [applyXlsxImport, canEdit, t]
  );

  const handleSwitchSheet = useCallback(
    (index: number) => {
      if (index === activeSheetIndex) {
        return;
      }
      switchSheet(index);
      setSelectedCell("A1");
      setFormulaBarEditing(false);
      setInlineCell(null);
    },
    [activeSheetIndex, switchSheet]
  );

  const applyGridChange = useCallback(
    (operation: GridOperation) => {
      if (!canEdit) {
        return;
      }
      cancelEdit();
      const snapshot = getWorkbookSnapshot();
      const nextWorkbook = applyGridOperation(snapshot, activeSheetIndex, operation);
      if (nextWorkbook === snapshot) {
        return;
      }
      commitWorkbook(nextWorkbook);
      if (selectedCell) {
        const count = operation.count ?? 1;
        const nextSelected = shiftSelectedCell(
          selectedCell,
          operation.axis,
          operation.at,
          count,
          operation.mode
        );
        setSelectedCell(nextSelected);
      }
    },
    [activeSheetIndex, cancelEdit, canEdit, commitWorkbook, getWorkbookSnapshot, selectedCell]
  );

  const runGridOpAtSelection = useCallback(
    (operation: Omit<GridOperation, "at"> & { atOffset?: number }) => {
      const anchor = selectedCell ?? "A1";
      const rc = a1ToRowCol(anchor);
      if (!rc) {
        return;
      }
      const at =
        operation.axis === "row"
          ? rc.row + (operation.atOffset ?? 0)
          : rc.col + (operation.atOffset ?? 0);
      applyGridChange({
        axis: operation.axis,
        mode: operation.mode,
        at,
        count: operation.count,
      });
    },
    [applyGridChange, selectedCell]
  );

  useEffect(() => {
    if (!importNotice) {
      return;
    }
    const timer = window.setTimeout(() => setImportNotice(null), 8000);
    return () => window.clearTimeout(timer);
  }, [importNotice]);

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
    effectiveConfig.colLabels && effectiveConfig.colLabels.length === effectiveConfig.cols
      ? effectiveConfig.colLabels
      : Array.from({ length: effectiveConfig.cols }, (_, i) =>
          rowColToA1(0, i).replace(/\d+$/, "")
        );

  const getDisplayText = (address: string): string => {
    const seed = effectiveConfig.cells[address];
    if (seed?.kind === "label") {
      return seed.text ?? "";
    }
    const raw = formula.getCellValue(address);
    return formatSheetCellValue(raw, seed?.format);
  };

  const visibleRows = useMemo(
    () => Array.from({ length: effectiveConfig.rows }, (_, rowIndex) => rowIndex),
    [effectiveConfig.rows]
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
    if (mergeHidden.has(address)) {
      return null;
    }
    const merge = findMergeAt(effectiveConfig.mergedCells, address);
    const seed = effectiveConfig.cells[address];
    const isBinding = formula.isBindingCell(address);
    const isSelected = selectedCell === address;
    const isInline = inlineCell === address;
    const cellStyle = resolveConditionalStyle(
      effectiveConfig.conditionalStyles,
      (addr) => formula.getCellValue(addr),
      effectiveConfig.cells,
      address
    );
    const mergedStyle = { ...seed?.style, ...cellStyle };
    const spanProps = merge
      ? { rowSpan: merge.rowSpan, colSpan: merge.colSpan }
      : {};

    if (seed?.kind === "label") {
      return (
        <td
          key={address}
          className={`dash-sheet-cell dash-sheet-label${isSelected ? " dash-sheet-selected" : ""}`}
          style={mergedStyle}
          {...spanProps}
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
          {...spanProps}
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
        {...spanProps}
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
      {Array.from({ length: effectiveConfig.cols }, (_, colIndex) => renderCell(rowIndex, colIndex))}
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
            <SheetToolButton
              label={t("spreadsheet.undo")}
              disabled={!canEdit}
              onClick={() => {
                if (formula.undo()) {
                  const next = formula.collectCellContents();
                  setLocalContents(next);
                  schedulePersist(next);
                }
              }}
            >
              <IconUndo />
            </SheetToolButton>
            <SheetToolButton
              label={t("spreadsheet.redo")}
              disabled={!canEdit}
              onClick={() => {
                if (formula.redo()) {
                  const next = formula.collectCellContents();
                  setLocalContents(next);
                  schedulePersist(next);
                }
              }}
            >
              <IconRedo />
            </SheetToolButton>
            <SheetToolDivider />
            <SheetToolButton
              label={t("spreadsheet.copy")}
              disabled={!canEdit || !selectedCell}
              onClick={() => selectedCell && formula.copySelection()}
            >
              <IconCopy />
            </SheetToolButton>
            <SheetToolButton
              label={t("spreadsheet.paste")}
              disabled={!canEdit || !selectedCell}
              onClick={() => {
                if (selectedCell) {
                  formula.pasteAt(selectedCell);
                  const next = formula.collectCellContents();
                  setLocalContents(next);
                  schedulePersist(next);
                }
              }}
            >
              <IconPaste />
            </SheetToolButton>
            <SheetToolDivider />
            <SheetToolButton
              label={t("spreadsheet.insertRowAbove")}
              disabled={!canEdit}
              onClick={() => runGridOpAtSelection({ axis: "row", mode: "insert" })}
            >
              <IconInsertRowAbove />
            </SheetToolButton>
            <SheetToolButton
              label={t("spreadsheet.insertRowBelow")}
              disabled={!canEdit}
              onClick={() => runGridOpAtSelection({ axis: "row", mode: "insert", atOffset: 1 })}
            >
              <IconInsertRowBelow />
            </SheetToolButton>
            <SheetToolButton
              label={t("spreadsheet.deleteRow")}
              disabled={!canEdit || effectiveConfig.rows <= 1}
              onClick={() => runGridOpAtSelection({ axis: "row", mode: "delete" })}
            >
              <IconDeleteRow />
            </SheetToolButton>
            <SheetToolDivider />
            <SheetToolButton
              label={t("spreadsheet.insertColLeft")}
              disabled={!canEdit}
              onClick={() => runGridOpAtSelection({ axis: "col", mode: "insert" })}
            >
              <IconInsertColLeft />
            </SheetToolButton>
            <SheetToolButton
              label={t("spreadsheet.insertColRight")}
              disabled={!canEdit}
              onClick={() => runGridOpAtSelection({ axis: "col", mode: "insert", atOffset: 1 })}
            >
              <IconInsertColRight />
            </SheetToolButton>
            <SheetToolButton
              label={t("spreadsheet.deleteCol")}
              disabled={!canEdit || effectiveConfig.cols <= 1}
              onClick={() => runGridOpAtSelection({ axis: "col", mode: "delete" })}
            >
              <IconDeleteCol />
            </SheetToolButton>
            <SheetToolDivider />
            <SheetToolButton
              label={t("spreadsheet.importXlsx")}
              disabled={!canEdit}
              onClick={() => xlsxInputRef.current?.click()}
            >
              <IconImport />
            </SheetToolButton>
            <SheetToolButton label={t("spreadsheet.exportXlsx")} onClick={exportXlsx}>
              <IconExport />
            </SheetToolButton>
            <SheetToolButton label={t("spreadsheet.exportCsv")} onClick={exportCsv}>
              <IconCsv />
            </SheetToolButton>
            <input
              ref={xlsxInputRef}
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              hidden
              onChange={onXlsxFileSelected}
            />
          </div>
          {sheetTabs.length > 1 ? (
            <div className="dash-sheet-tabs" role="tablist" aria-label={t("spreadsheet.sheetTabs")}>
              {sheetTabs.map((tab) => (
                <button
                  key={`${tab.index}-${tab.name}`}
                  type="button"
                  role="tab"
                  aria-selected={tab.index === activeSheetIndex}
                  className={`dash-sheet-tab${tab.index === activeSheetIndex ? " dash-sheet-tab-active" : ""}`}
                  onClick={() => handleSwitchSheet(tab.index)}
                >
                  {tab.name}
                </button>
              ))}
            </div>
          ) : null}
          {persistWarning ? (
            <div className="dash-sheet-import-notice dash-sheet-persist-warning" role="status">
              {t(persistWarning, {
                name: widget.valuesVariable ?? "",
                objectPath: objectPath || (widget.objectPath ?? ""),
              })}
            </div>
          ) : null}
          {importNotice ? (
            <div className="dash-sheet-import-notice" role="status">
              {importNotice}
            </div>
          ) : null}
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
          <div
            className="dash-table-wrap dash-sheet-wrap"
            ref={tableWrapRef}
            style={
              styles.body
                ? { ...styles.body, flex: "1 1 0", minHeight: 0, overflow: "auto" }
                : undefined
            }
          >
            <table className="dash-object-table dash-sheet-table" style={styles.table}>
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
                      colSpan={effectiveConfig.cols + 1}
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
                      colSpan={effectiveConfig.cols + 1}
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
