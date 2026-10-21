import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import Map, { Marker, Popup } from "react-map-gl/maplibre";
import type { MapRef } from "react-map-gl/maplibre";
import { useQuery } from "@tanstack/react-query";
import { fetchObjects } from "../../../api";
import { readFieldValue } from "../../../types/dashboard";
import type { MapWidget } from "../../../types/dashboard";
import type { VariableDto } from "../../../types";
import { useVariablesBatchQuery } from "../../../hooks/useVariablesQuery";
import { parseJsonObject } from "../dashboardUtils";
import {
  evaluateMarkerRules,
  filterMapCatalogChildren,
  hasMarkerRules,
  parseMarkerRules,
  resolveMapMarkerMode,
  resolveMapPopupMode,
} from "../mapWidgetUtils";
import type { MapMarkerStyle } from "../../../types/dashboard";
import { resolveMapStyle } from "../mapStyleUtils";
import { triggerDashboardOpen, useDashboardContext } from "../useDashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

import "../../../map/maplibreSetup";
import "maplibre-gl/dist/maplibre-gl.css";

interface MapWidgetViewProps {
  widget: MapWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

interface PopupVariableSpec {
  name: string;
  label?: string;
  field?: string;
}

interface MarkerPopup {
  path: string;
  lat: number;
  lon: number;
  label: string;
  rows: { label: string; value: string }[];
  statusClass: "ok" | "bad" | "unknown";
}

const DEFAULT_OK_STATUS = new Set([
  "RUNNING",
  "ONLINE",
  "OK",
  "CONNECTED",
  "GOOD",
  "TRUE",
  "1",
]);

function parsePopupSpecs(raw?: string): PopupVariableSpec[] {
  if (!raw?.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter((row): row is Record<string, unknown> => row != null && typeof row === "object")
      .map((row) => ({
        name: String(row.name ?? row.variable ?? "").trim(),
        label: row.label != null ? String(row.label) : undefined,
        field: row.field != null ? String(row.field) : undefined,
      }))
      .filter((row) => row.name.length > 0);
  } catch {
    return [];
  }
}

function resolveStatusClass(
  variables: VariableDto[] | undefined,
  statusVariable: string | undefined,
  statusField: string,
  okValuesRaw: string | undefined
): "ok" | "bad" | "unknown" {
  if (!statusVariable) {
    return "unknown";
  }
  const statusVar = variables?.find((v) => v.name === statusVariable);
  if (!statusVar) {
    return "unknown";
  }
  const raw = readFieldValue(statusVar.value?.rows[0], statusField);
  if (raw == null || raw === "") {
    return "unknown";
  }
  const token = String(raw).trim().toUpperCase();
  const okSet = new Set(
    (okValuesRaw ?? "")
      .split(",")
      .map((s) => s.trim().toUpperCase())
      .filter(Boolean)
  );
  const ok = okSet.size > 0 ? okSet : DEFAULT_OK_STATUS;
  return ok.has(token) ? "ok" : "bad";
}

function formatPopupValue(raw: unknown): string {
  if (raw == null) {
    return "—";
  }
  if (typeof raw === "number" && Number.isFinite(raw)) {
    return Number.isInteger(raw) ? String(raw) : raw.toFixed(2);
  }
  if (typeof raw === "boolean") {
    return raw ? "true" : "false";
  }
  return String(raw);
}

export default function MapWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: MapWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const { setSelection, navigateToDashboard, openDashboardModal } = useDashboardContext();
  const [gpsByPath, setGpsByPath] = useState<Record<string, boolean>>({});
  const [popup, setPopup] = useState<MarkerPopup | null>(null);
  const mapRef = useRef<MapRef>(null);

  const children = useQuery({
    queryKey: ["objects", widget.parentPath],
    queryFn: () => fetchObjects(widget.parentPath),
    enabled: Boolean(widget.parentPath),
    refetchInterval: refreshIntervalMs,
  });

  const markerObjects = useMemo(() => {
    const filtered = filterMapCatalogChildren(children.data ?? [], widget);
    return filtered.map((obj) => ({
      path: obj.path,
      displayName: obj.displayName,
    }));
  }, [
    children.data,
    widget.namePattern,
    widget.objectType,
    widget.includePathsJson,
    widget.excludePathsJson,
  ]);

  const markerPaths = useMemo(() => markerObjects.map((obj) => obj.path), [markerObjects]);
  const variablesBatch = useVariablesBatchQuery(
    markerPaths,
    refreshIntervalMs,
    Boolean(widget.parentPath)
  );

  const markerPathsKey = markerPaths.join("|");
  const markerRules = useMemo(() => parseMarkerRules(widget.markerRulesJson), [widget.markerRulesJson]);
  const rulesActive = markerRules.length > 0;
  const markerMode = resolveMapMarkerMode(widget);
  const popupMode = resolveMapPopupMode(widget);
  const popupSpecs = useMemo(
    () => (popupMode === "variables" ? parsePopupSpecs(widget.popupVariablesJson) : []),
    [popupMode, widget.popupVariablesJson]
  );
  const useStatus =
    !rulesActive &&
    (markerMode === "status-color" || (markerMode === "icon" && widget.markerIconTintByStatus));

  useEffect(() => {
    setGpsByPath({});
  }, [markerPathsKey]);

  const reportGps = useCallback((path: string, hasGps: boolean) => {
    setGpsByPath((prev) => (prev[path] === hasGps ? prev : { ...prev, [path]: hasGps }));
  }, []);

  const reportedCount = markerObjects.filter((obj) => gpsByPath[obj.path] !== undefined).length;
  const missingCount = markerObjects.filter((obj) => gpsByPath[obj.path] === false).length;

  const centerLat = widget.centerLat ?? 55.75;
  const centerLon = widget.centerLon ?? 37.62;
  const zoom = widget.zoom ?? 10;

  const mapStyle = useMemo(
    () =>
      resolveMapStyle({
        mapStyleUrl: widget.mapStyleUrl,
        tileUrl: widget.tileUrl,
        tileAttribution: widget.tileAttribution,
      }),
    [widget.mapStyleUrl, widget.tileUrl, widget.tileAttribution]
  );

  useEffect(() => {
    mapRef.current?.flyTo({ center: [centerLon, centerLat], zoom, duration: 0 });
  }, [centerLat, centerLon, zoom]);

  // Keep popup values fresh while open.
  useEffect(() => {
    if (!popup || popupMode === "none") {
      return;
    }
    const vars = variablesBatch.data?.[popup.path];
    if (!vars) {
      return;
    }
    const rows =
      popupMode === "variables"
        ? popupSpecs.map((spec) => {
            const variable = vars.find((v) => v.name === spec.name);
            const field = spec.field ?? "value";
            const value = formatPopupValue(readFieldValue(variable?.value?.rows[0], field));
            return { label: spec.label ?? spec.name, value };
          })
        : [];
    const statusClass = useStatus
      ? resolveStatusClass(
          vars,
          widget.statusVariable,
          widget.statusField ?? "value",
          widget.statusOkValues
        )
      : "unknown";
    setPopup((prev) =>
      prev && prev.path === popup.path ? { ...prev, rows, statusClass } : prev
    );
  }, [
    popup?.path,
    popupMode,
    popupSpecs,
    useStatus,
    variablesBatch.data,
    widget.statusField,
    widget.statusOkValues,
    widget.statusVariable,
  ]);

  const handleMarkerSelect = (
    path: string,
    lat: number,
    lon: number,
    label: string,
    rows: { label: string; value: string }[],
    statusClass: "ok" | "bad" | "unknown"
  ) => {
    if (editable) return;
    if (popupMode !== "none") {
      setPopup({ path, lat, lon, label, rows, statusClass });
    } else {
      setPopup(null);
    }
    const targetKey = widget.rowSelectionKey ?? widget.selectionKey;
    const openOptions = {
      selection: targetKey ? { [targetKey]: path } : undefined,
      params: parseJsonObject(widget.rowParamsJson),
    };
    if (widget.selectionKey) {
      setSelection(widget.selectionKey, path);
    }
    triggerDashboardOpen(
      widget.rowOpenMode,
      widget.rowTargetDashboard,
      widget.title,
      { navigateToDashboard, openDashboardModal },
      openOptions
    );
  };

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-map"
      editable={editable}
    >
      {!widget.parentPath ? (
        <p className="hint">{t("view.mapParentPathHint")}</p>
      ) : markerObjects.length === 0 ? (
        <p className="hint">{t("view.noChildObjects", { path: widget.parentPath })}</p>
      ) : (
        <div className="dash-map-wrap dash-map-container" style={styles.body}>
          <Map
            ref={mapRef}
            mapStyle={mapStyle}
            initialViewState={{
              longitude: centerLon,
              latitude: centerLat,
              zoom,
            }}
            style={{ width: "100%", height: "100%" }}
            scrollZoom={!editable}
            dragPan={!editable}
            dragRotate={false}
            doubleClickZoom={!editable}
            touchZoomRotate={!editable}
            keyboard={!editable}
            attributionControl={{ compact: true }}
            onClick={() => setPopup(null)}
          >
            {markerObjects.map((obj) => (
              <MapMarker
                key={obj.path}
                obj={obj}
                widget={widget}
                markerMode={markerMode}
                markerRules={markerRules}
                rulesActive={rulesActive}
                useStatus={useStatus}
                popupMode={popupMode}
                popupSpecs={popupSpecs}
                variables={variablesBatch.data?.[obj.path]}
                variablesLoading={variablesBatch.isLoading}
                editable={editable}
                onGpsStatus={reportGps}
                onSelect={(lat, lon, label, rows, statusClass) =>
                  handleMarkerSelect(obj.path, lat, lon, label, rows, statusClass)
                }
              />
            ))}
            {popup && popupMode !== "none" && (
              <Popup
                longitude={popup.lon}
                latitude={popup.lat}
                anchor="bottom"
                onClose={() => setPopup(null)}
                closeOnClick={false}
              >
                <div className={`dash-map-popup dash-map-popup--${popup.statusClass}`}>
                  <div className="dash-map-popup-title">{popup.label}</div>
                  {popup.rows.length > 0 ? (
                    <dl className="dash-map-popup-rows">
                      {popup.rows.map((row) => (
                        <div key={row.label} className="dash-map-popup-row">
                          <dt>{row.label}</dt>
                          <dd>{row.value}</dd>
                        </div>
                      ))}
                    </dl>
                  ) : null}
                </div>
              </Popup>
            )}
          </Map>
          {reportedCount === markerObjects.length && missingCount > 0 && (
            <p className="hint dash-map-no-gps">
              {t("view.mapMissingCoords", { missing: missingCount, total: markerObjects.length })}
            </p>
          )}
        </div>
      )}
    </DashWidgetShell>
  );
}

function resolveMarkerAppearance(args: {
  rulesActive: boolean;
  ruleStyle: MapMarkerStyle | null;
  markerMode: ReturnType<typeof resolveMapMarkerMode>;
  useStatus: boolean;
  statusClass: "ok" | "bad" | "unknown";
  widget: MapWidget;
}): {
  markerClass: string;
  markerStyle: CSSProperties | undefined;
  iconUrl?: string;
  iconSize: number;
  dataMode: string;
} {
  const { rulesActive, ruleStyle, markerMode, useStatus, statusClass, widget } = args;
  if (rulesActive) {
    if (ruleStyle?.type === "color") {
      return {
        markerClass: "dash-map-marker dash-map-marker--fixed dash-map-marker--rule",
        markerStyle: { background: ruleStyle.color },
        iconSize: 16,
        dataMode: "rule-color",
      };
    }
    if (ruleStyle?.type === "icon") {
      const iconSize = Math.max(12, Math.min(96, ruleStyle.size ?? 24));
      return {
        markerClass: "dash-map-marker dash-map-marker--icon dash-map-marker--rule",
        markerStyle: {
          width: iconSize,
          height: iconSize,
          background: "transparent",
          border: "none",
          boxShadow: "none",
        },
        iconUrl: ruleStyle.url,
        iconSize,
        dataMode: "rule-icon",
      };
    }
    return {
      markerClass: "dash-map-marker dash-map-marker--unknown dash-map-marker--rule",
      markerStyle: undefined,
      iconSize: 16,
      dataMode: "rule-default",
    };
  }

  const iconSize = Math.max(12, Math.min(96, widget.markerIconSize ?? 24));
  const fixedColor = widget.markerColor?.trim() || undefined;
  const iconUrl = widget.markerIconUrl?.trim() || undefined;
  const markerClass =
    markerMode === "icon"
      ? `dash-map-marker dash-map-marker--icon${useStatus ? ` dash-map-marker--${statusClass}` : ""}`
      : markerMode === "fixed-color"
        ? "dash-map-marker dash-map-marker--fixed"
        : `dash-map-marker dash-map-marker--${statusClass}`;
  const markerStyle: CSSProperties | undefined =
    markerMode === "fixed-color" && fixedColor
      ? { background: fixedColor }
      : markerMode === "icon"
        ? {
            width: iconSize,
            height: iconSize,
            background: "transparent",
            border: "none",
            boxShadow: "none",
          }
        : undefined;
  return {
    markerClass,
    markerStyle,
    iconUrl: markerMode === "icon" ? iconUrl : undefined,
    iconSize,
    dataMode: markerMode,
  };
}

function MapMarker({
  obj,
  widget,
  markerMode,
  markerRules,
  rulesActive,
  useStatus,
  popupMode,
  popupSpecs,
  variables,
  variablesLoading,
  editable,
  onSelect,
  onGpsStatus,
}: {
  obj: { path: string; displayName: string };
  widget: MapWidget;
  markerMode: ReturnType<typeof resolveMapMarkerMode>;
  markerRules: ReturnType<typeof parseMarkerRules>;
  rulesActive: boolean;
  useStatus: boolean;
  popupMode: ReturnType<typeof resolveMapPopupMode>;
  popupSpecs: PopupVariableSpec[];
  variables?: VariableDto[];
  variablesLoading: boolean;
  editable?: boolean;
  onSelect: (
    lat: number,
    lon: number,
    label: string,
    rows: { label: string; value: string }[],
    statusClass: "ok" | "bad" | "unknown"
  ) => void;
  onGpsStatus: (path: string, hasGps: boolean) => void;
}) {
  const latVar = widget.latVariable ?? "coordinates";
  const latField = widget.latField ?? "latitude";
  const lonField = widget.lonField ?? "longitude";
  const coordVar = variables?.find((v) => v.name === latVar);
  const lat = Number(readFieldValue(coordVar?.value?.rows[0], latField));
  const lon = Number(readFieldValue(coordVar?.value?.rows[0], lonField));
  const hasGps = Number.isFinite(lat) && Number.isFinite(lon);

  useEffect(() => {
    if (!variablesLoading) {
      onGpsStatus(obj.path, hasGps);
    }
  }, [variablesLoading, hasGps, obj.path, onGpsStatus]);

  if (!hasGps) {
    return null;
  }

  const labelVar = widget.labelVariable
    ? variables?.find((v) => v.name === widget.labelVariable)
    : undefined;
  const label =
    String(readFieldValue(labelVar?.value?.rows[0], "value") ?? "") || obj.displayName;

  const statusClass = useStatus
    ? resolveStatusClass(
        variables,
        widget.statusVariable,
        widget.statusField ?? "value",
        widget.statusOkValues
      )
    : "unknown";

  const rows =
    popupMode === "variables"
      ? popupSpecs.map((spec) => {
          const variable = variables?.find((v) => v.name === spec.name);
          const field = spec.field ?? "value";
          return {
            label: spec.label ?? spec.name,
            value: formatPopupValue(readFieldValue(variable?.value?.rows[0], field)),
          };
        })
      : [];

  const ruleStyle = rulesActive ? evaluateMarkerRules(variables, markerRules) : null;
  const appearance = resolveMarkerAppearance({
    rulesActive,
    ruleStyle,
    markerMode,
    useStatus,
    statusClass,
    widget,
  });

  return (
    <Marker
      longitude={lon}
      latitude={lat}
      anchor="center"
      onClick={(event) => {
        event.originalEvent.stopPropagation();
        onSelect(lat, lon, label, rows, statusClass);
      }}
    >
      <button
        type="button"
        className={appearance.markerClass}
        style={appearance.markerStyle}
        title={label}
        disabled={editable}
        aria-label={label}
        data-marker-mode={appearance.dataMode}
        data-has-rules={hasMarkerRules(widget) ? "1" : "0"}
      >
        {appearance.iconUrl ? (
          <img
            src={appearance.iconUrl}
            alt=""
            width={appearance.iconSize}
            height={appearance.iconSize}
            className="dash-map-marker-icon-img"
            draggable={false}
          />
        ) : null}
      </button>
    </Marker>
  );
}
