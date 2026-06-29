import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import type { MimicConnection, MimicCustomSymbol, MimicElement, ScadaMimicDocument } from "../../types/scadaMimic";
import { DEFAULT_CUSTOM_SVG_INNER, parseSvgUpload } from "../../scada/customSvg";
import {
  createMimicId,
  DEFAULT_LAYER_ID,
  mimicDocumentToJson,
  parseMimicDocument,
  snapCanvasCoordinate,
} from "../../scada/document";
import { findNearestPort, findPortOnElement, getElementPortPosition, rerouteConnectionsForElement, routeOrthogonal } from "../../scada/connectionRouting";
import { collectBindingPaths, resolveDocumentBindings } from "../../scada/bindingResolver";
import { resolvePlacementSymbol } from "../../scada/symbols/registry";
import { useVariablesBatchQuery } from "../../hooks/useVariablesQuery";
import { useDashboardContext } from "../dashboard/DashboardContext";
import ScadaMimicCanvas from "./ScadaMimicCanvas";
import SymbolPalette from "./SymbolPalette";
import MimicPropertiesPanel from "./MimicPropertiesPanel";
import {
  IconConnect,
  IconPlace,
  IconRedo,
  IconSelect,
  IconTrash,
  IconUndo,
} from "./ScadaEditorIcons";

export type ScadaEditorTool = "select" | "place" | "connect";

const CANVAS_MIN = 100;
const CANVAS_MAX = 10000;

function clampCanvasDimension(value: number, fallback: number): number {
  if (!Number.isFinite(value) || value <= 0) return fallback;
  return Math.max(CANVAS_MIN, Math.min(CANVAS_MAX, Math.round(value)));
}

interface ScadaMimicEditorProps {
  diagramJson: string;
  onSave: (diagramJson: string) => void;
  onClose: () => void;
}

export default function ScadaMimicEditor({ diagramJson, onSave, onClose }: ScadaMimicEditorProps) {
  const { t } = useTranslation("scada");
  const session = useDashboardContext();
  const [document, setDocument] = useState<ScadaMimicDocument>(() => parseMimicDocument(diagramJson));
  const [tool, setTool] = useState<ScadaEditorTool>("select");
  const [placeSymbolId, setPlaceSymbolId] = useState<string | null>("tank.vertical");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [selectedConnectionId, setSelectedConnectionId] = useState<string | null>(null);
  const [connectFrom, setConnectFrom] = useState<{ elementId: string; port: string } | null>(null);
  const [history, setHistory] = useState<ScadaMimicDocument[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const [importText, setImportText] = useState("");
  const documentRef = useRef(document);
  documentRef.current = document;

  useEffect(() => {
    if (tool !== "connect") {
      setConnectFrom(null);
    }
  }, [tool]);

  const pushHistory = useCallback((next: ScadaMimicDocument) => {
    setHistory((h) => [...h.slice(0, historyIndex + 1), next]);
    setHistoryIndex((i) => i + 1);
  }, [historyIndex]);

  const updateDocument = useCallback((updater: (doc: ScadaMimicDocument) => ScadaMimicDocument, recordHistory = true) => {
    setDocument((current) => {
      const next = updater(current);
      if (recordHistory) pushHistory(next);
      return next;
    });
  }, [pushHistory]);

  const selectedElement = useMemo(
    () => document.elements.find((el) => selectedIds.has(el.id)) ?? null,
    [document.elements, selectedIds]
  );
  const selectedConnection = useMemo(
    () => document.connections.find((c) => c.id === selectedConnectionId) ?? null,
    [document.connections, selectedConnectionId]
  );

  const bindingPaths = useMemo(
    () => collectBindingPaths(document.elements, document.connections, session),
    [document, session]
  );
  const variablesBatch = useVariablesBatchQuery(bindingPaths, 3000, bindingPaths.length > 0);
  const resolvedBindings = useMemo(
    () => resolveDocumentBindings(document.elements, document.connections, session, variablesBatch.data ?? {}),
    [document, session, variablesBatch.data]
  );

  useEffect(() => {
    setDocument(parseMimicDocument(diagramJson));
    setHistory([]);
    setHistoryIndex(-1);
    setSelectedIds(new Set());
    setSelectedConnectionId(null);
    setConnectFrom(null);
  }, [diagramJson]);

  const applyConnectHit = useCallback(
    (hit: { element: MimicElement; port: { id: string }; x: number; y: number } | null) => {
      if (!hit) {
        setConnectFrom(null);
        return;
      }
      if (!connectFrom) {
        setConnectFrom({ elementId: hit.element.id, port: hit.port.id });
        return;
      }
      if (connectFrom.elementId === hit.element.id) {
        setConnectFrom(null);
        return;
      }
      const fromEl = documentRef.current.elements.find((e) => e.id === connectFrom.elementId);
      if (!fromEl) return;
      const fromPos = getElementPortPosition(fromEl, connectFrom.port, documentRef.current.customSymbols);
      if (!fromPos) return;
      const points = routeOrthogonal(fromPos.x, fromPos.y, hit.x, hit.y);
      const conn: MimicConnection = {
        id: createMimicId("conn"),
        layerId: DEFAULT_LAYER_ID,
        from: connectFrom,
        to: { elementId: hit.element.id, port: hit.port.id },
        points,
      };
      updateDocument((doc) => ({ ...doc, connections: [...doc.connections, conn] }));
      setConnectFrom(null);
      setSelectedConnectionId(conn.id);
      setSelectedIds(new Set());
    },
    [connectFrom, updateDocument]
  );

  const handleConnectAtPoint = useCallback(
    (x: number, y: number) => {
      const sx = snapCanvasCoordinate(x, documentRef.current.grid);
      const sy = snapCanvasCoordinate(y, documentRef.current.grid);
      applyConnectHit(findNearestPort(documentRef.current.elements, sx, sy, undefined, 24, documentRef.current.customSymbols));
    },
    [applyConnectHit]
  );

  const handleElementConnectClick = useCallback(
    (element: MimicElement, x: number, y: number) => {
      const sx = snapCanvasCoordinate(x, documentRef.current.grid);
      const sy = snapCanvasCoordinate(y, documentRef.current.grid);
      const portHit = findPortOnElement(element, sx, sy, documentRef.current.customSymbols);
      if (!portHit) {
        setConnectFrom(null);
        return;
      }
      applyConnectHit({ element, port: portHit.port, x: portHit.x, y: portHit.y });
    },
    [applyConnectHit]
  );

  const handleElementDragEnd = useCallback(() => {
    pushHistory(documentRef.current);
  }, [pushHistory]);

  const handleAddCustomSymbol = useCallback(
    (def: MimicCustomSymbol) => {
      updateDocument((doc) => ({
        ...doc,
        customSymbols: [...(doc.customSymbols ?? []), def],
      }));
    },
    [updateDocument]
  );

  const handleUploadCustomSymbol = useCallback(
    (file: File) => {
      const reader = new FileReader();
      reader.onload = () => {
        const parsed = parseSvgUpload(String(reader.result ?? ""));
        const id = createMimicId("csym");
        const def: MimicCustomSymbol = {
          id,
          name: file.name.replace(/\.svg$/i, "") || t("props.customSvgDefaultName"),
          svg: parsed.svg,
          width: parsed.width,
          height: parsed.height,
          viewBox: parsed.viewBox,
          ports: parsed.ports,
        };
        updateDocument((doc) => ({
          ...doc,
          customSymbols: [...(doc.customSymbols ?? []), def],
        }));
        setPlaceSymbolId(`custom:${id}`);
        setTool("place");
      };
      reader.readAsText(file);
    },
    [t, updateDocument]
  );

  const handleCanvasClick = (x: number, y: number) => {
    const sx = snapCanvasCoordinate(x, document.grid);
    const sy = snapCanvasCoordinate(y, document.grid);

    if (tool === "place" && placeSymbolId) {
      const symbol = resolvePlacementSymbol(placeSymbolId, document.customSymbols);
      if (!symbol) return;

      let props: Record<string, unknown> = {};
      if (placeSymbolId === "custom.svg") {
        props = {
          svg: DEFAULT_CUSTOM_SVG_INNER,
          viewBox: "0 0 64 64",
          width: symbol.defaultWidth,
          height: symbol.defaultHeight,
        };
      } else if (placeSymbolId.startsWith("custom:")) {
        const def = document.customSymbols?.find((s) => s.id === placeSymbolId.slice("custom:".length));
        if (def) {
          props = {
            svg: def.svg,
            viewBox: def.viewBox ?? `0 0 ${def.width} ${def.height}`,
            width: def.width,
            height: def.height,
          };
        }
      }

      const el: MimicElement = {
        id: createMimicId("el"),
        symbolId: placeSymbolId,
        layerId: DEFAULT_LAYER_ID,
        x: sx,
        y: sy,
        bindings: {},
        props,
      };
      updateDocument((doc) => ({ ...doc, elements: [...doc.elements, el] }));
      setSelectedIds(new Set([el.id]));
      setSelectedConnectionId(null);
      return;
    }

    if (tool === "connect") {
      handleConnectAtPoint(x, y);
      return;
    }

    setSelectedIds(new Set());
    setSelectedConnectionId(null);
  };

  const handleElementDrag = (id: string, x: number, y: number) => {
    const sx = snapCanvasCoordinate(x, document.grid);
    const sy = snapCanvasCoordinate(y, document.grid);
    updateDocument((doc) => {
      const elements = doc.elements.map((el) => (el.id === id ? { ...el, x: sx, y: sy } : el));
      const connections = rerouteConnectionsForElement(id, doc.connections, elements, doc.customSymbols);
      return { ...doc, elements, connections };
    }, false);
  };

  const handleDeleteSelected = () => {
    if (selectedConnectionId) {
      updateDocument((doc) => ({
        ...doc,
        connections: doc.connections.filter((c) => c.id !== selectedConnectionId),
      }));
      setSelectedConnectionId(null);
      return;
    }
    if (selectedIds.size === 0) return;
    updateDocument((doc) => ({
      ...doc,
      elements: doc.elements.filter((el) => !selectedIds.has(el.id)),
      connections: doc.connections.filter(
        (c) => !selectedIds.has(c.from.elementId) && !selectedIds.has(c.to.elementId)
      ),
    }));
    setSelectedIds(new Set());
  };

  const undo = () => {
    if (historyIndex <= 0) return;
    const nextIndex = historyIndex - 1;
    setHistoryIndex(nextIndex);
    setDocument(history[nextIndex]);
  };

  const redo = () => {
    if (historyIndex >= history.length - 1) return;
    const nextIndex = historyIndex + 1;
    setHistoryIndex(nextIndex);
    setDocument(history[nextIndex]);
  };

  useEffect(() => {
    globalThis.document.body.classList.add("scada-mimic-editor-open");
    return () => {
      globalThis.document.body.classList.remove("scada-mimic-editor-open");
    };
  }, []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;

      if (e.key === "Delete" || e.key === "Backspace") {
        e.preventDefault();
        handleDeleteSelected();
        return;
      }
      if (e.ctrlKey || e.metaKey) {
        if (e.key === "z" && !e.shiftKey) {
          e.preventDefault();
          undo();
        } else if (e.key === "y" || (e.key === "z" && e.shiftKey)) {
          e.preventDefault();
          redo();
        } else if (e.key === "s") {
          e.preventDefault();
          onSave(mimicDocumentToJson(document));
        }
        return;
      }
      if (e.key === "v" || e.key === "V") setTool("select");
      if (e.key === "p" || e.key === "P") setTool("place");
      if (e.key === "c" || e.key === "C") setTool("connect");
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [document, handleDeleteSelected, historyIndex, history.length, onSave, redo, undo]);

  const toolLabel = t(`tools.${tool === "select" ? "select" : tool === "place" ? "place" : "connect"}`);

  const overlay = (
    <div className="scada-mimic-editor-overlay">
      <div className="scada-mimic-editor">
        <header className="scada-mimic-editor-toolbar">
          <div className="scada-toolbar-brand">
            <span className="scada-toolbar-logo" aria-hidden />
            <div className="scada-toolbar-brand-text">
              <strong>{t("editor.title")}</strong>
              <span className="scada-toolbar-subtitle">
                {t("editor.meta", { width: document.width, height: document.height, count: document.elements.length })}
              </span>
            </div>
          </div>

          <div className="scada-toolbar-segment" role="toolbar" aria-label={t("editor.tools")}>
            <button
              type="button"
              className={`scada-tool-btn${tool === "select" ? " active" : ""}`}
              onClick={() => setTool("select")}
              title={`${t("tools.select")} (V)`}
            >
              <IconSelect className="scada-tool-icon" />
              <span>{t("tools.select")}</span>
            </button>
            <button
              type="button"
              className={`scada-tool-btn${tool === "place" ? " active" : ""}`}
              onClick={() => setTool("place")}
              title={`${t("tools.place")} (P)`}
            >
              <IconPlace className="scada-tool-icon" />
              <span>{t("tools.place")}</span>
            </button>
            <button
              type="button"
              className={`scada-tool-btn${tool === "connect" ? " active" : ""}`}
              onClick={() => setTool("connect")}
              title={`${t("tools.connect")} (C)`}
            >
              <IconConnect className="scada-tool-icon" />
              <span>{t("tools.connect")}</span>
            </button>
          </div>

          <div className="scada-toolbar-actions">
            <button type="button" className="scada-icon-btn" onClick={undo} disabled={historyIndex <= 0} title={`${t("tools.undo")} (Ctrl+Z)`}>
              <IconUndo className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" onClick={redo} disabled={historyIndex >= history.length - 1} title={`${t("tools.redo")} (Ctrl+Y)`}>
              <IconRedo className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn scada-icon-btn-danger" onClick={handleDeleteSelected} title={t("tools.delete")}>
              <IconTrash className="scada-tool-icon" />
            </button>
          </div>

          <div className="scada-toolbar-spacer" />

          <span className="scada-toolbar-tool-hint">{toolLabel}</span>

          <div className="scada-toolbar-primary-actions">
            <button type="button" className="scada-btn-ghost" onClick={onClose}>{t("tools.close")}</button>
            <button type="button" className="scada-btn-primary" onClick={() => onSave(mimicDocumentToJson(document))}>
              {t("tools.save")}
            </button>
          </div>
        </header>
        <div className="scada-mimic-editor-body">
          <aside className="scada-mimic-editor-sidebar scada-editor-panel">
            <SymbolPalette
              selectedSymbolId={placeSymbolId}
              customSymbols={document.customSymbols}
              onUploadCustomSymbol={handleUploadCustomSymbol}
              onSelectSymbol={(id) => {
                setPlaceSymbolId(id);
                setTool("place");
              }}
            />
          </aside>
          <main className="scada-mimic-editor-canvas-wrap">
            <div className="scada-canvas-stage">
              <div className="scada-canvas-stage-header">
                <span>{t("editor.canvas")}</span>
                <span className="scada-canvas-stage-meta">{document.width} × {document.height}</span>
              </div>
              <div className="scada-canvas-viewport">
                <ScadaMimicCanvas
                  document={document}
                  valuesByElementId={resolvedBindings.byElementId}
                  valuesByConnectionId={resolvedBindings.byConnectionId}
                  editMode
                  connectMode={tool === "connect"}
                  connectFrom={connectFrom}
                  selectedIds={selectedIds}
                  selectedConnectionId={selectedConnectionId}
                  onSelectElement={(id, additive) => {
                    if (tool === "connect") return;
                    setSelectedConnectionId(null);
                    setSelectedIds((prev) => {
                      if (additive) {
                        const next = new Set(prev);
                        if (next.has(id)) next.delete(id);
                        else next.add(id);
                        return next;
                      }
                      return new Set([id]);
                    });
                  }}
                  onSelectConnection={setSelectedConnectionId}
                  onCanvasClick={handleCanvasClick}
                  onConnectAtPoint={handleConnectAtPoint}
                  onElementConnectClick={handleElementConnectClick}
                  onElementDrag={handleElementDrag}
                  onElementDragEnd={handleElementDragEnd}
                  customSymbols={document.customSymbols}
                />
              </div>
            </div>
          </main>
          <aside className="scada-mimic-editor-props scada-editor-panel">
            <MimicPropertiesPanel
              document={document}
              selectedElement={selectedElement}
              selectedConnection={selectedConnection}
              onUpdateElement={(el) =>
                updateDocument((doc) => {
                  const elements = doc.elements.map((item) => (item.id === el.id ? el : item));
                  const connections = rerouteConnectionsForElement(
                    el.id,
                    doc.connections,
                    elements,
                    doc.customSymbols,
                  );
                  return { ...doc, elements, connections };
                })
              }
              onUpdateConnection={(conn) =>
                updateDocument((doc) => ({
                  ...doc,
                  connections: doc.connections.map((item) => (item.id === conn.id ? conn : item)),
                }))
              }
              onDeleteSelected={handleDeleteSelected}
              onAddCustomSymbol={handleAddCustomSymbol}
              onUpdateCanvasSize={(width, height) =>
                updateDocument((doc) => ({
                  ...doc,
                  width: clampCanvasDimension(width, doc.width),
                  height: clampCanvasDimension(height, doc.height),
                }))
              }
            />
            <details className="scada-import-export">
              <summary>{t("importExport.title")}</summary>
              <textarea rows={6} value={importText} onChange={(e) => setImportText(e.target.value)} placeholder={t("importExport.placeholder")} />
              <div className="scada-import-export-actions">
                <button type="button" onClick={() => setImportText(mimicDocumentToJson(document))}>{t("importExport.export")}</button>
                <button
                  type="button"
                  onClick={() => {
                    setDocument(parseMimicDocument(importText));
                    pushHistory(parseMimicDocument(importText));
                  }}
                >
                  {t("importExport.import")}
                </button>
              </div>
            </details>
          </aside>
        </div>
      </div>
    </div>
  );

  return createPortal(overlay, globalThis.document.body);
}
