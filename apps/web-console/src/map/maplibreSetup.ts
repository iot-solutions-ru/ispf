/**
 * MapLibre GL JS v6 is ESM-only; Vite must provide a self-contained worker URL.
 * Import this module once before creating any Map (see MapWidgetView).
 * @see https://maplibre.org/maplibre-gl-js/docs/#installation
 */
import { setWorkerUrl } from "maplibre-gl";
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url";

setWorkerUrl(workerUrl);
