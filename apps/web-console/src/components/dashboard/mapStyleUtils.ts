import type { StyleSpecification } from "maplibre-gl";

/** Neutral default — MapLibre demo tiles, no API key. */
export const DEFAULT_MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json";

const SUBDOMAIN_HOSTS = ["a", "b", "c"];

function expandTileSubdomains(template: string): string[] {
  if (!template.includes("{s}")) {
    return [template];
  }
  return SUBDOMAIN_HOSTS.map((host) => template.replaceAll("{s}", host));
}

export function resolveMapStyle(options: {
  mapStyleUrl?: string;
  tileUrl?: string;
  tileAttribution?: string;
}): string | StyleSpecification {
  const tileUrl = options.tileUrl?.trim();
  if (tileUrl) {
    return {
      version: 8,
      sources: {
        basemap: {
          type: "raster",
          tiles: expandTileSubdomains(tileUrl),
          tileSize: 256,
          attribution: options.tileAttribution?.trim() || "",
        },
      },
      layers: [{ id: "basemap", type: "raster", source: "basemap" }],
    };
  }
  return options.mapStyleUrl?.trim() || DEFAULT_MAP_STYLE_URL;
}
