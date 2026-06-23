import type { StyleSpecification } from "maplibre-gl";

/** Default raster basemap — OpenStreetMap standard tiles. */
export const DEFAULT_TILE_URL = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
export const DEFAULT_TILE_ATTRIBUTION = "© OpenStreetMap contributors";

const SUBDOMAIN_HOSTS = ["a", "b", "c"];

function expandTileSubdomains(template: string): string[] {
  if (!template.includes("{s}")) {
    return [template];
  }
  return SUBDOMAIN_HOSTS.map((host) => template.replaceAll("{s}", host));
}

function buildRasterStyle(tileUrl: string, attribution: string): StyleSpecification {
  return {
    version: 8,
    sources: {
      basemap: {
        type: "raster",
        tiles: expandTileSubdomains(tileUrl),
        tileSize: 256,
        attribution,
      },
    },
    layers: [{ id: "basemap", type: "raster", source: "basemap" }],
  };
}

export function resolveMapStyle(options: {
  mapStyleUrl?: string;
  tileUrl?: string;
  tileAttribution?: string;
}): string | StyleSpecification {
  const tileUrl = options.tileUrl?.trim();
  if (tileUrl) {
    return buildRasterStyle(
      tileUrl,
      options.tileAttribution?.trim() || DEFAULT_TILE_ATTRIBUTION
    );
  }
  const mapStyleUrl = options.mapStyleUrl?.trim();
  if (mapStyleUrl) {
    return mapStyleUrl;
  }
  return buildRasterStyle(DEFAULT_TILE_URL, DEFAULT_TILE_ATTRIBUTION);
}
