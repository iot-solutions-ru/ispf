import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import type { MimicConnection, MimicCustomSymbol, MimicElement, MimicLayer, ScadaMimicDocument } from "../../types/scadaMimic";
import { useMimicHistory } from "../../hooks/useMimicHistory";
import { DEFAULT_CUSTOM_SVG_INNER, parseSvgUpload } from "../../scada/customSvg";
import {
  convertDocumentToLibrarySymbols,
  documentHasBuiltinSymbols,
} from "../../scada/convertBuiltinToLibrary";
import {
  createMimicId,
  DEFAULT_LAYER_ID,
  mimicDocumentToJson,
  parseMimicDocument,
  snapCanvasCoordinate,
} from "../../scada/document";
import { findNearestPort, findPortOnElement, getElementPortPosition, rerouteConnectionsForElement, routeOrthogonal } from "../../scada/connectionRouting";
import { snapElementPosition, type SnapGuide } from "../../scada/elementSnap";
import {
  alignElements,
  applyElementResize,
  boundsIntersect,
  distributeElements,
  flipElement,
  getElementBounds,
  rotateElement,
  setElementSize,
  type AlignMode,
  type DistributeAxis,
  type ResizeHandle,
} from "../../scada/layoutOps";
import { directionFromArrowKey, findElementInDirection } from "../../scada/mimicKeyboardNav";
import { collectBindingInterests, collectBindingPaths, groupBindingVariablesByPath, resolveDocumentBindings } from "../../scada/bindingResolver";
import { ensurePackLoaded, resolvePlacementSymbol } from "../../scada/symbols/registry";
import { useVariablesBatchQuery } from "../../hooks/useVariablesQuery";
import { useMimicHostSession } from "./MimicHostContext";
import ScadaMimicCanvas from "./ScadaMimicCanvas";
import SymbolPalette from "./SymbolPalette";
import MimicPropertiesPanel from "./MimicPropertiesPanel";
import MimicLayerPanel from "./MimicLayerPanel";
import MimicElementsListPanel from "./MimicElementsListPanel";
import {
  IconAlignBottom,
  IconAlignCenterH,
  IconAlignLeft,
  IconAlignMiddleV,
  IconAlignRight,
  IconAlignTop,
  IconConnect,
  IconDistributeH,
  IconDistributeV,
  IconFlipH,
  IconFlipV,
  IconGrid,
  IconPlace,
  IconRedo,
  IconRotateCcw,
  IconRotateCw,
  IconSelect,
  IconSnapGrid,
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
  /** Binding host session — not DashboardContext (editor boundary). */
  const session = useMimicHostSession();
  const {
    present: document,
    setPresent: setDocumentState,
    reset: resetDocumentHistory,
    undo,
    redo,
    canUndo,
    canRedo,
  } = useMimicHistory<ScadaMimicDocument>(parseMimicDocument(diagramJson));
  const [tool, setTool] = useState<ScadaEditorTool>("select");
  const [placeSymbolId, setPlaceSymbolId] = useState<string | null>("pack.ispf-pid.vertical-tank");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [selectedConnectionId, setSelectedConnectionId] = useState<string | null>(null);
  const [connectFrom, setConnectFrom] = useState<{ elementId: string; port: string } | null>(null);
  const [importText, setImportText] = useState("");
  const [snapGuides, setSnapGuides] = useState<SnapGuide[]>([]);
  const [activeLayerId, setActiveLayerId] = useState(DEFAULT_LAYER_ID);
  const documentRef = useRef(document);
  const editorRef = useRef<HTMLDivElement>(null);
  const dragOriginsRef = useRef<Map<string, { x: number; y: number }>>(new Map());
  const resizeOriginRef = useRef<MimicElement | null>(null);
  documentRef.current = document;

  useEffect(() => {
    void ensurePackLoaded();
  }, []);

  useEffect(() => {
    if (tool !== "connect") {
      setConnectFrom(null);
    }
  }, [tool]);

  const updateDocument = useCallback(
    (updater: (doc: ScadaMimicDocument) => ScadaMimicDocument, recordHistory = true) => {
      setDocumentState(updater(documentRef.current), recordHistory);
    },
    [setDocumentState]
  );

  const selectedElement = useMemo(
    () => document.elements.find((el) => selectedIds.has(el.id)) ?? null,
    [document.elements, selectedIds]
  );
  const selectedConnection = useMemo(
    () => document.connections.find((c) => c.id === selectedConnectionId) ?? null,
    [document.connections, selectedConnectionId]
  );
  const customSymbolNames = useMemo(
    () => new Map((document.customSymbols ?? []).map((sym) => [sym.id, sym.name])),
    [document.customSymbols]
  );

  const bindingPaths = useMemo(
    () => collectBindingPaths(document.elements, document.connections, session),
    [document, session]
  );
  const bindingVariablesByPath = useMemo(
    () =>
      groupBindingVariablesByPath(
        collectBindingInterests(document.elements, document.connections, session)
      ),
    [document, session]
  );
  const variablesBatch = useVariablesBatchQuery(
    bindingPaths,
    3000,
    bindingPaths.length > 0,
    bindingVariablesByPath
  );
  const resolvedBindings = useMemo(
    () => resolveDocumentBindings(document.elements, document.connections, session, variablesBatch.data ?? {}),
    [document, session, variablesBatch.data]
  );

  useEffect(() => {
    resetDocumentHistory(parseMimicDocument(diagramJson));
    setSelectedIds(new Set());
    setSelectedConnectionId(null);
    setConnectFrom(null);
  }, [diagramJson, resetDocumentHistory]);

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
        layerId: activeLayerId,
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
    dragOriginsRef.current = new Map();
    setSnapGuides([]);
    setDocumentState(documentRef.current, true);
  }, [setDocumentState]);

  const rerouteElements = useCallback(
    (connections: MimicConnection[], elements: MimicElement[], ids: Iterable<string>, customSymbols?: MimicCustomSymbol[]) => {
      let next = connections;
      for (const id of ids) {
        next = rerouteConnectionsForElement(id, next, elements, customSymbols);
      }
      return next;
    },
    []
  );

  const handleNavigateSelection = useCallback(
    (direction: ReturnType<typeof directionFromArrowKey>) => {
      if (!direction || tool !== "select") return;
      const anchorId = selectedIds.size > 0 ? [...selectedIds][0] : null;
      const next = findElementInDirection(
        documentRef.current.elements,
        anchorId,
        direction,
        documentRef.current.customSymbols
      );
      if (!next) return;
      setSelectedConnectionId(null);
      setSelectedIds(new Set([next.id]));
    },
    [selectedIds, tool]
  );

  const handleNudgeSelected = useCallback(
    (dx: number, dy: number) => {
      if (selectedIds.size === 0 || tool !== "select") return;
      updateDocument((doc) => {
        const elements = doc.elements.map((el) =>
          selectedIds.has(el.id) ? { ...el, x: el.x + dx, y: el.y + dy } : el
        );
        return {
          ...doc,
          elements,
          connections: rerouteElements(doc.connections, elements, selectedIds, doc.customSymbols),
        };
      });
    },
    [rerouteElements, selectedIds, tool, updateDocument]
  );

  const handleAlign = useCallback(
    (mode: AlignMode) => {
      if (selectedIds.size < 2 || tool === "connect") return;
      updateDocument((doc) => {
        const elements = alignElements(doc.elements, selectedIds, mode, doc.customSymbols);
        return {
          ...doc,
          elements,
          connections: rerouteElements(doc.connections, elements, selectedIds, doc.customSymbols),
        };
      });
    },
    [rerouteElements, selectedIds, tool, updateDocument]
  );

  const handleDistribute = useCallback(
    (axis: DistributeAxis) => {
      if (selectedIds.size < 3 || tool === "connect") return;
      updateDocument((doc) => {
        const elements = distributeElements(doc.elements, selectedIds, axis, doc.customSymbols);
        return {
          ...doc,
          elements,
          connections: rerouteElements(doc.connections, elements, selectedIds, doc.customSymbols),
        };
      });
    },
    [rerouteElements, selectedIds, tool, updateDocument]
  );

  const handleFlip = useCallback(
    (axis: "h" | "v") => {
      if (selectedIds.size === 0 || tool === "connect") return;
      updateDocument((doc) => {
        const elements = doc.elements.map((el) =>
          selectedIds.has(el.id) ? flipElement(el, axis) : el
        );
        return {
          ...doc,
          elements,
          connections: rerouteElements(doc.connections, elements, selectedIds, doc.customSymbols),
        };
      });
    },
    [rerouteElements, selectedIds, tool, updateDocument]
  );

  const handleRotate = useCallback(
    (delta: 90 | -90) => {
      if (selectedIds.size === 0 || tool === "connect") return;
      updateDocument((doc) => {
        const elements = doc.elements.map((el) =>
          selectedIds.has(el.id) ? rotateElement(el, delta) : el
        );
        return {
          ...doc,
          elements,
          connections: rerouteElements(doc.connections, elements, selectedIds, doc.customSymbols),
        };
      });
    },
    [rerouteElements, selectedIds, tool, updateDocument]
  );

  const toggleGridVisible = useCallback(() => {
    updateDocument((doc) => ({
      ...doc,
      grid: {
        size: doc.grid?.size ?? 10,
        snap: doc.grid?.snap ?? false,
        visible: !(doc.grid?.visible === true),
      },
    }));
  }, [updateDocument]);

  const toggleGridSnap = useCallback(() => {
    updateDocument((doc) => {
      const snap = !(doc.grid?.snap === true);
      return {
        ...doc,
        grid: {
          size: doc.grid?.size && doc.grid.size > 1 ? doc.grid.size : 10,
          snap,
          visible: doc.grid?.visible ?? false,
        },
      };
    });
  }, [updateDocument]);

  const handleAddCustomSymbol = useCallback(
    (def: MimicCustomSymbol) => {
      updateDocument((doc) => ({
        ...doc,
        customSymbols: [...(doc.customSymbols ?? []), def],
      }));
    },
    [updateDocument]
  );

  const handleUpdateCustomSymbol = useCallback(
    (id: string, patch: Partial<MimicCustomSymbol>) => {
      updateDocument((doc) => ({
        ...doc,
        customSymbols: (doc.customSymbols ?? []).map((sym) =>
          sym.id === id ? { ...sym, ...patch } : sym
        ),
      }));
    },
    [updateDocument]
  );

  const handleUpdateCustomSymbols = useCallback(
    (symbols: MimicCustomSymbol[]) => {
      updateDocument((doc) => ({ ...doc, customSymbols: symbols }));
    },
    [updateDocument]
  );

  const handleConvertDocumentToLibrary = useCallback(() => {
    updateDocument((doc) => convertDocumentToLibrarySymbols(doc));
  }, [updateDocument]);

  const hasBuiltinSymbols = useMemo(() => documentHasBuiltinSymbols(document), [document]);

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
          inUserLibrary: true,
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

  const handleUpdateLayers = useCallback(
    (layers: MimicLayer[]) => {
      updateDocument((doc) => {
        const nextIds = new Set(layers.map((l) => l.id));
        const removed = doc.layers.filter((l) => !nextIds.has(l.id)).map((l) => l.id);
        let elements = doc.elements;
        let connections = doc.connections;
        for (const layerId of removed) {
          elements = elements.map((el) =>
            el.layerId === layerId ? { ...el, layerId: DEFAULT_LAYER_ID } : el
          );
          connections = connections.map((c) =>
            c.layerId === layerId ? { ...c, layerId: DEFAULT_LAYER_ID } : c
          );
        }
        return { ...doc, layers, elements, connections };
      });
    },
    [updateDocument]
  );

  const handleMarqueeSelect = useCallback(
    (rect: { x1: number; y1: number; x2: number; y2: number }, additive: boolean) => {
      if (tool !== "select") return;
      const selection = {
        left: Math.min(rect.x1, rect.x2),
        top: Math.min(rect.y1, rect.y2),
        right: Math.max(rect.x1, rect.x2),
        bottom: Math.max(rect.y1, rect.y2),
      };
      const hitIds = document.elements
        .filter((el) => boundsIntersect(selection, getElementBounds(el, document.customSymbols)))
        .map((el) => el.id);
      setSelectedConnectionId(null);
      setSelectedIds((prev) => {
        if (additive) {
          const next = new Set(prev);
          for (const id of hitIds) next.add(id);
          return next;
        }
        return new Set(hitIds);
      });
    },
    [document.customSymbols, document.elements, tool]
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
            width: def.width,
            height: def.height,
          };
        }
      }

      const el: MimicElement = {
        id: createMimicId("el"),
        symbolId: placeSymbolId,
        layerId: activeLayerId,
        x: sx,
        y: sy,
        bindings: {},
        props,
      };
      updateDocument((doc) => ({
        ...doc,
        elements: [...doc.elements, el],
      }));
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

  const handleElementDragStart = useCallback((id: string, origins: Map<string, { x: number; y: number }>) => {
    dragOriginsRef.current = origins;
    if (!origins.has(id)) {
      const el = documentRef.current.elements.find((e) => e.id === id);
      if (el) dragOriginsRef.current.set(id, { x: el.x, y: el.y });
    }
  }, []);

  const handleElementDrag = useCallback((id: string, x: number, y: number) => {
    const origins = dragOriginsRef.current;
    const leaderOrig = origins.get(id);
    if (!leaderOrig) return;
    const leader = documentRef.current.elements.find((e) => e.id === id);
    if (!leader) return;

    const excludeIds = new Set(origins.keys());
    const snapped = snapElementPosition(
      leader,
      leaderOrig.x,
      leaderOrig.y,
      x,
      y,
      documentRef.current.elements,
      excludeIds,
      documentRef.current.grid,
      documentRef.current.customSymbols
    );
    setSnapGuides(snapped.guides);
    const dx = snapped.x - leaderOrig.x;
    const dy = snapped.y - leaderOrig.y;

    updateDocument((doc) => {
      const elements = doc.elements.map((el) => {
        const orig = origins.get(el.id);
        if (!orig) return el;
        return {
          ...el,
          x: snapCanvasCoordinate(orig.x + dx, doc.grid),
          y: snapCanvasCoordinate(orig.y + dy, doc.grid),
        };
      });
      const connections = rerouteElements(doc.connections, elements, origins.keys(), doc.customSymbols);
      return { ...doc, elements, connections };
    }, false);
  }, [rerouteElements, updateDocument]);

  const handleElementResizeStart = useCallback((id: string) => {
    const el = documentRef.current.elements.find((e) => e.id === id);
    resizeOriginRef.current = el ? { ...el, props: el.props ? { ...el.props } : {} } : null;
  }, []);

  const handleElementResize = useCallback(
    (id: string, handle: ResizeHandle, dx: number, dy: number, aspectLock: boolean) => {
      const base = resizeOriginRef.current;
      if (!base || base.id !== id) return;
      const size = applyElementResize(base, handle, dx, dy, documentRef.current.customSymbols, {
        aspectLock,
        grid: documentRef.current.grid?.snap ? documentRef.current.grid : undefined,
      });
      updateDocument((doc) => {
        const updated = setElementSize({ ...base, x: size.x, y: size.y }, size.width, size.height, doc.customSymbols);
        const elements = doc.elements.map((el) => (el.id === id ? updated : el));
        const connections = rerouteConnectionsForElement(id, doc.connections, elements, doc.customSymbols);
        return { ...doc, elements, connections };
      }, false);
    },
    [updateDocument]
  );

  const handleElementResizeEnd = useCallback(() => {
    resizeOriginRef.current = null;
    setDocumentState(documentRef.current, true);
  }, [setDocumentState]);

  const handleDeleteSelected = useCallback(() => {
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
  }, [selectedConnectionId, selectedIds, updateDocument]);

  useEffect(() => {
    globalThis.document.body.classList.add("scada-mimic-editor-open");
    editorRef.current?.focus();
    return () => {
      globalThis.document.body.classList.remove("scada-mimic-editor-open");
    };
  }, []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
      if ((e.target as HTMLElement | null)?.isContentEditable) return;

      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
        return;
      }
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
      const navDirection = directionFromArrowKey(e.key);
      if (navDirection) {
        if (tool !== "select") return;
        e.preventDefault();
        if (e.shiftKey) {
          if (selectedIds.size === 0) return;
          const base = document.grid?.snap ? (document.grid?.size ?? 1) : 1;
          const step = e.ctrlKey || e.metaKey ? base * 10 : base;
          const dx = navDirection === "left" ? -step : navDirection === "right" ? step : 0;
          const dy = navDirection === "up" ? -step : navDirection === "down" ? step : 0;
          handleNudgeSelected(dx, dy);
        } else {
          handleNavigateSelection(navDirection);
        }
        return;
      }
      if (e.key === "v" || e.key === "V") setTool("select");
      if (e.key === "p" || e.key === "P") setTool("place");
      if (e.key === "c" || e.key === "C") setTool("connect");
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [
    document,
    handleDeleteSelected,
    handleNavigateSelection,
    handleNudgeSelected,
    canRedo,
    canUndo,
    onClose,
    onSave,
    redo,
    selectedIds.size,
    tool,
    undo,
  ]);

  const layoutDisabled = tool === "connect" || selectedIds.size === 0;
  const alignDisabled = layoutDisabled || selectedIds.size < 2;
  const distributeDisabled = layoutDisabled || selectedIds.size < 3;

  const toolLabel = t(`tools.${tool === "select" ? "select" : tool === "place" ? "place" : "connect"}`);

  const overlay = (
    <div className="scada-mimic-editor-overlay">
      <div
        ref={editorRef}
        className="scada-mimic-editor"
        role="dialog"
        aria-modal="true"
        aria-label={t("editor.title")}
        tabIndex={-1}
      >
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
              aria-pressed={tool === "select"}
            >
              <IconSelect className="scada-tool-icon" />
              <span>{t("tools.select")}</span>
            </button>
            <button
              type="button"
              className={`scada-tool-btn${tool === "place" ? " active" : ""}`}
              onClick={() => setTool("place")}
              title={`${t("tools.place")} (P)`}
              aria-pressed={tool === "place"}
            >
              <IconPlace className="scada-tool-icon" />
              <span>{t("tools.place")}</span>
            </button>
            <button
              type="button"
              className={`scada-tool-btn${tool === "connect" ? " active" : ""}`}
              onClick={() => setTool("connect")}
              title={`${t("tools.connect")} (C)`}
              aria-pressed={tool === "connect"}
            >
              <IconConnect className="scada-tool-icon" />
              <span>{t("tools.connect")}</span>
            </button>
          </div>

          <span className="scada-toolbar-divider" aria-hidden />

          <div className="scada-toolbar-segment scada-toolbar-group" role="toolbar" aria-label={t("tools.transform")}>
            <button type="button" className="scada-icon-btn" disabled={layoutDisabled} onClick={() => handleFlip("h")} title={t("tools.flipH")} aria-label={t("tools.flipH")}>
              <IconFlipH className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" disabled={layoutDisabled} onClick={() => handleFlip("v")} title={t("tools.flipV")} aria-label={t("tools.flipV")}>
              <IconFlipV className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" disabled={layoutDisabled} onClick={() => handleRotate(90)} title={t("tools.rotateCw")} aria-label={t("tools.rotateCw")}>
              <IconRotateCw className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" disabled={layoutDisabled} onClick={() => handleRotate(-90)} title={t("tools.rotateCcw")} aria-label={t("tools.rotateCcw")}>
              <IconRotateCcw className="scada-tool-icon" />
            </button>
          </div>

          <span className="scada-toolbar-divider" aria-hidden />

          <div className="scada-toolbar-segment scada-toolbar-group" role="toolbar" aria-label={t("tools.align")}>
            <button type="button" className="scada-icon-btn" disabled={alignDisabled} onClick={() => handleAlign("left")} title={t("tools.alignLeft")} aria-label={t("tools.alignLeft")}>
              <IconAlignLeft className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" disabled={alignDisabled} onClick={() => handleAlign("centerX")} title={t("tools.alignCenterH")} aria-label={t("tools.alignCenterH")}>
              <IconAlignCenterH className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" disabled={alignDisabled} onClick={() => handleAlign("right")} title={t("tools.alignRight")} aria-label={t("tools.alignRight")}>
              <IconAlignRight className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" disabled={alignDisabled} onClick={() => handleAlign("top")} title={t("tools.alignTop")} aria-label={t("tools.alignTop")}>
              <IconAlignTop className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" disabled={alignDisabled} onClick={() => handleAlign("centerY")} title={t("tools.alignMiddleV")} aria-label={t("tools.alignMiddleV")}>
              <IconAlignMiddleV className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" disabled={alignDisabled} onClick={() => handleAlign("bottom")} title={t("tools.alignBottom")} aria-label={t("tools.alignBottom")}>
              <IconAlignBottom className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" disabled={distributeDisabled} onClick={() => handleDistribute("horizontal")} title={t("tools.distributeH")} aria-label={t("tools.distributeH")}>
              <IconDistributeH className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" disabled={distributeDisabled} onClick={() => handleDistribute("vertical")} title={t("tools.distributeV")} aria-label={t("tools.distributeV")}>
              <IconDistributeV className="scada-tool-icon" />
            </button>
          </div>

          <span className="scada-toolbar-divider" aria-hidden />

          <div className="scada-toolbar-segment scada-toolbar-group" role="toolbar" aria-label={t("tools.grid")}>
            <button
              type="button"
              className={`scada-icon-btn${document.grid?.visible ? " active" : ""}`}
              onClick={toggleGridVisible}
              title={t("tools.gridVisible")}
              aria-label={t("tools.gridVisible")}
              aria-pressed={document.grid?.visible === true}
            >
              <IconGrid className="scada-tool-icon" />
            </button>
            <button
              type="button"
              className={`scada-icon-btn${document.grid?.snap ? " active" : ""}`}
              onClick={toggleGridSnap}
              title={t("tools.gridSnap")}
              aria-label={t("tools.gridSnap")}
              aria-pressed={document.grid?.snap === true}
            >
              <IconSnapGrid className="scada-tool-icon" />
            </button>
          </div>

          <div className="scada-toolbar-actions">
            <button type="button" className="scada-icon-btn" onClick={undo} disabled={!canUndo} title={`${t("tools.undo")} (Ctrl+Z)`} aria-label={t("tools.undo")}>
              <IconUndo className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn" onClick={redo} disabled={!canRedo} title={`${t("tools.redo")} (Ctrl+Y)`} aria-label={t("tools.redo")}>
              <IconRedo className="scada-tool-icon" />
            </button>
            <button type="button" className="scada-icon-btn scada-icon-btn-danger" onClick={handleDeleteSelected} title={`${t("tools.delete")} (Del)`} aria-label={t("tools.delete")}>
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
            <MimicLayerPanel
              layers={document.layers}
              activeLayerId={activeLayerId}
              onActiveLayerChange={setActiveLayerId}
              onUpdateLayers={handleUpdateLayers}
            />
            <MimicElementsListPanel
              elements={document.elements}
              layers={document.layers}
              customSymbolNames={customSymbolNames}
              selectedIds={selectedIds}
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
                  snapGuides={snapGuides}
                  showResizeHandles={tool === "select"}
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
                  onMarqueeSelect={tool === "select" ? handleMarqueeSelect : undefined}
                  onConnectAtPoint={handleConnectAtPoint}
                  onElementConnectClick={handleElementConnectClick}
                  onElementDragStart={handleElementDragStart}
                  onElementDrag={handleElementDrag}
                  onElementDragEnd={handleElementDragEnd}
                  onElementResizeStart={handleElementResizeStart}
                  onElementResize={handleElementResize}
                  onElementResizeEnd={handleElementResizeEnd}
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
              onUpdateCustomSymbol={handleUpdateCustomSymbol}
              onUpdateCustomSymbols={handleUpdateCustomSymbols}
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
              {hasBuiltinSymbols && (
                <button
                  type="button"
                  className="scada-btn-ghost scada-btn-block scada-import-export-convert"
                  onClick={handleConvertDocumentToLibrary}
                >
                  {t("importExport.convertToLibrary")}
                </button>
              )}
              <textarea rows={6} value={importText} onChange={(e) => setImportText(e.target.value)} placeholder={t("importExport.placeholder")} />
              <div className="scada-import-export-actions">
                <button type="button" onClick={() => setImportText(mimicDocumentToJson(document))}>{t("importExport.export")}</button>
                <button
                  type="button"
                  onClick={() => {
                    const imported = parseMimicDocument(importText);
                    resetDocumentHistory(imported);
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
