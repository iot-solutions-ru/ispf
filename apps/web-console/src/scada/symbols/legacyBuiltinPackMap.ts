import type { MimicPort } from "../../types/scadaMimic";
import type { PackSymbolRecord } from "./symbolPackLoader";
import electrical from "./packs/ispf-pid-v1/electrical.json";
import isa from "./packs/ispf-pid-v1/isa.json";
import misc from "./packs/ispf-pid-v1/misc.json";
import pipes from "./packs/ispf-pid-v1/pipes.json";
import pumps from "./packs/ispf-pid-v1/pumps.json";
import sensors from "./packs/ispf-pid-v1/sensors.json";
import tanks from "./packs/ispf-pid-v1/tanks.json";
import valves from "./packs/ispf-pid-v1/valves.json";

/** Maps removed React builtin ids → ispf-pid-v1 pack symbols (SVG). */
export const LEGACY_BUILTIN_TO_PACK: Record<string, string> = {
  "tank.vertical": "pack.ispf-pid.vertical-tank",
  "tank.horizontal": "pack.ispf-pid.horizontal-tank",
  "tank.spherical": "pack.ispf-pid.spherical-tank",
  "valve.gate": "pack.ispf-pid.gate-valve",
  "valve.butterfly": "pack.ispf-pid.butterfly-valve",
  "valve.ball": "pack.ispf-pid.ball-valve",
  "valve.globe": "pack.ispf-pid.globe-valve",
  "valve.check": "pack.ispf-pid.check-valve",
  "valve.control": "pack.ispf-pid.control-valve",
  "valve.safety": "pack.ispf-pid.safety-relief-valve",
  "valve.needle": "pack.ispf-pid.needle-valve",
  "valve.diaphragm": "pack.ispf-pid.diaphragm-valve",
  "pump.centrifugal": "pack.ispf-pid.centrifugal-pump",
  "pump.positive": "pack.ispf-pid.positive-displacement-pump",
  "pipe.segment": "pack.ispf-pid.pipe-segment-h",
  "pipe.junction": "pack.ispf-pid.cross",
  "pipe.tee": "pack.ispf-pid.tee",
  "pipe.elbow": "pack.ispf-pid.elbow-90",
  "pipe.reducer": "pack.ispf-pid.reducer",
};

const packById = new Map<string, PackSymbolRecord>();
for (const chunk of [valves, pumps, tanks, pipes, sensors, electrical, isa, misc] as PackSymbolRecord[][]) {
  for (const rec of chunk) {
    packById.set(rec.id, rec);
  }
}

export interface LegacyPackSvg {
  name: string;
  svg: string;
  viewBox: string;
  width: number;
  height: number;
  ports: MimicPort[];
}

export function getPackRecord(id: string): PackSymbolRecord | undefined {
  return packById.get(id);
}

export function getPackRecordForLegacyBuiltin(builtinId: string): PackSymbolRecord | undefined {
  const packId = LEGACY_BUILTIN_TO_PACK[builtinId];
  return packId ? packById.get(packId) : undefined;
}

export function legacyPackSvg(builtinId: string): LegacyPackSvg | undefined {
  const rec = getPackRecordForLegacyBuiltin(builtinId);
  if (!rec) return undefined;
  return {
    name: rec.nameRu || rec.nameEn,
    svg: rec.svg,
    viewBox: rec.viewBox,
    width: rec.defaultWidth,
    height: rec.defaultHeight,
    ports: rec.ports,
  };
}
