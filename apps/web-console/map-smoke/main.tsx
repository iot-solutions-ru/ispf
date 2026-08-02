import { StrictMode, useState } from "react";
import { createRoot } from "react-dom/client";
import Map, { Marker, Popup } from "react-map-gl/maplibre";
import "../src/map/maplibreSetup";
import "maplibre-gl/dist/maplibre-gl.css";
import { resolveMapStyle } from "../src/components/dashboard/mapStyleUtils";

const mapStyle = resolveMapStyle({
  tileUrl: "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
  tileAttribution: "© OpenStreetMap contributors",
});

function SmokeMap() {
  const [open, setOpen] = useState(false);
  return (
    <div data-testid="map-smoke-root" style={{ width: "100%", height: "100%" }}>
      <Map
        mapStyle={mapStyle}
        initialViewState={{ longitude: 37.62, latitude: 55.75, zoom: 10 }}
        style={{ width: "100%", height: "100%" }}
        attributionControl={{ compact: true }}
        onLoad={() => {
          document.documentElement.dataset.mapReady = "1";
        }}
        onError={(event) => {
          document.documentElement.dataset.mapError = String(event.error?.message ?? event.error);
        }}
      >
        <Marker
          longitude={37.64}
          latitude={55.76}
          anchor="center"
          onClick={(event) => {
            event.originalEvent.stopPropagation();
            setOpen(true);
          }}
        >
          <button type="button" className="dash-map-marker" aria-label="Truck 1" data-testid="smoke-marker" />
        </Marker>
        {open && (
          <Popup
            longitude={37.64}
            latitude={55.76}
            anchor="bottom"
            onClose={() => setOpen(false)}
            closeOnClick={false}
          >
            Truck 1
          </Popup>
        )}
      </Map>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <SmokeMap />
  </StrictMode>,
);
