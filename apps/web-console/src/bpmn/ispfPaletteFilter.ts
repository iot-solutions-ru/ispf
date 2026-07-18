/**
 * ADR-0047: keep bpmn-js palette aligned with the ISPF BPMN subset.
 * Returns middleware for palette.registerProvider (receives existing entries).
 */
export const ISPF_PALETTE_REMOVE = [
  "create.participant-expanded",
  "create.data-object",
  "create.data-store",
  "create.group",
  // Generic bpmn:Task is not executed by the ISPF engine (serviceTask / userTask only).
  "create.task",
] as const;

export type PaletteEntries = Record<string, unknown>;

export function filterIspfPaletteEntries(entries: PaletteEntries): PaletteEntries {
  const next = { ...entries };
  for (const key of ISPF_PALETTE_REMOVE) {
    delete next[key];
  }
  return next;
}

export function createIspfPaletteFilterProvider() {
  return {
    __init__: ["ispfPaletteFilter"],
    ispfPaletteFilter: [
      "type",
      class IspfPaletteFilter {
        static $inject = ["palette"];

        constructor(palette: {
          registerProvider: (priority: number, provider: { getPaletteEntries: () => (e: PaletteEntries) => PaletteEntries }) => void;
        }) {
          palette.registerProvider(500, {
            getPaletteEntries: () => (entries: PaletteEntries) => filterIspfPaletteEntries(entries),
          });
        }
      },
    ],
  };
}
