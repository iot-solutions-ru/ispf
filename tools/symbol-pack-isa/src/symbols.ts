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
];
