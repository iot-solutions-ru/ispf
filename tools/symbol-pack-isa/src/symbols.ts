import type { IsaSymbolDef } from "./types.js";
import { WAVE4_ISA_SYMBOLS } from "./symbols-wave4.js";
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

/** Gate valve — ISA wedge (two triangles on stem). */
function gateValve(): IsaSymbolDef {
  return sym({
    slug: "gate-valve",
    category: "pack-valves",
    nameEn: "Gate valve",
    nameRu: "Задвижка",
    tags: ["valve", "isa", "block"],
    ports: portsHorizontal(),
    svg: wrap(
      `${pipeHorizontal()}${poly("22,32 30,24 30,40")}${poly("42,32 34,24 34,40")}`,
    ),
  });
}

function globeValve(): IsaSymbolDef {
  return sym({
    slug: "globe-valve",
    category: "pack-valves",
    nameEn: "Globe valve",
    nameRu: "Клапан проходной",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("24,32 32,22 32,42")}${poly("40,32 32,22 32,42")}`),
  });
}

function checkValve(): IsaSymbolDef {
  return sym({
    slug: "check-valve",
    category: "pack-valves",
    nameEn: "Check valve",
    nameRu: "Обратный клапан",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("26,32 38,26 38,38")}${line(38, 26, 38, 38)}`),
  });
}

function butterflyValve(): IsaSymbolDef {
  return sym({
    slug: "butterfly-valve",
    category: "pack-valves",
    nameEn: "Butterfly valve",
    nameRu: "Дисковый затвор",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${line(32, 18, 32, 46)}${circle(32, 32, 10)}`),
  });
}

function ballValve(): IsaSymbolDef {
  return sym({
    slug: "ball-valve",
    category: "pack-valves",
    nameEn: "Ball valve",
    nameRu: "Шаровой кран",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${circle(32, 32, 9)}`),
  });
}

function controlValve(): IsaSymbolDef {
  return sym({
    slug: "control-valve",
    category: "pack-valves",
    nameEn: "Control valve",
    nameRu: "Регулирующий клапан",
    tags: ["valve", "control", "isa"],
    ports: portsHorizontal(),
    svg: wrap(
      `${pipeHorizontal()}${poly("26,32 32,26 38,32 32,38")}${line(32, 12, 32, 26)}${poly("32,8 26,14 38,14")}`,
    ),
  });
}

function threeWayValve(): IsaSymbolDef {
  return sym({
    slug: "three-way-valve",
    category: "pack-valves",
    nameEn: "3-way valve",
    nameRu: "Трёхходовой клапан",
    tags: ["valve", "isa"],
    ports: [
      { id: "w", x: 0, y: 32 },
      { id: "e", x: 64, y: 32 },
      { id: "s", x: 32, y: 64 },
    ],
    svg: wrap(
      `${line(0, 32, 64, 32)}${line(32, 32, 32, 64)}${circle(32, 32, 8)}`,
    ),
  });
}

function needleValve(): IsaSymbolDef {
  return sym({
    slug: "needle-valve",
    category: "pack-valves",
    nameEn: "Needle valve",
    nameRu: "Игольчатый клапан",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${line(32, 14, 32, 50)}${poly("32,10 28,18 36,18")}`),
  });
}

function plugValve(): IsaSymbolDef {
  return sym({
    slug: "plug-valve",
    category: "pack-valves",
    nameEn: "Plug valve",
    nameRu: "Пробковый кран",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(26, 26, 12, 12)}`),
  });
}

function diaphragmValve(): IsaSymbolDef {
  return sym({
    slug: "diaphragm-valve",
    category: "pack-valves",
    nameEn: "Diaphragm valve",
    nameRu: "Мембранный клапан",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(
      `${pipeHorizontal()}${path("M26 28 Q32 22 38 28 Q32 36 26 28")}${line(32, 14, 32, 22)}`,
    ),
  });
}

function safetyValve(): IsaSymbolDef {
  return sym({
    slug: "safety-relief-valve",
    category: "pack-valves",
    nameEn: "Safety relief valve",
    nameRu: "Предохранительный клапан",
    tags: ["valve", "safety", "isa"],
    ports: portsHorizontal(),
    svg: wrap(
      `${pipeHorizontal()}${poly("28,32 32,24 36,32 32,38")}${line(32, 8, 32, 24)}${poly("32,4 26,10 38,10")}`,
    ),
  });
}

function solenoidValve(): IsaSymbolDef {
  return sym({
    slug: "solenoid-valve",
    category: "pack-valves",
    nameEn: "Solenoid valve",
    nameRu: "Соленоидный клапан",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(
      `${pipeHorizontal()}${poly("26,32 32,28 38,32 32,36")}${rect(26, 10, 12, 10)}${line(32, 20, 32, 28)}`,
    ),
  });
}

function centrifugalPump(): IsaSymbolDef {
  return sym({
    slug: "centrifugal-pump",
    category: "pack-pumps",
    nameEn: "Centrifugal pump",
    nameRu: "Центробежный насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${circle(32, 32, 16)}${path("M32 32 L48 32", "none")}${poly("48,32 42,28 42,36")}`),
  });
}

function positiveDisplacementPump(): IsaSymbolDef {
  return sym({
    slug: "positive-displacement-pump",
    category: "pack-pumps",
    nameEn: "Positive displacement pump",
    nameRu: "Объёмный насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${circle(32, 32, 14)}${circle(24, 32, 4)}${circle(40, 32, 4)}`),
  });
}

function verticalPump(): IsaSymbolDef {
  return sym({
    slug: "vertical-pump",
    category: "pack-pumps",
    nameEn: "Vertical pump",
    nameRu: "Вертикальный насос",
    tags: ["pump", "isa"],
    ports: portsVertical(),
    svg: wrap(`${circle(32, 32, 14)}${line(32, 46, 32, 58)}${poly("32,58 28,50 36,50")}`),
  });
}

function gearPump(): IsaSymbolDef {
  return sym({
    slug: "gear-pump",
    category: "pack-pumps",
    nameEn: "Gear pump",
    nameRu: "Шестерёнчатый насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${circle(26, 32, 10)}${circle(38, 32, 10)}`),
  });
}

function vacuumPump(): IsaSymbolDef {
  return sym({
    slug: "vacuum-pump",
    category: "pack-pumps",
    nameEn: "Vacuum pump",
    nameRu: "Вакуумный насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${rect(20, 24, 24, 16)}${line(44, 32, 58, 32)}${text(32, 33, "V", 9)}`),
  });
}

function verticalTank(): IsaSymbolDef {
  return sym({
    slug: "vertical-tank",
    category: "pack-tanks",
    nameEn: "Vertical tank",
    nameRu: "Вертикальный резервуар",
    tags: ["tank", "vessel", "isa"],
    ports: [
      { id: "n", x: 32, y: 8 },
      { id: "s", x: 32, y: 56 },
      { id: "w", x: 16, y: 32 },
    ],
    svg: wrap(`${rect(16, 10, 32, 44, 4)}${line(16, 10, 48, 10)}`),
  });
}

function horizontalTank(): IsaSymbolDef {
  return sym({
    slug: "horizontal-tank",
    category: "pack-tanks",
    nameEn: "Horizontal tank",
    nameRu: "Горизонтальный резервуар",
    tags: ["tank", "vessel", "isa"],
    ports: [
      { id: "w", x: 8, y: 32 },
      { id: "e", x: 56, y: 32 },
      { id: "n", x: 32, y: 16 },
    ],
    svg: wrap(`${rect(10, 22, 44, 20, 10)}`),
  });
}

function sphericalTank(): IsaSymbolDef {
  return sym({
    slug: "spherical-tank",
    category: "pack-tanks",
    nameEn: "Spherical tank",
    nameRu: "Сферический резервуар",
    tags: ["tank", "vessel", "isa"],
    ports: portsCross(),
    svg: wrap(`${circle(32, 32, 18)}`),
  });
}

function openTank(): IsaSymbolDef {
  return sym({
    slug: "open-tank",
    category: "pack-tanks",
    nameEn: "Open tank",
    nameRu: "Открытая ёмкость",
    tags: ["tank", "isa"],
    ports: [{ id: "s", x: 32, y: 56 }],
    svg: wrap(`${line(14, 14, 50, 14)}${line(14, 14, 14, 54)}${line(50, 14, 50, 54)}${line(14, 54, 50, 54)}`),
  });
}

function coneRoofTank(): IsaSymbolDef {
  return sym({
    slug: "cone-roof-tank",
    category: "pack-tanks",
    nameEn: "Cone roof tank",
    nameRu: "Резервуар с конусной крышей",
    tags: ["tank", "isa"],
    ports: [{ id: "s", x: 32, y: 58 }],
    svg: wrap(`${poly("32,8 48,18 48,54 16,54 16,18")}${line(16, 54, 48, 54)}`),
  });
}

function drum(): IsaSymbolDef {
  return sym({
    slug: "drum",
    category: "pack-tanks",
    nameEn: "Drum",
    nameRu: "Барабан",
    tags: ["tank", "isa"],
    ports: portsHorizontal(40),
    svg: wrap(`${rect(12, 28, 40, 24, 8)}`),
  });
}

function pipeSegmentH(): IsaSymbolDef {
  return sym({
    slug: "pipe-segment-h",
    category: "pack-pipes",
    nameEn: "Pipe segment (horizontal)",
    nameRu: "Участок трубы (гор.)",
    tags: ["pipe", "isa"],
    ports: portsHorizontal(),
    svg: wrap(pipeHorizontal()),
  });
}

function pipeSegmentV(): IsaSymbolDef {
  return sym({
    slug: "pipe-segment-v",
    category: "pack-pipes",
    nameEn: "Pipe segment (vertical)",
    nameRu: "Участок трубы (верт.)",
    tags: ["pipe", "isa"],
    ports: portsVertical(),
    svg: wrap(pipeVertical()),
  });
}

function elbow90(): IsaSymbolDef {
  return sym({
    slug: "elbow-90",
    category: "pack-pipes",
    nameEn: "Elbow 90°",
    nameRu: "Отвод 90°",
    tags: ["pipe", "fitting", "isa"],
    ports: [
      { id: "w", x: 0, y: 48 },
      { id: "n", x: 48, y: 0 },
    ],
    svg: wrap(`${line(0, 48, 40, 48)}${line(40, 48, 40, 8)}`),
  });
}

function tee(): IsaSymbolDef {
  return sym({
    slug: "tee",
    category: "pack-pipes",
    nameEn: "Tee",
    nameRu: "Тройник",
    tags: ["pipe", "fitting", "isa"],
    ports: portsCross(),
    svg: wrap(`${line(0, 32, 64, 32)}${line(32, 32, 32, 64)}`),
  });
}

function cross(): IsaSymbolDef {
  return sym({
    slug: "cross",
    category: "pack-pipes",
    nameEn: "Cross",
    nameRu: "Крестовина",
    tags: ["pipe", "fitting", "isa"],
    ports: portsCross(),
    svg: wrap(`${line(0, 32, 64, 32)}${line(32, 0, 32, 64)}`),
  });
}

function reducer(): IsaSymbolDef {
  return sym({
    slug: "reducer",
    category: "pack-pipes",
    nameEn: "Reducer",
    nameRu: "Переход",
    tags: ["pipe", "fitting", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${poly("8,28 24,24 24,40")}${poly("40,26 56,32 40,38")}${line(24, 32, 40, 32)}`),
  });
}

function flange(): IsaSymbolDef {
  return sym({
    slug: "flange",
    category: "pack-pipes",
    nameEn: "Flange",
    nameRu: "Фланец",
    tags: ["pipe", "fitting", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 64, 32)}${line(28, 22, 28, 42)}${line(36, 22, 36, 42)}`),
  });
}

function cap(): IsaSymbolDef {
  return sym({
    slug: "pipe-cap",
    category: "pack-pipes",
    nameEn: "Pipe cap",
    nameRu: "Заглушка",
    tags: ["pipe", "fitting", "isa"],
    ports: [{ id: "w", x: 0, y: 32 }],
    svg: wrap(`${line(0, 32, 40, 32)}${path("M40 22 A10 10 0 0 1 40 42 A10 10 0 0 1 40 22 Z")}`),
  });
}

function pressureIndicator(): IsaSymbolDef {
  return sym({
    slug: "pressure-indicator",
    category: "pack-sensors",
    nameEn: "Pressure indicator (PI)",
    nameRu: "Индикатор давления (PI)",
    tags: ["instrument", "pressure", "isa"],
    ports: [{ id: "s", x: 32, y: 48 }],
    svg: wrap(`${instrumentBubble("PI")}${line(32, 46, 32, 58)}`),
  });
}

function temperatureIndicator(): IsaSymbolDef {
  return sym({
    slug: "temperature-indicator",
    category: "pack-sensors",
    nameEn: "Temperature indicator (TI)",
    nameRu: "Индикатор температуры (TI)",
    tags: ["instrument", "temperature", "isa"],
    ports: [{ id: "s", x: 32, y: 48 }],
    svg: wrap(`${instrumentBubble("TI")}${line(32, 46, 32, 58)}`),
  });
}

function flowIndicator(): IsaSymbolDef {
  return sym({
    slug: "flow-indicator",
    category: "pack-sensors",
    nameEn: "Flow indicator (FI)",
    nameRu: "Индикатор расхода (FI)",
    tags: ["instrument", "flow", "isa"],
    ports: portsHorizontal(48),
    svg: wrap(`${instrumentBubble("FI")}${line(32, 46, 32, 48)}${line(0, 48, 64, 48)}`),
  });
}

function levelIndicator(): IsaSymbolDef {
  return sym({
    slug: "level-indicator",
    category: "pack-sensors",
    nameEn: "Level indicator (LI)",
    nameRu: "Индикатор уровня (LI)",
    tags: ["instrument", "level", "isa"],
    ports: [{ id: "e", x: 48, y: 32 }],
    svg: wrap(`${instrumentBubble("LI")}${line(46, 32, 58, 32)}`),
  });
}

function pressureTransmitter(): IsaSymbolDef {
  return sym({
    slug: "pressure-transmitter",
    category: "pack-sensors",
    nameEn: "Pressure transmitter (PT)",
    nameRu: "Датчик давления (PT)",
    tags: ["instrument", "transmitter", "isa"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 14, "PT")}${line(32, 42, 32, 54)}`),
  });
}

function flowTransmitter(): IsaSymbolDef {
  return sym({
    slug: "flow-transmitter",
    category: "pack-sensors",
    nameEn: "Flow transmitter (FT)",
    nameRu: "Датчик расхода (FT)",
    tags: ["instrument", "transmitter", "isa"],
    ports: portsHorizontal(48),
    svg: wrap(`${bubble(32, 28, 14, "FT")}${line(0, 48, 64, 48)}`),
  });
}

function levelTransmitter(): IsaSymbolDef {
  return sym({
    slug: "level-transmitter",
    category: "pack-sensors",
    nameEn: "Level transmitter (LT)",
    nameRu: "Датчик уровня (LT)",
    tags: ["instrument", "transmitter", "isa"],
    ports: [{ id: "e", x: 50, y: 32 }],
    svg: wrap(`${bubble(32, 32, 14, "LT")}${line(46, 32, 58, 32)}`),
  });
}

function analyzerTransmitter(): IsaSymbolDef {
  return sym({
    slug: "analyzer-transmitter",
    category: "pack-sensors",
    nameEn: "Analyzer transmitter (AT)",
    nameRu: "Анализатор (AT)",
    tags: ["instrument", "analyzer", "isa"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 14, "AT")}${line(32, 42, 32, 54)}`),
  });
}

function motor(): IsaSymbolDef {
  return sym({
    slug: "motor",
    category: "pack-electrical",
    nameEn: "Motor",
    nameRu: "Электродвигатель",
    tags: ["electrical", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }],
    svg: wrap(`${circle(32, 36, 16)}${text(32, 37, "M", 12)}`),
  });
}

function generator(): IsaSymbolDef {
  return sym({
    slug: "generator",
    category: "pack-electrical",
    nameEn: "Generator",
    nameRu: "Генератор",
    tags: ["electrical", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${circle(32, 32, 16)}${text(32, 33, "G", 12)}`),
  });
}

function transformer2w(): IsaSymbolDef {
  return sym({
    slug: "transformer-2w",
    category: "pack-electrical",
    nameEn: "Transformer (2-winding)",
    nameRu: "Трансформатор (2 обм.)",
    tags: ["electrical", "isa"],
    ports: [
      { id: "n", x: 20, y: 0 },
      { id: "s", x: 44, y: 64 },
    ],
    svg: wrap(`${circle(20, 22, 12)}${circle(44, 42, 12)}`),
  });
}

function breaker(): IsaSymbolDef {
  return sym({
    slug: "circuit-breaker",
    category: "pack-electrical",
    nameEn: "Circuit breaker",
    nameRu: "Выключатель",
    tags: ["electrical", "isa"],
    ports: portsVertical(),
    svg: wrap(`${line(32, 0, 32, 22)}${rect(22, 22, 20, 20)}${line(32, 42, 32, 64)}`),
  });
}

function disconnect(): IsaSymbolDef {
  return sym({
    slug: "disconnect-switch",
    category: "pack-electrical",
    nameEn: "Disconnect switch",
    nameRu: "Разъединитель",
    tags: ["electrical", "isa"],
    ports: portsVertical(),
    svg: wrap(`${line(32, 0, 32, 24)}${line(32, 24, 44, 36)}${line(32, 40, 32, 64)}`),
  });
}

function fuse(): IsaSymbolDef {
  return sym({
    slug: "fuse",
    category: "pack-electrical",
    nameEn: "Fuse",
    nameRu: "Предохранитель",
    tags: ["electrical", "isa"],
    ports: portsVertical(),
    svg: wrap(`${line(32, 0, 32, 20)}${rect(26, 20, 12, 24)}${line(32, 44, 32, 64)}`),
  });
}

function controller(): IsaSymbolDef {
  return sym({
    slug: "controller",
    category: "pack-isa",
    nameEn: "Controller (IC)",
    nameRu: "Регулятор (IC)",
    tags: ["isa", "function"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "IC")}`),
  });
}

function recorder(): IsaSymbolDef {
  return sym({
    slug: "recorder",
    category: "pack-isa",
    nameEn: "Recorder (RC)",
    nameRu: "Самописец (RC)",
    tags: ["isa", "function"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "RC")}`),
  });
}

function alarm(): IsaSymbolDef {
  return sym({
    slug: "alarm",
    category: "pack-isa",
    nameEn: "Alarm (A)",
    nameRu: "Сигнализация (A)",
    tags: ["isa", "function"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "A")}`),
  });
}

function switchFunc(): IsaSymbolDef {
  return sym({
    slug: "switch-function",
    category: "pack-isa",
    nameEn: "Switch (S)",
    nameRu: "Выключатель (S)",
    tags: ["isa", "function"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "S")}`),
  });
}

function heatExchanger(): IsaSymbolDef {
  return sym({
    slug: "heat-exchanger",
    category: "pack-misc",
    nameEn: "Heat exchanger",
    nameRu: "Теплообменник",
    tags: ["equipment", "isa"],
    ports: [
      { id: "w", x: 0, y: 24 },
      { id: "e", x: 64, y: 24 },
      { id: "w2", x: 0, y: 40 },
      { id: "e2", x: 64, y: 40 },
    ],
    svg: wrap(`${rect(12, 16, 40, 32, 4)}${line(18, 20, 46, 44)}${line(18, 44, 46, 20)}`),
  });
}

function compressor(): IsaSymbolDef {
  return sym({
    slug: "compressor",
    category: "pack-misc",
    nameEn: "Compressor",
    nameRu: "Компрессор",
    tags: ["equipment", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${circle(32, 32, 16)}${text(32, 33, "K", 11)}`),
  });
}

function filter(): IsaSymbolDef {
  return sym({
    slug: "filter",
    category: "pack-misc",
    nameEn: "Filter",
    nameRu: "Фильтр",
    tags: ["equipment", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${rect(20, 22, 24, 20)}${line(24, 26, 40, 38)}${line(24, 38, 40, 26)}`),
  });
}

function mixer(): IsaSymbolDef {
  return sym({
    slug: "mixer",
    category: "pack-misc",
    nameEn: "Mixer",
    nameRu: "Смеситель",
    tags: ["equipment", "isa"],
    ports: portsCross(),
    svg: wrap(`${circle(32, 32, 16)}${line(32, 16, 32, 48)}${line(16, 32, 48, 32)}`),
  });
}

function reactor(): IsaSymbolDef {
  return sym({
    slug: "reactor",
    category: "pack-misc",
    nameEn: "Reactor",
    nameRu: "Реактор",
    tags: ["equipment", "isa"],
    ports: [
      { id: "n", x: 32, y: 8 },
      { id: "s", x: 32, y: 56 },
    ],
    svg: wrap(`${rect(18, 12, 28, 40, 6)}`),
  });
}

function column(): IsaSymbolDef {
  return sym({
    slug: "column",
    category: "pack-misc",
    nameEn: "Distillation column",
    nameRu: "Колонна",
    tags: ["equipment", "isa"],
    ports: [
      { id: "n", x: 32, y: 6 },
      { id: "s", x: 32, y: 58 },
      { id: "e", x: 48, y: 32 },
    ],
    svg: wrap(`${rect(24, 8, 16, 48)}${line(20, 20, 44, 20)}${line(20, 32, 44, 32)}${line(20, 44, 44, 44)}`),
  });
}

function fan(): IsaSymbolDef {
  return sym({
    slug: "fan",
    category: "pack-misc",
    nameEn: "Fan / blower",
    nameRu: "Вентилятор",
    tags: ["equipment", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${circle(32, 32, 14)}${line(32, 32, 48, 32)}${poly("48,32 42,28 42,36")}`),
  });
}

function conveyor(): IsaSymbolDef {
  return sym({
    slug: "conveyor",
    category: "pack-misc",
    nameEn: "Conveyor",
    nameRu: "Конвейер",
    tags: ["equipment", "isa"],
    ports: [
      { id: "w", x: 0, y: 36 },
      { id: "e", x: 64, y: 36 },
    ],
    svg: wrap(`${line(8, 36, 56, 36)}${circle(12, 36, 6)}${circle(52, 36, 6)}`),
  });
}

function angleValve(): IsaSymbolDef {
  return sym({
    slug: "angle-valve",
    category: "pack-valves",
    nameEn: "Angle valve",
    nameRu: "Угловой клапан",
    tags: ["valve", "isa"],
    ports: [
      { id: "w", x: 0, y: 40 },
      { id: "s", x: 40, y: 64 },
    ],
    svg: wrap(`${line(0, 40, 36, 40)}${line(36, 40, 36, 64)}${poly("36,40 44,32 44,48")}`),
  });
}

function pinchValve(): IsaSymbolDef {
  return sym({
    slug: "pinch-valve",
    category: "pack-valves",
    nameEn: "Pinch valve",
    nameRu: "Зажимной клапан",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${line(24, 22, 40, 22)}${line(24, 42, 40, 42)}${line(32, 14, 32, 22)}${line(32, 42, 32, 50)}`),
  });
}

function rotaryValve(): IsaSymbolDef {
  return sym({
    slug: "rotary-valve",
    category: "pack-valves",
    nameEn: "Rotary valve",
    nameRu: "Роторный клапан",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(24, 24, 16, 16)}${line(32, 24, 32, 40)}${line(24, 32, 40, 32)}`),
  });
}

function reciprocatingPump(): IsaSymbolDef {
  return sym({
    slug: "reciprocating-pump",
    category: "pack-pumps",
    nameEn: "Reciprocating pump",
    nameRu: "Поршневой насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${rect(18, 24, 28, 16)}${line(46, 32, 58, 32)}${rect(22, 28, 10, 8)}${line(32, 28, 32, 36)}`),
  });
}

function submersiblePump(): IsaSymbolDef {
  return sym({
    slug: "submersible-pump",
    category: "pack-pumps",
    nameEn: "Submersible pump",
    nameRu: "Погружной насос",
    tags: ["pump", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }],
    svg: wrap(`${circle(32, 38, 14)}${line(32, 24, 32, 10)}${poly("32,10 28,18 36,18")}`),
  });
}

function silo(): IsaSymbolDef {
  return sym({
    slug: "silo",
    category: "pack-tanks",
    nameEn: "Silo",
    nameRu: "Силос",
    tags: ["tank", "vessel", "isa"],
    ports: [
      { id: "n", x: 32, y: 8 },
      { id: "s", x: 32, y: 58 },
    ],
    svg: wrap(`${poly("20,12 44,12 48,54 16,54")}${line(16, 54, 48, 54)}`),
  });
}

function orificePlate(): IsaSymbolDef {
  return sym({
    slug: "orifice-plate",
    category: "pack-pipes",
    nameEn: "Orifice plate",
    nameRu: "Диафрагма (отверстие)",
    tags: ["pipe", "instrument", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${line(30, 20, 30, 44)}${line(34, 20, 34, 44)}`),
  });
}

function agitator(): IsaSymbolDef {
  return sym({
    slug: "agitator",
    category: "pack-misc",
    nameEn: "Agitator",
    nameRu: "Мешалка",
    tags: ["equipment", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }],
    svg: wrap(`${line(32, 8, 32, 48)}${poly("32,48 26,40 38,40")}${line(20, 32, 44, 32)}${line(24, 38, 40, 26)}${line(24, 26, 40, 38)}`),
  });
}

function separator(): IsaSymbolDef {
  return sym({
    slug: "separator",
    category: "pack-misc",
    nameEn: "Separator",
    nameRu: "Сепаратор",
    tags: ["equipment", "isa"],
    ports: [
      { id: "w", x: 0, y: 28 },
      { id: "e", x: 64, y: 28 },
      { id: "s", x: 32, y: 64 },
    ],
    svg: wrap(`${rect(16, 16, 32, 32, 4)}${line(0, 28, 16, 28)}${line(48, 28, 64, 28)}${line(32, 48, 32, 64)}`),
  });
}

function burner(): IsaSymbolDef {
  return sym({
    slug: "burner",
    category: "pack-misc",
    nameEn: "Burner",
    nameRu: "Горелка",
    tags: ["equipment", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(22, 28, 20, 20)}${poly("32,12 26,28 38,28")}${line(28, 48, 32, 58)}${line(36, 48, 32, 58)}`),
  });
}

function damper(): IsaSymbolDef {
  return sym({
    slug: "damper",
    category: "pack-misc",
    nameEn: "Damper",
    nameRu: "Заслонка",
    tags: ["equipment", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${line(32, 18, 32, 46)}${poly("32,18 26,26 38,26")}`),
  });
}

function pressureController(): IsaSymbolDef {
  return sym({
    slug: "pressure-controller",
    category: "pack-isa",
    nameEn: "Pressure controller (PC)",
    nameRu: "Регулятор давления (PC)",
    tags: ["isa", "control"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "PC")}`),
  });
}

function temperatureController(): IsaSymbolDef {
  return sym({
    slug: "temperature-controller",
    category: "pack-isa",
    nameEn: "Temperature controller (TC)",
    nameRu: "Регулятор температуры (TC)",
    tags: ["isa", "control"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "TC")}`),
  });
}

function levelController(): IsaSymbolDef {
  return sym({
    slug: "level-controller",
    category: "pack-isa",
    nameEn: "Level controller (LC)",
    nameRu: "Регулятор уровня (LC)",
    tags: ["isa", "control"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "LC")}`),
  });
}

function flowController(): IsaSymbolDef {
  return sym({
    slug: "flow-controller",
    category: "pack-isa",
    nameEn: "Flow controller (FC)",
    nameRu: "Регулятор расхода (FC)",
    tags: ["isa", "control"],
    ports: portsHorizontal(48),
    svg: wrap(`${bubble(32, 28, 16, "FC")}${line(0, 48, 64, 48)}`),
  });
}

function speedIndicator(): IsaSymbolDef {
  return sym({
    slug: "speed-indicator",
    category: "pack-sensors",
    nameEn: "Speed indicator (SI)",
    nameRu: "Индикатор скорости (SI)",
    tags: ["instrument", "isa"],
    ports: [{ id: "s", x: 32, y: 48 }],
    svg: wrap(`${instrumentBubble("SI")}${line(32, 46, 32, 58)}`),
  });
}

function weightIndicator(): IsaSymbolDef {
  return sym({
    slug: "weight-indicator",
    category: "pack-sensors",
    nameEn: "Weight indicator (WI)",
    nameRu: "Индикатор массы (WI)",
    tags: ["instrument", "isa"],
    ports: [{ id: "s", x: 32, y: 48 }],
    svg: wrap(`${instrumentBubble("WI")}${line(32, 46, 32, 58)}`),
  });
}

function phIndicator(): IsaSymbolDef {
  return sym({
    slug: "ph-indicator",
    category: "pack-sensors",
    nameEn: "pH indicator (pH)",
    nameRu: "Индикатор pH (pH)",
    tags: ["instrument", "analyzer", "isa"],
    ports: [{ id: "s", x: 32, y: 48 }],
    svg: wrap(`${instrumentBubble("pH")}${line(32, 46, 32, 58)}`),
  });
}

function differentialPressureIndicator(): IsaSymbolDef {
  return sym({
    slug: "differential-pressure-indicator",
    category: "pack-sensors",
    nameEn: "Differential pressure (PDI)",
    nameRu: "Перепад давления (PDI)",
    tags: ["instrument", "pressure", "isa"],
    ports: portsHorizontal(48),
    svg: wrap(`${instrumentBubble("PDI")}${line(0, 48, 64, 48)}`),
  });
}

function reliefValve(): IsaSymbolDef {
  return sym({
    slug: "relief-valve",
    category: "pack-valves",
    nameEn: "Relief valve",
    nameRu: "Предохранительный клапан",
    tags: ["valve", "safety", "isa"],
    ports: portsVertical(),
    svg: wrap(`${pipeVertical()}${poly("22,28 42,28 32,40")}${line(28, 22, 36, 22)}`),
  });
}

function knifeGateValve(): IsaSymbolDef {
  return sym({
    slug: "knife-gate-valve",
    category: "pack-valves",
    nameEn: "Knife gate valve",
    nameRu: "Ножевой затвор",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(28, 20, 8, 24)}`),
  });
}

function fourWayValve(): IsaSymbolDef {
  return sym({
    slug: "four-way-valve",
    category: "pack-valves",
    nameEn: "Four-way valve",
    nameRu: "Четырёхходовой клапан",
    tags: ["valve", "isa"],
    ports: portsCross(),
    svg: wrap(`${line(32, 0, 32, 20)}${line(32, 44, 32, 64)}${line(0, 32, 20, 32)}${line(44, 32, 64, 32)}${circle(32, 32, 10)}`),
  });
}

function meteringPump(): IsaSymbolDef {
  return sym({
    slug: "metering-pump",
    category: "pack-pumps",
    nameEn: "Metering pump",
    nameRu: "Дозирующий насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${circle(32, 32, 12)}${text(32, 33, "M", 10)}`),
  });
}

function screwPump(): IsaSymbolDef {
  return sym({
    slug: "screw-pump",
    category: "pack-pumps",
    nameEn: "Screw pump",
    nameRu: "Шнековый насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(22, 24, 20, 16, 2)}${line(26, 28, 38, 36)}${line(26, 36, 38, 28)}`),
  });
}

function blower(): IsaSymbolDef {
  return sym({
    slug: "blower",
    category: "pack-misc",
    nameEn: "Blower",
    nameRu: "Воздуходувка",
    tags: ["equipment", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${circle(32, 32, 14)}${poly("32,18 40,32 32,46 24,32")}`),
  });
}

function coolingTower(): IsaSymbolDef {
  return sym({
    slug: "cooling-tower",
    category: "pack-misc",
    nameEn: "Cooling tower",
    nameRu: "Градирня",
    tags: ["equipment", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${poly("16,48 48,48 40,16 24,16")}${line(32, 48, 32, 58)}`),
  });
}

function evaporator(): IsaSymbolDef {
  return sym({
    slug: "evaporator",
    category: "pack-misc",
    nameEn: "Evaporator",
    nameRu: "Испаритель",
    tags: ["equipment", "heat", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${rect(14, 24, 36, 16)}${line(50, 32, 64, 32)}${line(0, 32, 14, 32)}${line(22, 24, 22, 16)}${line(42, 24, 42, 16)}`),
  });
}

function condenser(): IsaSymbolDef {
  return sym({
    slug: "condenser",
    category: "pack-misc",
    nameEn: "Condenser",
    nameRu: "Конденсатор",
    tags: ["equipment", "heat", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${rect(14, 24, 36, 16)}${line(50, 32, 64, 32)}${line(0, 32, 14, 32)}${line(18, 40, 46, 40)}`),
  });
}

function steamTrap(): IsaSymbolDef {
  return sym({
    slug: "steam-trap",
    category: "pack-valves",
    nameEn: "Steam trap",
    nameRu: "Конденсатоотводчик",
    tags: ["valve", "steam", "isa"],
    ports: portsVertical(),
    svg: wrap(`${line(32, 0, 32, 22)}${circle(32, 32, 10)}${line(32, 42, 32, 64)}`),
  });
}

function sightGlass(): IsaSymbolDef {
  return sym({
    slug: "sight-glass",
    category: "pack-pipes",
    nameEn: "Sight glass",
    nameRu: "Смотровое стекло",
    tags: ["pipe", "fitting", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(26, 24, 12, 16, 2)}`),
  });
}

function strainer(): IsaSymbolDef {
  return sym({
    slug: "strainer",
    category: "pack-pipes",
    nameEn: "Strainer",
    nameRu: "Фильтр-сетка",
    tags: ["pipe", "fitting", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("26,24 38,24 32,40")}`),
  });
}

function yStrainer(): IsaSymbolDef {
  return sym({
    slug: "y-strainer",
    category: "pack-pipes",
    nameEn: "Y-strainer",
    nameRu: "Y-фильтр",
    tags: ["pipe", "fitting", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 22, 32)}${line(42, 32, 64, 32)}${line(22, 32, 32, 44)}${poly("28,40 36,40 32,48")}`),
  });
}

function expansionJoint(): IsaSymbolDef {
  return sym({
    slug: "expansion-joint",
    category: "pack-pipes",
    nameEn: "Expansion joint",
    nameRu: "Компенсатор",
    tags: ["pipe", "fitting", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 22, 32)}${path("M22 26 Q32 38 42 26 Q32 14 22 26", "none")}${line(42, 32, 64, 32)}`),
  });
}

function electricHeater(): IsaSymbolDef {
  return sym({
    slug: "electric-heater",
    category: "pack-electrical",
    nameEn: "Electric heater",
    nameRu: "Электронагреватель",
    tags: ["electrical", "heat", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 18, 32)}${rect(18, 22, 28, 20, 2)}${line(24, 28, 40, 36)}${line(24, 36, 40, 28)}${line(46, 32, 64, 32)}`),
  });
}

function steamTurbine(): IsaSymbolDef {
  return sym({
    slug: "steam-turbine",
    category: "pack-misc",
    nameEn: "Steam turbine",
    nameRu: "Паровая турбина",
    tags: ["equipment", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${circle(32, 32, 14)}${poly("32,18 40,32 32,46 24,32")}`),
  });
}

function boiler(): IsaSymbolDef {
  return sym({
    slug: "boiler",
    category: "pack-misc",
    nameEn: "Boiler",
    nameRu: "Котёл",
    tags: ["equipment", "heat", "isa"],
    ports: [
      { id: "n", x: 32, y: 0 },
      { id: "s", x: 32, y: 64 },
    ],
    svg: wrap(`${rect(16, 18, 32, 36, 4)}${line(32, 54, 32, 64)}${line(32, 0, 32, 18)}`),
  });
}

function hopper(): IsaSymbolDef {
  return sym({
    slug: "hopper",
    category: "pack-tanks",
    nameEn: "Hopper",
    nameRu: "Бункер",
    tags: ["vessel", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${poly("18,16 46,16 40,44 24,44")}${line(32, 44, 32, 58)}`),
  });
}

function floatValve(): IsaSymbolDef {
  return sym({
    slug: "float-valve",
    category: "pack-valves",
    nameEn: "Float valve",
    nameRu: "Поплавковый клапан",
    tags: ["valve", "level", "isa"],
    ports: portsVertical(),
    svg: wrap(`${pipeVertical()}${circle(32, 40, 8)}${line(32, 32, 32, 48)}`),
  });
}

function footValve(): IsaSymbolDef {
  return sym({
    slug: "foot-valve",
    category: "pack-valves",
    nameEn: "Foot valve",
    nameRu: "Напорный клапан",
    tags: ["valve", "pump", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${line(32, 0, 32, 40)}${poly("22,40 42,40 32,52")}`),
  });
}

function vacuumBreaker(): IsaSymbolDef {
  return sym({
    slug: "vacuum-breaker",
    category: "pack-valves",
    nameEn: "Vacuum breaker",
    nameRu: "Вакуумный клапан",
    tags: ["valve", "safety", "isa"],
    ports: portsVertical(),
    svg: wrap(`${pipeVertical()}${poly("26,28 38,28 32,38")}${line(32, 10, 32, 28)}`),
  });
}

function backflowPreventer(): IsaSymbolDef {
  return sym({
    slug: "backflow-preventer",
    category: "pack-valves",
    nameEn: "Backflow preventer",
    nameRu: "Обратный клапан двойной",
    tags: ["valve", "safety", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(24, 24, 16, 16, 2)}${poly("28,32 36,28 36,36")}`),
  });
}

function manualValve(): IsaSymbolDef {
  return sym({
    slug: "manual-valve",
    category: "pack-valves",
    nameEn: "Manual valve",
    nameRu: "Ручной клапан",
    tags: ["valve", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("26,32 32,26 38,32 32,38")}${line(32, 12, 32, 26)}`),
  });
}

function diaphragmPump(): IsaSymbolDef {
  return sym({
    slug: "diaphragm-pump",
    category: "pack-pumps",
    nameEn: "Diaphragm pump",
    nameRu: "Мембранный насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${path("M22 28 Q32 20 42 28 Q32 44 22 36 Z")}`),
  });
}

function jetPump(): IsaSymbolDef {
  return sym({
    slug: "jet-pump",
    category: "pack-pumps",
    nameEn: "Jet pump",
    nameRu: "Струйный насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${poly("24,32 40,26 40,38")}`),
  });
}

function rotaryPump(): IsaSymbolDef {
  return sym({
    slug: "rotary-pump",
    category: "pack-pumps",
    nameEn: "Rotary pump",
    nameRu: "Роторный насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${circle(32, 32, 12)}${poly("32,20 38,32 32,44 26,32")}`),
  });
}

function airPump(): IsaSymbolDef {
  return sym({
    slug: "air-pump",
    category: "pack-pumps",
    nameEn: "Air pump",
    nameRu: "Воздушный насос",
    tags: ["pump", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(22, 24, 20, 16, 2)}${line(26, 28, 38, 36)}${line(26, 36, 38, 28)}`),
  });
}

function dayTank(): IsaSymbolDef {
  return sym({
    slug: "day-tank",
    category: "pack-tanks",
    nameEn: "Day tank",
    nameRu: "Дневной бак",
    tags: ["vessel", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(20, 20, 24, 32, 2)}${line(32, 52, 32, 60)}`),
  });
}

function surgeTank(): IsaSymbolDef {
  return sym({
    slug: "surge-tank",
    category: "pack-tanks",
    nameEn: "Surge tank",
    nameRu: "Гидроаккумулятор",
    tags: ["vessel", "isa"],
    ports: portsVertical(),
    svg: wrap(`${rect(18, 16, 28, 36, 4)}${line(32, 52, 32, 64)}${line(32, 0, 32, 16)}`),
  });
}

function settlingTank(): IsaSymbolDef {
  return sym({
    slug: "settling-tank",
    category: "pack-tanks",
    nameEn: "Settling tank",
    nameRu: "Отстойник",
    tags: ["vessel", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(14, 18, 36, 30, 2)}${line(32, 48, 32, 58)}${line(32, 10, 32, 18)}`),
  });
}

function undergroundTank(): IsaSymbolDef {
  return sym({
    slug: "underground-tank",
    category: "pack-tanks",
    nameEn: "Underground tank",
    nameRu: "Подземный резервуар",
    tags: ["vessel", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }],
    svg: wrap(`${path("M16 28 Q32 48 48 28 L48 44 Q32 56 16 44 Z")}${line(32, 0, 32, 20)}`),
  });
}

function pipeUnion(): IsaSymbolDef {
  return sym({
    slug: "pipe-union",
    category: "pack-pipes",
    nameEn: "Union",
    nameRu: "Муфта",
    tags: ["pipe", "fitting", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${rect(26, 26, 12, 12)}`),
  });
}

function pipeCoupling(): IsaSymbolDef {
  return sym({
    slug: "pipe-coupling",
    category: "pack-pipes",
    nameEn: "Coupling",
    nameRu: "Соединение",
    tags: ["pipe", "fitting", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 24, 32)}${line(40, 32, 64, 32)}${rect(24, 28, 16, 8)}`),
  });
}

function pipeVent(): IsaSymbolDef {
  return sym({
    slug: "pipe-vent",
    category: "pack-pipes",
    nameEn: "Vent",
    nameRu: "Вентиляция",
    tags: ["pipe", "fitting", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${line(32, 20, 32, 64)}${poly("32,12 26,20 38,20")}`),
  });
}

function pipeDrain(): IsaSymbolDef {
  return sym({
    slug: "pipe-drain",
    category: "pack-pipes",
    nameEn: "Drain",
    nameRu: "Дренаж",
    tags: ["pipe", "fitting", "isa"],
    ports: [{ id: "n", x: 32, y: 0 }],
    svg: wrap(`${line(32, 0, 32, 44)}${poly("32,52 26,44 38,44")}`),
  });
}

function instrumentConnection(): IsaSymbolDef {
  return sym({
    slug: "instrument-connection",
    category: "pack-pipes",
    nameEn: "Instrument connection",
    nameRu: "Отвод к прибору",
    tags: ["pipe", "instrument", "isa"],
    ports: [
      { id: "w", x: 0, y: 32 },
      { id: "n", x: 32, y: 0 },
    ],
    svg: wrap(`${line(0, 32, 32, 32)}${line(32, 32, 32, 0)}${circle(32, 32, 4)}`),
  });
}

function expansionLoop(): IsaSymbolDef {
  return sym({
    slug: "expansion-loop",
    category: "pack-pipes",
    nameEn: "Expansion loop",
    nameRu: "Компенсатор петля",
    tags: ["pipe", "fitting", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 16, 32)}${path("M16 32 Q24 12 32 32 Q40 52 48 32", "none")}${line(48, 32, 64, 32)}`),
  });
}

function conductivityIndicator(): IsaSymbolDef {
  return sym({
    slug: "conductivity-indicator",
    category: "pack-sensors",
    nameEn: "Conductivity indicator (CI)",
    nameRu: "Индикатор проводимости (CI)",
    tags: ["instrument", "analyzer", "isa"],
    ports: [{ id: "s", x: 32, y: 48 }],
    svg: wrap(`${instrumentBubble("CI")}${line(32, 46, 32, 58)}`),
  });
}

function oxygenIndicator(): IsaSymbolDef {
  return sym({
    slug: "oxygen-indicator",
    category: "pack-sensors",
    nameEn: "Oxygen indicator (OI)",
    nameRu: "Индикатор кислорода (OI)",
    tags: ["instrument", "analyzer", "isa"],
    ports: [{ id: "s", x: 32, y: 48 }],
    svg: wrap(`${instrumentBubble("OI")}${line(32, 46, 32, 58)}`),
  });
}

function humidityIndicator(): IsaSymbolDef {
  return sym({
    slug: "humidity-indicator",
    category: "pack-sensors",
    nameEn: "Humidity indicator (HI)",
    nameRu: "Индикатор влажности (HI)",
    tags: ["instrument", "isa"],
    ports: [{ id: "s", x: 32, y: 48 }],
    svg: wrap(`${instrumentBubble("HI")}${line(32, 46, 32, 58)}`),
  });
}

function vibrationTransmitter(): IsaSymbolDef {
  return sym({
    slug: "vibration-transmitter",
    category: "pack-sensors",
    nameEn: "Vibration transmitter (VT)",
    nameRu: "Датчик вибрации (VT)",
    tags: ["instrument", "isa"],
    ports: [{ id: "s", x: 32, y: 48 }],
    svg: wrap(`${bubble(32, 28, 14, "VT")}${line(32, 42, 32, 54)}`),
  });
}

function positionTransmitter(): IsaSymbolDef {
  return sym({
    slug: "position-transmitter",
    category: "pack-sensors",
    nameEn: "Position transmitter (ZT)",
    nameRu: "Датчик положения (ZT)",
    tags: ["instrument", "isa"],
    ports: [{ id: "s", x: 32, y: 48 }],
    svg: wrap(`${bubble(32, 28, 14, "ZT")}${line(32, 42, 32, 54)}`),
  });
}

function relayCoil(): IsaSymbolDef {
  return sym({
    slug: "relay-coil",
    category: "pack-electrical",
    nameEn: "Relay coil",
    nameRu: "Катушка реле",
    tags: ["electrical", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 18, 32)}${line(46, 32, 64, 32)}${rect(18, 24, 28, 16, 2)}${text(32, 33, "K", 10)}`),
  });
}

function contactor(): IsaSymbolDef {
  return sym({
    slug: "contactor",
    category: "pack-electrical",
    nameEn: "Contactor",
    nameRu: "Контактор",
    tags: ["electrical", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 16, 32)}${line(48, 32, 64, 32)}${rect(16, 22, 32, 20, 2)}${line(24, 28, 40, 36)}${line(24, 36, 40, 28)}`),
  });
}

function motorStarter(): IsaSymbolDef {
  return sym({
    slug: "motor-starter",
    category: "pack-electrical",
    nameEn: "Motor starter",
    nameRu: "Пускатель",
    tags: ["electrical", "motor", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 14, 32)}${line(50, 32, 64, 32)}${rect(14, 20, 36, 24, 2)}${circle(32, 32, 8)}`),
  });
}

function limitSwitch(): IsaSymbolDef {
  return sym({
    slug: "limit-switch",
    category: "pack-electrical",
    nameEn: "Limit switch",
    nameRu: "Концевой выключатель",
    tags: ["electrical", "switch", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${line(0, 32, 22, 32)}${line(42, 32, 64, 32)}${poly("22,32 32,24 42,32 32,40")}`),
  });
}

function pushButton(): IsaSymbolDef {
  return sym({
    slug: "push-button",
    category: "pack-electrical",
    nameEn: "Push button",
    nameRu: "Кнопка",
    tags: ["electrical", "switch", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${line(32, 36, 32, 64)}${circle(32, 28, 10)}${line(26, 28, 38, 28)}`),
  });
}

function ratioController(): IsaSymbolDef {
  return sym({
    slug: "ratio-controller",
    category: "pack-isa",
    nameEn: "Ratio controller (RC)",
    nameRu: "Регулятор соотношения (RC)",
    tags: ["isa", "control"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "RC")}`),
  });
}

function safetyController(): IsaSymbolDef {
  return sym({
    slug: "safety-controller",
    category: "pack-isa",
    nameEn: "Safety controller (SC)",
    nameRu: "Регулятор безопасности (SC)",
    tags: ["isa", "control", "safety"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "SC")}`),
  });
}

function selectorSwitch(): IsaSymbolDef {
  return sym({
    slug: "selector-switch",
    category: "pack-isa",
    nameEn: "Selector switch (HS)",
    nameRu: "Переключатель (HS)",
    tags: ["isa", "switch"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "HS")}`),
  });
}

function indicatorTransmitter(): IsaSymbolDef {
  return sym({
    slug: "indicator-transmitter",
    category: "pack-isa",
    nameEn: "Indicator transmitter (IT)",
    nameRu: "Индикатор-передатчик (IT)",
    tags: ["isa", "instrument"],
    ports: [{ id: "s", x: 32, y: 50 }],
    svg: wrap(`${bubble(32, 28, 16, "IT")}`),
  });
}

function cyclone(): IsaSymbolDef {
  return sym({
    slug: "cyclone",
    category: "pack-misc",
    nameEn: "Cyclone",
    nameRu: "Циклон",
    tags: ["equipment", "isa"],
    ports: [
      { id: "n", x: 32, y: 0 },
      { id: "s", x: 32, y: 64 },
    ],
    svg: wrap(`${poly("20,12 44,12 48,48 16,48")}${line(32, 48, 32, 58)}`),
  });
}

function clarifier(): IsaSymbolDef {
  return sym({
    slug: "clarifier",
    category: "pack-misc",
    nameEn: "Clarifier",
    nameRu: "Осветлитель",
    tags: ["equipment", "isa"],
    ports: [{ id: "s", x: 32, y: 64 }],
    svg: wrap(`${rect(16, 20, 32, 28, 4)}${line(32, 48, 32, 58)}${line(32, 8, 32, 20)}`),
  });
}

function scrubber(): IsaSymbolDef {
  return sym({
    slug: "scrubber",
    category: "pack-misc",
    nameEn: "Scrubber",
    nameRu: "Улавливатель",
    tags: ["equipment", "isa"],
    ports: portsVertical(),
    svg: wrap(`${rect(20, 16, 24, 36, 2)}${line(32, 52, 32, 64)}${line(32, 0, 32, 16)}`),
  });
}

function deaerator(): IsaSymbolDef {
  return sym({
    slug: "deaerator",
    category: "pack-misc",
    nameEn: "Deaerator",
    nameRu: "Деаэратор",
    tags: ["equipment", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${path("M18 32 Q18 18 32 18 Q46 18 46 32 Q46 46 32 46 Q18 46 18 32")}${line(32, 0, 32, 14)}`),
  });
}

function dryer(): IsaSymbolDef {
  return sym({
    slug: "dryer",
    category: "pack-misc",
    nameEn: "Dryer",
    nameRu: "Сушилка",
    tags: ["equipment", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${rect(16, 22, 32, 20, 2)}${line(0, 32, 16, 32)}${line(48, 32, 64, 32)}${line(24, 22, 24, 14)}${line(40, 22, 40, 14)}`),
  });
}

function chute(): IsaSymbolDef {
  return sym({
    slug: "chute",
    category: "pack-misc",
    nameEn: "Chute",
    nameRu: "Желоб",
    tags: ["equipment", "isa"],
    ports: [
      { id: "n", x: 32, y: 0 },
      { id: "s", x: 32, y: 64 },
    ],
    svg: wrap(`${poly("20,8 44,8 48,56 16,56")}`),
  });
}

function heatTracer(): IsaSymbolDef {
  return sym({
    slug: "heat-tracer",
    category: "pack-misc",
    nameEn: "Heat trace",
    nameRu: "Обогрев трасс",
    tags: ["equipment", "heat", "isa"],
    ports: portsHorizontal(),
    svg: wrap(`${pipeHorizontal()}${path("M18 26 Q32 18 46 26 Q32 38 18 26", "none")}`),
  });
}

export const ALL_ISA_SYMBOLS: IsaSymbolDef[] = [
  gateValve(),
  globeValve(),
  checkValve(),
  butterflyValve(),
  ballValve(),
  controlValve(),
  threeWayValve(),
  needleValve(),
  plugValve(),
  diaphragmValve(),
  safetyValve(),
  solenoidValve(),
  centrifugalPump(),
  positiveDisplacementPump(),
  verticalPump(),
  gearPump(),
  vacuumPump(),
  verticalTank(),
  horizontalTank(),
  sphericalTank(),
  openTank(),
  coneRoofTank(),
  drum(),
  pipeSegmentH(),
  pipeSegmentV(),
  elbow90(),
  tee(),
  cross(),
  reducer(),
  flange(),
  cap(),
  pressureIndicator(),
  temperatureIndicator(),
  flowIndicator(),
  levelIndicator(),
  pressureTransmitter(),
  flowTransmitter(),
  levelTransmitter(),
  analyzerTransmitter(),
  motor(),
  generator(),
  transformer2w(),
  breaker(),
  disconnect(),
  fuse(),
  controller(),
  recorder(),
  alarm(),
  switchFunc(),
  heatExchanger(),
  compressor(),
  filter(),
  mixer(),
  reactor(),
  column(),
  fan(),
  conveyor(),
  angleValve(),
  pinchValve(),
  rotaryValve(),
  reciprocatingPump(),
  submersiblePump(),
  silo(),
  orificePlate(),
  agitator(),
  separator(),
  burner(),
  damper(),
  pressureController(),
  temperatureController(),
  levelController(),
  flowController(),
  speedIndicator(),
  weightIndicator(),
  phIndicator(),
  differentialPressureIndicator(),
  reliefValve(),
  knifeGateValve(),
  fourWayValve(),
  meteringPump(),
  screwPump(),
  blower(),
  coolingTower(),
  evaporator(),
  condenser(),
  steamTrap(),
  sightGlass(),
  strainer(),
  yStrainer(),
  expansionJoint(),
  electricHeater(),
  steamTurbine(),
  boiler(),
  hopper(),
  floatValve(),
  footValve(),
  vacuumBreaker(),
  backflowPreventer(),
  manualValve(),
  diaphragmPump(),
  jetPump(),
  rotaryPump(),
  airPump(),
  dayTank(),
  surgeTank(),
  settlingTank(),
  undergroundTank(),
  pipeUnion(),
  pipeCoupling(),
  pipeVent(),
  pipeDrain(),
  instrumentConnection(),
  expansionLoop(),
  conductivityIndicator(),
  oxygenIndicator(),
  humidityIndicator(),
  vibrationTransmitter(),
  positionTransmitter(),
  relayCoil(),
  contactor(),
  motorStarter(),
  limitSwitch(),
  pushButton(),
  ratioController(),
  safetyController(),
  selectorSwitch(),
  indicatorTransmitter(),
  cyclone(),
  clarifier(),
  scrubber(),
  deaerator(),
  dryer(),
  chute(),
  heatTracer(),
  ...WAVE4_ISA_SYMBOLS,
];
