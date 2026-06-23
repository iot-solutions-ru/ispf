import { useEffect, useMemo, useRef, useState } from "react";
import Map, { Marker, Popup, type MapRef } from "react-map-gl/maplibre";
import { useQuery } from "@tanstack/react-query";
import { fetchObjects, fetchVariables } from "../../../api";
import type { MapWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { parseJsonObject } from "../dashboardUtils";
import { resolveMapStyle } from "../mapStyleUtils";
import { triggerDashboardOpen, useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import "maplibre-gl/dist/maplibre-gl.css";

interface MapWidgetViewProps {
  widget: MapWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

interface MarkerPopup {
  path: string;
  lat: number;
  lon: number;
  label: string;
}

export default function MapWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: MapWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { setSelection, navigateToDashboard, openDashboardModal } = useDashboardContext();
  const [noGps, setNoGps] = useState<string[]>([]);
  const [popup, setPopup] = useState<MarkerPopup | null>(null);
  const mapRef = useRef<MapRef>(null);

  const children = useQuery({
    queryKey: ["objects", widget.parentPath],
    queryFn: () => fetchObjects(widget.parentPath),
    enabled: Boolean(widget.parentPath),
    refetchInterval: refreshIntervalMs,
  });

  const markers = useMemo(() => {
    return (children.data ?? []).map((obj) => ({ path: obj.path, displayName: obj.displayName }));
  }, [children.data]);

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

  const handleMarkerSelect = (path: string, lat: number, lon: number, label: string) => {
    if (editable) return;
    setPopup({ path, lat, lon, label });
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
        <p className="hint">Укажите parentPath</p>
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
            {markers.map((obj) => (
              <MapMarker
                key={obj.path}
                obj={obj}
                widget={widget}
                refreshIntervalMs={refreshIntervalMs}
                editable={editable}
                onMissing={() => setNoGps((list) => [...list, obj.path])}
                onSelect={(lat, lon, label) => handleMarkerSelect(obj.path, lat, lon, label)}
              />
            ))}
            {popup && (
              <Popup
                longitude={popup.lon}
                latitude={popup.lat}
                anchor="bottom"
                onClose={() => setPopup(null)}
                closeOnClick={false}
              >
                {popup.label}
              </Popup>
            )}
          </Map>
          {noGps.length > 0 && (
            <p className="hint dash-map-no-gps">Без GPS: {noGps.length}</p>
          )}
        </div>
      )}
    </DashWidgetShell>
  );
}

function MapMarker({
  obj,
  widget,
  refreshIntervalMs,
  editable,
  onSelect,
  onMissing,
}: {
  obj: { path: string; displayName: string };
  widget: MapWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  onSelect: (lat: number, lon: number, label: string) => void;
  onMissing: () => void;
}) {
  const vars = useQuery({
    queryKey: ["variables", obj.path],
    queryFn: () => fetchVariables(obj.path),
    refetchInterval: refreshIntervalMs,
  });

  const latVar = widget.latVariable ?? "coordinates";
  const latField = widget.latField ?? "latitude";
  const lonField = widget.lonField ?? "longitude";
  const coordVar = vars.data?.find((v) => v.name === latVar);
  const lat = Number(readFieldValue(coordVar?.value?.rows[0], latField));
  const lon = Number(readFieldValue(coordVar?.value?.rows[0], lonField));

  useEffect(() => {
    if (!vars.isLoading && (!Number.isFinite(lat) || !Number.isFinite(lon))) {
      onMissing();
    }
  }, [vars.isLoading, lat, lon, onMissing]);

  if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
    return null;
  }

  const labelVar = widget.labelVariable
    ? vars.data?.find((v) => v.name === widget.labelVariable)
    : undefined;
  const label =
    String(readFieldValue(labelVar?.value?.rows[0], "value") ?? "") || obj.displayName;

  return (
    <Marker
      longitude={lon}
      latitude={lat}
      anchor="center"
      onClick={(event) => {
        event.originalEvent.stopPropagation();
        onSelect(lat, lon, label);
      }}
    >
      <button
        type="button"
        className="dash-map-marker"
        title={label}
        disabled={editable}
        aria-label={label}
      />
    </Marker>
  );
}
