/** BL-146 Wave 4 expansion — 70+ additional ISA/ISO P&ID symbols. */
import type { IsaSymbolDef } from "./types.js";
import {
  bubble,
  circle,
  instrumentBubble,
  line,
  path,
  pipeHorizontal,
  pipeVertical,
  poly,
  portsCross,
  portsHorizontal,
  portsVertical,
  rect,
  text,
  wrap,
} from "./svg.js";

const VB = "0 0 64 64";
const SZ = 64;

function sym(partial: Omit<IsaSymbolDef, "viewBox" | "defaultWidth" | "defaultHeight"> & Partial<Pick<IsaSymbolDef, "viewBox">>): IsaSymbolDef {
  return {
    viewBox: VB,
    defaultWidth: SZ,
    defaultHeight: SZ,
    tags: [],
    ...partial,
  };
}

function instSensor(label: string, slug: string, nameEn: string, nameRu: string): IsaSymbolDef {
  return sym({
    slug,
    category: "pack-sensors",
    nameEn,
    nameRu,
    tags: ["instrument", "isa"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 14, label)}${line(32, 42, 32, 54)}`),
  });
}

function instIsa(label: string, slug: string, nameEn: string, nameRu: string): IsaSymbolDef {
  return sym({
    slug,
    category: "pack-isa",
    nameEn,
    nameRu,
    tags: ["isa", "function"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(instrumentBubble(label)),
  });
}

function motorOperatedValve(): IsaSymbolDef {
  return sym({
    slug: "motor-operated-valve",
    category: "pack-valves",
    nameEn: "Motor-operated valve",
    nameRu: "Клапан с электроприводом",
    tags: ["valve", "motor", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("24,32 32,24 32,40")}${poly("40,32 32,24 32,40")}${rect(28, 8, 8, 12)}${line(32, 20, 32, 24)}`),
  });
}

function pressureReducingValve(): IsaSymbolDef {
  return sym({
    slug: "pressure-reducing-valve",
    category: "pack-valves",
    nameEn: "Pressure reducing valve",
    nameRu: "Редукционный клапан",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("26,32 32,26 38,32 32,38")}${line(32, 12, 32, 26)}${text(32, 10, "PRV", 8)}`),
  });
}

function ventValve(): IsaSymbolDef {
  return sym({
    slug: "vent-valve",
    category: "pack-valves",
    nameEn: "Vent valve",
    nameRu: "Вентиляционный клапан",
    tags: ["valve", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${pipeVertical()}${poly("26,32 32,26 38,32 32,38")}${line(32, 8, 32, 26)}`),
  });
}

function samplingValve(): IsaSymbolDef {
  return sym({
    slug: "sampling-valve",
    category: "pack-valves",
    nameEn: "Sampling valve",
    nameRu: "Пробоотборный клапан",
    tags: ["valve", "isa"],
    ports: [{ id: "w", x: 0, y: 32 }, { id: "n", x: 32, y: 0 }],
    svg: wrap(`${line(0, 32, 24, 32)}${circle(32, 32, 8)}${line(32, 24, 32, 0)}`),
  });
}

function doubleBlockBleed(): IsaSymbolDef {
  return sym({
    slug: "double-block-bleed",
    category: "pack-valves",
    nameEn: "Double block & bleed",
    nameRu: "Двойная отсечка с дренажом",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("20,32 26,26 26,38")}${poly("44,32 38,26 38,38")}${line(32, 32, 32, 48)}${poly("28,48 32,54 36,48")}`),
  });
}

function throttleValve(): IsaSymbolDef {
  return sym({
    slug: "throttle-valve",
    category: "pack-valves",
    nameEn: "Throttle valve",
    nameRu: "Дроссельный клапан",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("26,32 32,22 38,32 32,42")}${line(32, 12, 32, 22)}`),
  });
}

function bellowsSealedValve(): IsaSymbolDef {
  return sym({
    slug: "bellows-sealed-valve",
    category: "pack-valves",
    nameEn: "Bellows sealed valve",
    nameRu: "Клапан с сильфоном",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("26,32 32,26 38,32 32,38")}${path("M26 14 Q32 8 38 14 Q32 20 26 14", "none")}`),
  });
}

function flushValve(): IsaSymbolDef {
  return sym({
    slug: "flush-valve",
    category: "pack-valves",
    nameEn: "Flush valve",
    nameRu: "Промывочный клапан",
    tags: ["valve", "isa"],
    ports: [{ id: "w", x: 0, y: 32 }, { id: "s", x: 32, y: 64 }],
    svg: wrap(`${line(0, 32, 28, 32)}${poly("28,32 36,26 36,38")}${line(32, 38, 32, 64)}`),
  });
}

function excessFlowValve(): IsaSymbolDef {
  return sym({
    slug: "excess-flow-valve",
    category: "pack-valves",
    nameEn: "Excess flow valve",
    nameRu: "Клапан избыточного расхода",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("26,32 38,26 38,38")}${line(38, 20, 38, 44)}${text(38, 18, "EF", 7)}`),
  });
}

function rotaryPlugValve(): IsaSymbolDef {
  return sym({
    slug: "rotary-plug-valve",
    category: "pack-valves",
    nameEn: "Rotary plug valve",
    nameRu: "Пробковый клапан",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(26, 26, 12, 12, 2)}${line(32, 14, 32, 26)}`),
  });
}

function cannedMotorPump(): IsaSymbolDef {
  return sym({
    slug: "canned-motor-pump",
    category: "pack-pumps",
    nameEn: "Canned motor pump",
    nameRu: "Насос с герметичным двигателем",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${circle(32, 32, 14)}${rect(28, 8, 8, 10)}${text(32, 33, "CMP", 8)}`),
  });
}

function turbinePump(): IsaSymbolDef {
  return sym({
    slug: "turbine-pump",
    category: "pack-pumps",
    nameEn: "Turbine pump",
    nameRu: "Турбинный насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${circle(32, 32, 14)}${poly("32,18 38,32 32,46 26,32")}`),
  });
}

function peristalticPump(): IsaSymbolDef {
  return sym({
    slug: "peristaltic-pump",
    category: "pack-pumps",
    nameEn: "Peristaltic pump",
    nameRu: "Перистальтический насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${circle(32, 32, 16, "none")}${path("M20 32 Q32 20 44 32 Q32 44 20 32", "none")}`),
  });
}

function handPump(): IsaSymbolDef {
  return sym({
    slug: "hand-pump",
    category: "pack-pumps",
    nameEn: "Hand pump",
    nameRu: "Ручной насос",
    tags: ["pump", "isa"],
    ports: portsVertical(),
    svg: wrap(`${line(32, 0, 32, 20)}${rect(24, 20, 16, 24)}${line(32, 44, 32, 64)}${line(24, 28, 40, 28)}`),
  });
}

function boosterPump(): IsaSymbolDef {
  return sym({
    slug: "booster-pump",
    category: "pack-pumps",
    nameEn: "Booster pump",
    nameRu: "Дозирующий насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${circle(32, 32, 12)}${text(32, 33, "BP", 9)}`),
  });
}

function floatingRoofTank(): IsaSymbolDef {
  return sym({
    slug: "floating-roof-tank",
    category: "pack-tanks",
    nameEn: "Floating roof tank",
    nameRu: "Резервуар с плавающей крышей",
    tags: ["tank", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(16, 16, 32, 40, 2)}${line(16, 28, 48, 28)}${line(32, 16, 32, 0)}`),
  });
}

function pressurizedTank(): IsaSymbolDef {
  return sym({
    slug: "pressurized-tank",
    category: "pack-tanks",
    nameEn: "Pressurized tank",
    nameRu: "Резервуар под давлением",
    tags: ["tank", "isa"],
    ports: portsCross(),
    svg: wrap(`${rect(18, 14, 28, 36, 4)}${line(18, 14, 32, 4)}${line(46, 14, 32, 4)}`),
  });
}

function bufferTank(): IsaSymbolDef {
  return sym({
    slug: "buffer-tank",
    category: "pack-tanks",
    nameEn: "Buffer tank",
    nameRu: "Буферный бак",
    tags: ["tank", "isa"],
    ports: [{ id: "w", x: 0, y: 40 }, { id: "e", x: 64, y: 40 }],
    svg: wrap(`${rect(20, 20, 24, 32, 3)}${line(0, 40, 20, 40)}${line(44, 40, 64, 40)}`),
  });
}

function storageTankV(): IsaSymbolDef {
  return sym({
    slug: "storage-tank-v",
    category: "pack-tanks",
    nameEn: "Storage tank (vertical)",
    nameRu: "Накопительный резервуар",
    tags: ["tank", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(20, 12, 24, 44, 2)}${line(32, 12, 32, 0)}`),
  });
}

function receiverTank(): IsaSymbolDef {
  return sym({
    slug: "receiver-tank",
    category: "pack-tanks",
    nameEn: "Receiver tank",
    nameRu: "Ресивер",
    tags: ["tank", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${rect(14, 24, 36, 24, 12)}${line(0, 36, 14, 36)}${line(50, 36, 64, 36)}`),
  });
}

function processTank(): IsaSymbolDef {
  return sym({
    slug: "process-tank",
    category: "pack-tanks",
    nameEn: "Process tank",
    nameRu: "Процессный резервуар",
    tags: ["tank", "isa"],
    ports: portsCross(),
    svg: wrap(`${rect(16, 18, 32, 36, 2)}${line(32, 18, 32, 8)}`),
  });
}

function batchTank(): IsaSymbolDef {
  return sym({
    slug: "batch-tank",
    category: "pack-tanks",
    nameEn: "Batch tank",
    nameRu: "Периодический реактор-бак",
    tags: ["tank", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }, { id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(18, 16, 28, 36, 2)}${line(32, 0, 32, 16)}${line(32, 52, 32, 64)}`),
  });
}

function cryogenicTank(): IsaSymbolDef {
  return sym({
    slug: "cryogenic-tank",
    category: "pack-tanks",
    nameEn: "Cryogenic tank",
    nameRu: "Криогенный резервуар",
    tags: ["tank", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(14, 14, 36, 40, 2)}${rect(18, 18, 28, 32, 1)}${text(32, 36, "CRYO", 8)}`),
  });
}

function wyeTee(): IsaSymbolDef {
  return sym({
    slug: "wye-tee",
    category: "pack-pipes",
    nameEn: "Wye tee",
    nameRu: "Тройник Y-образный",
    tags: ["pipe", "isa"],
    ports: [{ id: "w", x: 0, y: 32 }, { id: "e", x: 64, y: 32 }, { id: "s", x: 32, y: 64 }],
    svg: wrap(`${line(0, 32, 32, 32)}${line(32, 32, 64, 32)}${line(32, 32, 32, 64)}`),
  });
}

function lateralTee(): IsaSymbolDef {
  return sym({
    slug: "lateral-tee",
    category: "pack-pipes",
    nameEn: "Lateral tee",
    nameRu: "Боковой тройник",
    tags: ["pipe", "isa"],
    ports: [{ id: "w", x: 0, y: 40 }, { id: "e", x: 64, y: 40 }, { id: "n", x: 40, y: 0 }],
    svg: wrap(`${line(0, 40, 40, 40)}${line(40, 40, 64, 40)}${line(40, 40, 40, 0)}`),
  });
}

function returnBend(): IsaSymbolDef {
  return sym({
    slug: "return-bend",
    category: "pack-pipes",
    nameEn: "Return bend",
    nameRu: "Обратный изгиб",
    tags: ["pipe", "isa"],
    ports: [{ id: "n", x: 20, y: 0 }, { id: "s", x: 44, y: 64 }],
    svg: wrap(`${path("M20 0 L20 32 Q32 48 44 32 L44 64", "none")}`),
  });
}

function hoseConnection(): IsaSymbolDef {
  return sym({
    slug: "hose-connection",
    category: "pack-pipes",
    nameEn: "Hose connection",
    nameRu: "Шланговое соединение",
    tags: ["pipe", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 20, 32)}${path("M20 26 Q32 20 44 26 Q32 38 20 38 Q32 44 44 38", "none")}${line(44, 32, 64, 32)}`),
  });
}

function samplePoint(): IsaSymbolDef {
  return sym({
    slug: "sample-point",
    category: "pack-pipes",
    nameEn: "Sample point",
    nameRu: "Точка отбора проб",
    tags: ["pipe", "isa"],
    ports: [{ id: "w", x: 0, y: 32 }, { id: "n", x: 32, y: 0 }],
    svg: wrap(`${line(0, 32, 28, 32)}${circle(32, 32, 6)}${line(32, 26, 32, 0)}`),
  });
}

function spectacleBlind(): IsaSymbolDef {
  return sym({
    slug: "spectacle-blind",
    category: "pack-pipes",
    nameEn: "Spectacle blind",
    nameRu: "Очковая заглушка",
    tags: ["pipe", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${circle(26, 32, 8)}${circle(38, 32, 8, "none")}${line(26, 32, 38, 32)}`),
  });
}

function blindFlange(): IsaSymbolDef {
  return sym({
    slug: "blind-flange",
    category: "pack-pipes",
    nameEn: "Blind flange",
    nameRu: "Глухая заглушка",
    tags: ["pipe", "isa"],
    ports: [{ id: "w", x: 0, y: 32 }],
    svg: wrap(`${line(0, 32, 40, 32)}${line(40, 22, 40, 42)}`),
  });
}

function spacerFlange(): IsaSymbolDef {
  return sym({
    slug: "spacer-flange",
    category: "pack-pipes",
    nameEn: "Spacer / spade",
    nameRu: "Прокладочная шайба",
    tags: ["pipe", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 24, 32)}${rect(24, 24, 16, 16)}${line(40, 32, 64, 32)}`),
  });
}

function pipePlugInline(): IsaSymbolDef {
  return sym({
    slug: "pipe-plug-inline",
    category: "pack-pipes",
    nameEn: "Pipe plug",
    nameRu: "Пробка трубопровода",
    tags: ["pipe", "isa"],
    ports: [{ id: "w", x: 0, y: 32 }],
    svg: wrap(`${line(0, 32, 36, 32)}${poly("36,32 48,24 48,40")}`),
  });
}

function pipeSupport(): IsaSymbolDef {
  return sym({
    slug: "pipe-support",
    category: "pack-pipes",
    nameEn: "Pipe support",
    nameRu: "Опора трубопровода",
    tags: ["pipe", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }],
    svg: wrap(`${line(32, 0, 32, 28)}${poly("20,28 44,28 32,48")}`),
  });
}

function pipeAnchor(): IsaSymbolDef {
  return sym({
    slug: "pipe-anchor",
    category: "pack-pipes",
    nameEn: "Pipe anchor",
    nameRu: "Неподвижная опора",
    tags: ["pipe", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }, { id: "s", x: 32, y: 64 }],
    svg: wrap(`${line(32, 0, 32, 24)}${rect(22, 24, 20, 8)}${line(32, 32, 32, 64)}`),
  });
}

function steamJacket(): IsaSymbolDef {
  return sym({
    slug: "steam-jacket",
    category: "pack-pipes",
    nameEn: "Steam jacket",
    nameRu: "Паровая рубашка",
    tags: ["pipe", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${rect(12, 24, 40, 16, 2)}${line(0, 32, 12, 32)}${line(52, 32, 64, 32)}${line(32, 16, 32, 24)}`),
  });
}

function staticMixer(): IsaSymbolDef {
  return sym({
    slug: "static-mixer",
    category: "pack-pipes",
    nameEn: "Static mixer",
    nameRu: "Статический смеситель",
    tags: ["pipe", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(22, 24, 20, 16, 2)}${line(26, 28, 38, 36)}${line(26, 36, 38, 28)}`),
  });
}

function funnel(): IsaSymbolDef {
  return sym({
    slug: "funnel",
    category: "pack-pipes",
    nameEn: "Funnel",
    nameRu: "Воронка",
    tags: ["pipe", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }, { id: "s", x: 32, y: 64 }],
    svg: wrap(`${poly("16,8 48,8 36,40 28,40")}${line(32, 40, 32, 64)}`),
  });
}

function vfd(): IsaSymbolDef {
  return sym({
    slug: "vfd",
    category: "pack-electrical",
    nameEn: "Variable frequency drive",
    nameRu: "Частотный преобразователь",
    tags: ["electrical", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }, { id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(18, 18, 28, 28, 2)}${text(32, 33, "VFD", 9)}`),
  });
}

function plc(): IsaSymbolDef {
  return sym({
    slug: "plc",
    category: "pack-electrical",
    nameEn: "PLC",
    nameRu: "ПЛК",
    tags: ["electrical", "isa"],
    ports: [{ id: "w", x: 0, y: 32 }, { id: "e", x: 64, y: 32 }],
    svg: wrap(`${rect(16, 16, 32, 32, 2)}${text(32, 33, "PLC", 10)}`),
  });
}

function transformer3w(): IsaSymbolDef {
  return sym({
    slug: "transformer-3w",
    category: "pack-electrical",
    nameEn: "Transformer (3-winding)",
    nameRu: "Трансформатор (3 обм.)",
    tags: ["electrical", "isa"],
    ports: [
      { id: "n", x: 20, y: 0 },
      { id: "e", x: 64, y: 40 },
      { id: "s", x: 44, y: 64 },
    ],
    svg: wrap(`${circle(20, 18, 10)}${circle(44, 46, 10)}${circle(54, 28, 8)}`),
  });
}

function softStarter(): IsaSymbolDef {
  return sym({
    slug: "soft-starter",
    category: "pack-electrical",
    nameEn: "Soft starter",
    nameRu: "Устройство плавного пуска",
    tags: ["electrical", "isa"],
    ports: portsVertical(),
    svg: wrap(`${line(32, 0, 32, 18)}${rect(20, 18, 24, 20, 2)}${text(32, 29, "SS", 9)}${line(32, 38, 32, 64)}`),
  });
}

function overloadRelay(): IsaSymbolDef {
  return sym({
    slug: "overload-relay",
    category: "pack-electrical",
    nameEn: "Overload relay",
    nameRu: "Реле перегрузки",
    tags: ["electrical", "isa"],
    ports: portsVertical(),
    svg: wrap(`${line(32, 0, 32, 20)}${rect(24, 20, 16, 16)}${text(32, 29, "OL", 8)}${line(32, 36, 32, 64)}`),
  });
}

function powerMeter(): IsaSymbolDef {
  return sym({
    slug: "power-meter",
    category: "pack-electrical",
    nameEn: "Power meter",
    nameRu: "Счётчик мощности",
    tags: ["electrical", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${rect(18, 20, 28, 24, 2)}${text(32, 33, "kW", 9)}`),
  });
}

function battery(): IsaSymbolDef {
  return sym({
    slug: "battery",
    category: "pack-electrical",
    nameEn: "Battery",
    nameRu: "Аккумулятор",
    tags: ["electrical", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }, { id: "s", x: 32, y: 64 }],
    svg: wrap(`${line(24, 20, 40, 20)}${line(24, 28, 40, 28)}${rect(26, 28, 12, 24)}`),
  });
}

function solarPanel(): IsaSymbolDef {
  return sym({
    slug: "solar-panel",
    category: "pack-electrical",
    nameEn: "Solar panel",
    nameRu: "Солнечная панель",
    tags: ["electrical", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(12, 16, 40, 28, 1)}${line(12, 26, 52, 26)}${line(12, 36, 52, 36)}${line(32, 16, 32, 44)}${line(32, 44, 32, 54)}`),
  });
}

function shellTubeHx(): IsaSymbolDef {
  return sym({
    slug: "shell-tube-hx",
    category: "pack-misc",
    nameEn: "Shell & tube HX",
    nameRu: "Кожухотрубный теплообменник",
    tags: ["equipment", "isa"],
    ports: [
      { id: "w", x: 0, y: 24 },
      { id: "e", x: 64, y: 24 },
      { id: "w2", x: 0, y: 40 },
      { id: "e2", x: 64, y: 40 },
    ],
    svg: wrap(`${rect(10, 18, 44, 28, 2)}${line(14, 24, 50, 24)}${line(14, 40, 50, 40)}`),
  });
}

function plateHx(): IsaSymbolDef {
  return sym({
    slug: "plate-hx",
    category: "pack-misc",
    nameEn: "Plate heat exchanger",
    nameRu: "Пластинчатый теплообменник",
    tags: ["equipment", "isa"],
    ports: [
      { id: "n", x: 20, y: 0 },
      { id: "s", x: 20, y: 64 },
      { id: "n2", x: 44, y: 0 },
      { id: "s2", x: 44, y: 64 },
    ],
    svg: wrap(`${rect(14, 12, 36, 40, 1)}${line(20, 12, 20, 52)}${line(28, 12, 28, 52)}${line(36, 12, 36, 52)}${line(44, 12, 44, 52)}`),
  });
}

function kiln(): IsaSymbolDef {
  return sym({
    slug: "kiln",
    category: "pack-misc",
    nameEn: "Kiln",
    nameRu: "Печь",
    tags: ["equipment", "isa"],
    ports: [{ id: "w", x: 0, y: 32 }, { id: "e", x: 64, y: 32 }],
    svg: wrap(`${rect(12, 20, 40, 24, 4)}${line(0, 32, 12, 32)}${line(52, 32, 64, 32)}`),
  });
}

function extruder(): IsaSymbolDef {
  return sym({
    slug: "extruder",
    category: "pack-misc",
    nameEn: "Extruder",
    nameRu: "Экструдер",
    tags: ["equipment", "isa"],
    ports: [{ id: "w", x: 0, y: 32 }, { id: "e", x: 64, y: 32 }],
    svg: wrap(`${rect(14, 24, 36, 16, 2)}${poly("50,24 58,28 58,36")}${line(0, 32, 14, 32)}`),
  });
}

function filterPress(): IsaSymbolDef {
  return sym({
    slug: "filter-press",
    category: "pack-misc",
    nameEn: "Filter press",
    nameRu: "Фильтр-пресс",
    tags: ["equipment", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${rect(16, 20, 32, 24, 1)}${line(20, 24, 20, 40)}${line(28, 24, 28, 40)}${line(36, 24, 36, 40)}${line(44, 24, 44, 40)}`),
  });
}

function ruptureDisk(): IsaSymbolDef {
  return sym({
    slug: "rupture-disk",
    category: "pack-misc",
    nameEn: "Rupture disk",
    nameRu: "Разрывная мембрана",
    tags: ["equipment", "safety", "isa"],
    ports: portsVertical(),
    svg: wrap(`${line(32, 0, 32, 22)}${poly("22,22 42,22 32,42")}${line(32, 42, 32, 64)}`),
  });
}

function flameArrestor(): IsaSymbolDef {
  return sym({
    slug: "flame-arrestor",
    category: "pack-misc",
    nameEn: "Flame arrestor",
    nameRu: "Пламегаситель",
    tags: ["equipment", "safety", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(24, 24, 16, 16, 2)}${line(28, 28, 36, 36)}${line(28, 36, 36, 28)}`),
  });
}

function elevator(): IsaSymbolDef {
  return sym({
    slug: "elevator",
    category: "pack-misc",
    nameEn: "Elevator",
    nameRu: "Элеватор",
    tags: ["equipment", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }, { id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(24, 12, 16, 40)}${poly("32,8 24,16 40,16")}${line(32, 52, 32, 64)}`),
  });
}

function granulator(): IsaSymbolDef {
  return sym({
    slug: "granulator",
    category: "pack-misc",
    nameEn: "Granulator",
    nameRu: "Гранулятор",
    tags: ["equipment", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }, { id: "s", x: 32, y: 64 }],
    svg: wrap(`${circle(32, 32, 16)}${line(32, 0, 32, 16)}${line(32, 48, 32, 64)}${line(24, 32, 40, 32)}`),
  });
}

function silencer(): IsaSymbolDef {
  return sym({
    slug: "silencer",
    category: "pack-misc",
    nameEn: "Silencer",
    nameRu: "Глушитель",
    tags: ["equipment", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(22, 24, 20, 16, 2)}${line(26, 28, 38, 36)}${line(26, 36, 38, 28)}`),
  });
}

function packedColumn(): IsaSymbolDef {
  return sym({
    slug: "packed-column",
    category: "pack-misc",
    nameEn: "Packed column",
    nameRu: "Насадочная колонна",
    tags: ["equipment", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }, { id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(22, 10, 20, 44, 2)}${line(26, 18, 38, 18)}${line(26, 28, 38, 28)}${line(26, 38, 38, 38)}`),
  });
}

function manualLoader(): IsaSymbolDef {
  return instIsa("ML", "manual-loader", "Manual loader (ML)", "Ручной загрузчик (ML)");
}

function functionBlock(): IsaSymbolDef {
  return instIsa("FB", "function-block", "Function block (FB)", "Функциональный блок (FB)");
}

function programmableController(): IsaSymbolDef {
  return instIsa("PC", "programmable-controller", "Programmable controller (PC)", "Программируемый контроллер (PC)");
}

function ratioStation(): IsaSymbolDef {
  return instIsa("RS", "ratio-station", "Ratio station (RS)", "Станция соотношения (RS)");
}

function splitRange(): IsaSymbolDef {
  return instIsa("SR", "split-range", "Split range (SR)", "Разделение диапазона (SR)");
}

function signalConditioner(): IsaSymbolDef {
  return instIsa("SC", "signal-conditioner", "Signal conditioner (SC)", "Преобразователь сигнала (SC)");
}

function indicatorController(): IsaSymbolDef {
  return instIsa("IC", "indicator-controller", "Indicator controller (IC)", "Индикатор-регулятор (IC)");
}

function transmitterController(): IsaSymbolDef {
  return instIsa("TC", "transmitter-controller", "Transmitter controller (TC)", "Датчик-регулятор (TC)");
}

const WAVE4_INSTRUMENTS: IsaSymbolDef[] = [
  instSensor("PIC", "pressure-indicator-controller", "Pressure indicator controller (PIC)", "Регулятор давления (PIC)"),
  instSensor("TIC", "temperature-indicator-controller", "Temperature indicator controller (TIC)", "Регулятор температуры (TIC)"),
  instSensor("LIC", "level-indicator-controller", "Level indicator controller (LIC)", "Регулятор уровня (LIC)"),
  instSensor("FIC", "flow-indicator-controller", "Flow indicator controller (FIC)", "Регулятор расхода (FIC)"),
  instSensor("HV", "hand-valve-instrument", "Hand valve (HV)", "Ручной клапан (HV)"),
  instSensor("ZT", "position-transmitter-isa", "Position transmitter (ZT)", "Датчик положения (ZT)"),
  instSensor("WT", "weight-transmitter", "Weight transmitter (WT)", "Датчик веса (WT)"),
  instSensor("DT", "density-transmitter", "Density transmitter (DT)", "Датчик плотности (DT)"),
  instSensor("PI", "pressure-indicator-isa", "Pressure indicator (PI)", "Индикатор давления (PI)"),
  instSensor("TI", "temperature-indicator-isa", "Temperature indicator (TI)", "Индикатор температуры (TI)"),
  instSensor("FI", "flow-indicator-isa", "Flow indicator (FI)", "Индикатор расхода (FI)"),
  instSensor("LI", "level-indicator-isa", "Level indicator (LI)", "Индикатор уровня (LI)"),
  instSensor("AI", "analyzer-indicator", "Analyzer indicator (AI)", "Индикатор анализатора (AI)"),
  instSensor("SI", "speed-indicator-isa", "Speed indicator (SI)", "Индикатор скорости (SI)"),
  instSensor("ZI", "position-indicator", "Position indicator (ZI)", "Индикатор положения (ZI)"),
  instSensor("WI", "weight-indicator-isa", "Weight indicator (WI)", "Индикатор веса (WI)"),
  instSensor("DI", "density-indicator", "Density indicator (DI)", "Индикатор плотности (DI)"),
  instSensor("XI", "unclassified-indicator", "Unclassified indicator (XI)", "Индикатор (XI)"),
  instSensor("RI", "radiation-indicator", "Radiation indicator (RI)", "Индикатор радиации (RI)"),
  instSensor("CI", "conductivity-indicator-isa", "Conductivity indicator (CI)", "Индикатор проводимости (CI)"),
];

export const WAVE4_ISA_SYMBOLS: IsaSymbolDef[] = [
  motorOperatedValve(),
  pressureReducingValve(),
  ventValve(),
  samplingValve(),
  doubleBlockBleed(),
  throttleValve(),
  bellowsSealedValve(),
  flushValve(),
  excessFlowValve(),
  rotaryPlugValve(),
  cannedMotorPump(),
  turbinePump(),
  peristalticPump(),
  handPump(),
  boosterPump(),
  floatingRoofTank(),
  pressurizedTank(),
  bufferTank(),
  storageTankV(),
  receiverTank(),
  processTank(),
  batchTank(),
  cryogenicTank(),
  wyeTee(),
  lateralTee(),
  returnBend(),
  hoseConnection(),
  samplePoint(),
  spectacleBlind(),
  blindFlange(),
  spacerFlange(),
  pipePlugInline(),
  pipeSupport(),
  pipeAnchor(),
  steamJacket(),
  staticMixer(),
  funnel(),
  ...WAVE4_INSTRUMENTS,
  manualLoader(),
  functionBlock(),
  programmableController(),
  ratioStation(),
  splitRange(),
  signalConditioner(),
  indicatorController(),
  transmitterController(),
  vfd(),
  plc(),
  transformer3w(),
  softStarter(),
  overloadRelay(),
  powerMeter(),
  battery(),
  solarPanel(),
  shellTubeHx(),
  plateHx(),
  kiln(),
  extruder(),
  filterPress(),
  ruptureDisk(),
  flameArrestor(),
  elevator(),
  granulator(),
  silencer(),
  packedColumn(),
];
