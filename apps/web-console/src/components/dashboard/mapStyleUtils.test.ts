import { describe, expect, it } from "vitest";
import {
  DEFAULT_TILE_ATTRIBUTION,
  DEFAULT_TILE_URL,
  resolveMapStyle,
} from "./mapStyleUtils";

describe("resolveMapStyle", () => {
  it("builds default OSM raster style", () => {
    const style = resolveMapStyle({});
    expect(style).toMatchObject({
      version: 8,
      sources: {
        basemap: {
          type: "raster",
          tileSize: 256,
          attribution: DEFAULT_TILE_ATTRIBUTION,
        },
      },
      layers: [{ id: "basemap", type: "raster", source: "basemap" }],
    });
    if (typeof style === "string") {
      throw new Error("expected style object");
    }
    expect(style.sources.basemap).toMatchObject({ type: "raster" });
    const tiles =
      style.sources.basemap.type === "raster" ? style.sources.basemap.tiles : undefined;
    expect(tiles).toEqual([
      "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
      "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png",
      "https://c.tile.openstreetmap.org/{z}/{x}/{y}.png",
    ]);
    expect(DEFAULT_TILE_URL).toContain("{s}");
  });

  it("prefers custom tileUrl over mapStyleUrl", () => {
    const style = resolveMapStyle({
      tileUrl: "https://tiles.example/{z}/{x}/{y}.png",
      tileAttribution: "Example",
      mapStyleUrl: "https://example.com/style.json",
    });
    expect(style).toMatchObject({
      sources: {
        basemap: {
          type: "raster",
          tiles: ["https://tiles.example/{z}/{x}/{y}.png"],
          attribution: "Example",
        },
      },
    });
  });

  it("returns mapStyleUrl when no tileUrl", () => {
    expect(resolveMapStyle({ mapStyleUrl: "https://example.com/style.json" })).toBe(
      "https://example.com/style.json",
    );
  });
});
