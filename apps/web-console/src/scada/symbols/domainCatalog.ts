import type { ComponentType } from "react";
import type { MimicBindingSlot, SymbolDefinition, SymbolRenderProps } from "../../types/scadaMimic";
import { type DomainRenderKind, domainRenderer } from "./domainRenders";

export interface DomainRegisteredSymbol extends SymbolDefinition {
  render: ComponentType<SymbolRenderProps>;
  paletteProps?: Record<string, unknown>;
  _nameEn?: string;
  _nameRu?: string;
}

const port = (id: string, x: number, y: number) => ({ id, x, y });

export interface DomainCategoryMeta {
  id: string;
  nameEn: string;
  nameRu: string;
}

type SymRow = [suffix: string, nameEn: string, nameRu: string, kind: DomainRenderKind, w?: number, h?: number, glyph?: string];

function rows(category: string, items: SymRow[]): DomainRegisteredSymbol[] {
  return items.map(([suffix, nameEn, nameRu, kind, w = 64, h = 48, glyph]) => {
    const id = `dom.${category}.${suffix}`;
    return {
      id,
      category,
      nameKey: `symbols.${id}`,
      defaultWidth: w,
      defaultHeight: h,
      ports: portsFor(kind, w, h),
      bindingSchema: bindingsFor(kind),
      render: domainRenderer(kind),
      paletteProps: glyph ? { glyph } : undefined,
      _nameEn: nameEn,
      _nameRu: nameRu,
    };
  });
}

function portsFor(kind: DomainRenderKind, w: number, h: number) {
  if (kind === "duct" || kind === "seg-pipe" || kind === "flex-pipe" || kind === "wire") {
    return [port("w", 0, h / 2), port("e", w, h / 2)];
  }
  if (kind === "chute") return [port("n", w / 2, 0), port("s", w / 2, h)];
  if (kind === "wellhead") return [port("s", w / 2, h)];
  return [];
}

function bindingsFor(kind: DomainRenderKind): MimicBindingSlot[] {
  if (kind === "gauge") return [{ key: "value", labelKey: "bindings.value", type: "number" }];
  if (kind === "vehicle" || kind === "screen" || kind === "wellhead") {
    return [{ key: "active", labelKey: "bindings.active", type: "boolean" }];
  }
  if (kind === "glyph") {
    return [
      { key: "running", labelKey: "bindings.running", type: "boolean", optional: true },
      { key: "active", labelKey: "bindings.active", type: "boolean", optional: true },
      { key: "open", labelKey: "bindings.open", type: "boolean", optional: true },
    ];
  }
  return [];
}

/** Industry / ASHRAE / P&ID domain categories and symbols. */
export const DOMAIN_CATEGORY_META: DomainCategoryMeta[] = [
  { id: "thermal-power", nameEn: "Thermal power", nameRu: "Тепловая энергетика" },
  { id: "oil-gas", nameEn: "Oil & gas production", nameRu: "Нефтедобыча" },
  { id: "ventilation", nameEn: "Ventilation", nameRu: "Вентиляция" },
  { id: "air-conditioning", nameEn: "Air conditioning", nameRu: "Кондиционирование воздуха" },
  { id: "laboratory", nameEn: "Laboratories", nameRu: "Лаборатории" },
  { id: "indicators", nameEn: "Indicators", nameRu: "Указатели" },
  { id: "machining", nameEn: "Machining", nameRu: "Механическая обработка" },
  { id: "ashrae-controls", nameEn: "ASHRAE controls", nameRu: "Контроль ASHRAE" },
  { id: "material-handling", nameEn: "Material handling", nameRu: "Загрузка-разгрузка материалов" },
  { id: "ashrae-ducts", nameEn: "ASHRAE ducts", nameRu: "Желоба ASHRAE" },
  { id: "mining", nameEn: "Mining", nameRu: "Горное дело" },
  { id: "ashrae-piping", nameEn: "ASHRAE piping", nameRu: "Трубопроводы ASHRAE" },
  { id: "mixing", nameEn: "Mixing devices", nameRu: "Смесительные устройства" },
  { id: "shapes", nameEn: "Basic shapes", nameRu: "Базовые фигуры" },
  { id: "motor-drives", nameEn: "Motor drives", nameRu: "Моторные установки" },
  { id: "fans", nameEn: "Fans", nameRu: "Вентиляторы" },
  { id: "hmi", nameEn: "Operator interfaces", nameRu: "Операторские интерфейсы" },
  { id: "boilers", nameEn: "Boilers", nameRu: "Бойлеры" },
  { id: "panels", nameEn: "Panels", nameRu: "Панели" },
  { id: "chemical", nameEn: "Chemical industry", nameRu: "Химическая промышленность" },
  { id: "piping", nameEn: "Pipelines", nameRu: "Трубопроводы" },
  { id: "containers", nameEn: "Containers", nameRu: "Контейнеры" },
  { id: "structures", nameEn: "Industrial structures", nameRu: "Промышленные сооружения" },
  { id: "controllers", nameEn: "Controllers", nameRu: "Контроллеры" },
  { id: "power-systems", nameEn: "Power systems", nameRu: "Энергетические системы" },
  { id: "control-blocks", nameEn: "Control blocks", nameRu: "Блоки управления" },
  { id: "process-cooling", nameEn: "Process cooling", nameRu: "Технологическое охлаждение" },
  { id: "conveyors", nameEn: "Conveyors", nameRu: "Конвейеры" },
  { id: "process-heating", nameEn: "Process heating", nameRu: "Технологический нагрев" },
  { id: "chutes", nameEn: "Chutes", nameRu: "Желоба" },
  { id: "pulp-paper", nameEn: "Pulp & paper", nameRu: "Целлюлозно-бумажное производство" },
  { id: "electrical-systems", nameEn: "Electrical systems", nameRu: "Электрические системы" },
  { id: "pumps", nameEn: "Pumps", nameRu: "Насосы" },
  { id: "finishing", nameEn: "Finishing systems", nameRu: "Окончательная обработка" },
  { id: "segmented-pipe", nameEn: "Segmented pipelines", nameRu: "Сегментированные трубопроводы" },
  { id: "flexible-pipe", nameEn: "Flexible pipelines", nameRu: "Гибкие трубопроводы" },
  { id: "sensors", nameEn: "Sensors", nameRu: "Датчики" },
  { id: "flow-meters", nameEn: "Flow meters", nameRu: "Счётчики-расходомеры" },
  { id: "tanks", nameEn: "Tanks & vessels", nameRu: "Резервуары" },
  { id: "food", nameEn: "Food industry", nameRu: "Пищевая промышленность" },
  { id: "valves", nameEn: "Valves", nameRu: "Клапаны" },
  { id: "general", nameEn: "General purpose", nameRu: "Общего применения" },
  { id: "vehicles", nameEn: "Vehicles", nameRu: "Транспортные средства" },
  { id: "district-heating", nameEn: "District heating", nameRu: "Теплоснабжение" },
  { id: "water", nameEn: "Water & wastewater", nameRu: "Водоснабжение и водоотведение" },
  { id: "hvac", nameEn: "HVAC", nameRu: "ОВК" },
  { id: "wires", nameEn: "Wires & cables", nameRu: "Провода и кабели" },
];

const CATALOG: Record<string, SymRow[]> = {
  "thermal-power": [
    ["steam-drum", "Steam drum", "Паровой барабан", "glyph", 72, 56, "SD"],
    ["deaerator", "Deaerator", "Деаэратор", "glyph", 64, 56, "DA"],
    ["economizer", "Economizer", "Экономайзер", "glyph", 80, 48, "EC"],
    ["superheater", "Superheater", "Пароперегреватель", "glyph", 80, 40, "SH"],
    ["condenser", "Condenser", "Конденсатор", "glyph", 80, 48, "CN"],
    ["cooling-tower", "Cooling tower", "Градирня", "structure", 56, 72],
    ["feed-pump", "Feedwater pump", "Питательный насос", "glyph", 56, 56, "FP"],
    ["steam-header", "Steam header", "Паропроводной коллектор", "duct", 120, 24],
  ],
  "oil-gas": [
    ["wellhead", "Wellhead", "Устье скважины", "wellhead", 48, 64],
    ["christmas-tree", "Christmas tree", "Фонтанная арматура", "glyph", 56, 56, "XT"],
    ["separator", "Separator", "Сепаратор", "glyph", 64, 56, "SEP"],
    ["heater-treater", "Heater treater", "Подогреватель-сепаратор", "glyph", 72, 48, "HT"],
    ["flare", "Flare stack", "Факел", "glyph", 32, 64, "FL"],
    ["storage-tank", "Storage tank", "Резервуар хранения", "glyph", 64, 72, "ST"],
    ["pig-trap", "Pig trap", "Камера очистки", "glyph", 56, 40, "PIG"],
    ["pumpjack", "Pump jack", "Штанговый насос", "glyph", 48, 64, "PJ"],
  ],
  ventilation: [
    ["axial-fan", "Axial fan", "Осевой вентилятор", "glyph", 56, 56, "AF"],
    ["centrifugal-fan", "Centrifugal fan", "Радиальный вентилятор", "glyph", 56, 56, "CF"],
    ["damper", "Damper", "Заслонка", "glyph", 48, 32, "DM"],
    ["louver", "Louver", "Жалюзи", "glyph", 64, 32, "LV"],
    ["diffuser", "Diffuser", "Диффузор", "glyph", 48, 48, "DF"],
    ["grille", "Grille", "Решётка", "glyph", 64, 32, "GR"],
    ["air-handler", "Air handler", "Приточная установка", "glyph", 96, 56, "AHU"],
    ["filter-unit", "Filter unit", "Фильтр", "glyph", 64, 48, "FLT"],
  ],
  "air-conditioning": [
    ["chiller", "Chiller", "Чиллер", "glyph", 80, 48, "CH"],
    ["cooling-coil", "Cooling coil", "Охлаждающий змеевик", "glyph", 72, 40, "CC"],
    ["condenser-unit", "Condenser unit", "Конденсаторный блок", "glyph", 72, 48, "CU"],
    ["compressor-ac", "AC compressor", "Компрессор", "glyph", 56, 48, "CMP"],
    ["evaporator", "Evaporator", "Испаритель", "glyph", 72, 40, "EV"],
    ["split-unit", "Split unit", "Сплит-система", "glyph", 64, 48, "SP"],
    ["vrf-outdoor", "VRF outdoor", "VRF наружный блок", "glyph", 80, 56, "VRF"],
    ["refrigerant-line", "Refrigerant line", "Линия хладона", "flex-pipe", 80, 24],
  ],
  laboratory: [
    ["flask", "Flask", "Колба", "lab", 32, 48],
    ["beaker", "Beaker", "Стакан", "lab", 28, 40],
    ["analyzer", "Analyzer", "Анализатор", "glyph", 64, 48, "AN"],
    ["fume-hood", "Fume hood", "Вытяжной шкаф", "glyph", 80, 56, "FH"],
    ["bench", "Lab bench", "Лабораторный стол", "glyph", 96, 40, "LB"],
    ["autoclave", "Autoclave", "Автоклав", "glyph", 48, 64, "AC"],
    ["spectrometer", "Spectrometer", "Спектрометр", "glyph", 72, 48, "SP"],
    ["microscope", "Microscope", "Микроскоп", "glyph", 48, 56, "MIC"],
  ],
  indicators: [
    ["dial-gauge", "Dial gauge", "Стрелочный указатель", "gauge", 48, 48],
    ["bar-indicator", "Bar indicator", "Шкала", "glyph", 72, 32, "BAR"],
    ["led", "LED indicator", "Светодиод", "glyph", 24, 24, "LED"],
    ["annunciator", "Annunciator", "Аннунциатор", "glyph", 80, 40, "ANN"],
    ["trend", "Trend display", "Тренд", "screen", 80, 56],
    ["status-panel", "Status panel", "Панель статуса", "screen", 96, 48],
    ["level-ind", "Level indicator", "Уровень", "gauge", 40, 48],
    ["flow-ind", "Flow indicator", "Расход", "gauge", 40, 48],
  ],
  machining: [
    ["lathe", "Lathe", "Токарный станок", "glyph", 72, 48, "LAT"],
    ["mill", "Milling machine", "Фрезерный станок", "glyph", 72, 48, "MIL"],
    ["drill", "Drill press", "Сверлильный станок", "glyph", 56, 56, "DRL"],
    ["grinder", "Grinder", "Шлифовальный станок", "glyph", 64, 48, "GRD"],
    ["cnc", "CNC machine", "ЧПУ станок", "glyph", 80, 56, "CNC"],
    ["press", "Press", "Пресс", "glyph", 64, 56, "PRS"],
    ["robot", "Robot arm", "Робот", "glyph", 56, 64, "RB"],
    ["workbench", "Workbench", "Верстак", "glyph", 80, 40, "WB"],
  ],
  "ashrae-controls": [
    ["thermostat", "Thermostat", "Термостат", "glyph", 40, 40, "T"],
    ["humidity-ctrl", "Humidity controller", "Регулятор влажности", "glyph", 48, 40, "RH"],
    ["vav-box", "VAV box", "VAV-бокс", "glyph", 64, 40, "VAV"],
    ["actuator", "Damper actuator", "Привод заслонки", "glyph", 48, 32, "ACT"],
    ["sensor-bus", "Sensor bus", "Шина датчиков", "glyph", 80, 32, "BUS"],
    ["bacnet-gw", "BACnet gateway", "Шлюз BACnet", "glyph", 64, 40, "BN"],
    ["pid-block", "PID controller", "ПИД-регулятор", "glyph", 56, 40, "PID"],
    ["setpoint", "Setpoint block", "Блок уставки", "glyph", 56, 32, "SP"],
  ],
  "material-handling": [
    ["crane", "Overhead crane", "Мостовой кран", "glyph", 96, 48, "CR"],
    ["hoist", "Hoist", "Таль", "glyph", 48, 56, "HO"],
    ["lift-table", "Lift table", "Подъёмный стол", "glyph", 64, 48, "LT"],
    ["palletizer", "Palletizer", "Паллетайзер", "glyph", 72, 56, "PL"],
    ["loader", "Loader", "Погрузчик", "glyph", 64, 48, "LD"],
    ["bucket-elev", "Bucket elevator", "Ковшовый элеватор", "glyph", 40, 72, "BE"],
    ["screw-feeder", "Screw feeder", "Шнек", "glyph", 80, 32, "SF"],
    ["silo-fill", "Silo fill point", "Загрузка в силос", "chute", 48, 56],
  ],
  "ashrae-ducts": [
    ["rect-duct", "Rectangular duct", "Прямоугольный воздуховод", "duct", 120, 24],
    ["round-duct", "Round duct", "Круглый воздуховод", "duct-round", 80, 32],
    ["transition", "Duct transition", "Переход", "glyph", 64, 32, "TR"],
    ["duct-elbow", "Duct elbow", "Отвод воздуховода", "glyph", 32, 32, "90"],
    ["flex-conn", "Flexible connection", "Гибкая вставка", "flex-pipe", 64, 24],
    ["fire-damper", "Fire damper", "Противопожарная заслонка", "glyph", 48, 32, "FD"],
    ["volume-damper", "Volume damper", "Регулирующая заслонка", "glyph", 48, 32, "VD"],
    ["exhaust-hood", "Exhaust hood", "Вытяжной зонт", "glyph", 72, 48, "EH"],
  ],
  mining: [
    ["crusher", "Crusher", "Дробилка", "glyph", 64, 56, "CR"],
    ["headframe", "Headframe", "Копёр", "structure", 48, 80],
    ["mine-shaft", "Mine shaft", "Ствол", "glyph", 40, 80, "SH"],
    ["mine-fan", "Mine ventilation fan", "Шахтный вентилятор", "glyph", 56, 56, "MF"],
    ["wagon", "Mine wagon", "Вагонетка", "vehicle", 72, 40],
    ["conveyor-mine", "Mine conveyor", "Шахтный конвейер", "seg-pipe", 120, 24],
    ["sump-pump", "Sump pump", "Дренажный насос", "glyph", 48, 48, "SP"],
    ["vent-raise", "Vent raise", "Вентиляционный штрек", "duct", 80, 24],
  ],
  "ashrae-piping": [
    ["supply-pipe", "Supply pipe", "Подающий трубопровод", "seg-pipe", 120, 16],
    ["return-pipe", "Return pipe", "Обратный трубопровод", "seg-pipe", 120, 16],
    ["condensate", "Condensate line", "Конденсатопровод", "seg-pipe", 100, 16],
    ["refrigerant", "Refrigerant pipe", "Линия хладонта", "flex-pipe", 100, 20],
    ["insulated", "Insulated pipe", "Изолированный трубопровод", "glyph", 100, 20, "INS"],
    ["strainer", "Strainer", "Фильтр-сетка", "glyph", 32, 32, "ST"],
    ["expansion", "Expansion joint", "Компенсатор", "flex-pipe", 48, 24],
    ["branch", "Pipe branch", "Ответвление", "glyph", 32, 32, "BR"],
  ],
  mixing: [
    ["static-mixer", "Static mixer", "Статический смеситель", "glyph", 80, 32, "SM"],
    ["inline-blender", "Inline blender", "Линейный смеситель", "glyph", 80, 32, "IB"],
    ["tank-mixer", "Tank mixer", "Смеситель в ёмкости", "glyph", 56, 64, "TM"],
    ["powder-feed", "Powder feeder", "Питатель порошка", "glyph", 64, 40, "PF"],
    ["liquid-doser", "Liquid doser", "Дозатор жидкости", "glyph", 56, 48, "LD"],
    ["homogenizer", "Homogenizer", "Гомогенизатор", "glyph", 64, 48, "HG"],
  ],
  shapes: [
    ["triangle", "Triangle", "Треугольник", "triangle", 48, 48],
    ["hexagon", "Hexagon", "Шестиугольник", "hexagon", 48, 48],
    ["pentagon", "Pentagon", "Пятиугольник", "glyph", 48, 48, "5"],
    ["star", "Star", "Звезда", "glyph", 48, 48, "★"],
    ["cross-shape", "Cross", "Крест", "glyph", 48, 48, "+"],
    ["rounded-box", "Rounded box", "Скруглённый блок", "glyph", 80, 48, "□"],
  ],
  "motor-drives": [
    ["vfd", "VFD drive", "Частотный преобразователь", "glyph", 64, 40, "VFD"],
    ["gearbox", "Gearbox", "Редуктор", "glyph", 56, 48, "GB"],
    ["coupling", "Coupling", "Муфта", "glyph", 32, 32, "CP"],
    ["soft-starter", "Soft starter", "Устройство плавного пуска", "glyph", 56, 40, "SS"],
    ["drive-panel", "Drive panel", "Шкаф привода", "glyph", 64, 72, "DP"],
    ["servo", "Servo drive", "Сервопривод", "glyph", 56, 40, "SV"],
  ],
  fans: [
    ["exhaust-fan", "Exhaust fan", "Вытяжной вентилятор", "glyph", 56, 56, "EF"],
    ["supply-fan", "Supply fan", "Приточный вентилятор", "glyph", 56, 56, "SF"],
    ["roof-fan", "Roof fan", "Крышный вентилятор", "glyph", 56, 48, "RF"],
    ["jet-fan", "Jet fan", "Jet-fan", "glyph", 64, 40, "JF"],
    ["fan-coil", "Fan coil", "Фанкойл", "glyph", 72, 40, "FC"],
  ],
  hmi: [
    ["operator-station", "Operator station", "АРМ оператора", "screen", 96, 64],
    ["touch-panel", "Touch panel", "Сенсорная панель", "screen", 72, 56],
    ["marshalling", "Marshalling cabinet", "Клеммный шкаф", "glyph", 64, 80, "MC"],
    ["alarm-panel", "Alarm panel", "Панель аварий", "screen", 80, 48],
    ["trend-screen", "Trend screen", "Экран трендов", "screen", 96, 56],
    ["mimic-panel", "Mimic panel", "Мнемопанель", "screen", 120, 72],
  ],
  boilers: [
    ["steam-boiler", "Steam boiler", "Паровой котёл", "glyph", 80, 64, "SB"],
    ["hw-boiler", "Hot water boiler", "Водогрейный котёл", "glyph", 80, 64, "HW"],
    ["burner", "Burner", "Горелка", "glyph", 48, 48, "BRN"],
    ["stack", "Stack", "Дымовая труба", "structure", 32, 80],
    ["blowdown", "Blowdown", "Спускной", "glyph", 32, 48, "BD"],
    ["feed-tank", "Feed tank", "Питательный бак", "glyph", 56, 64, "FT"],
  ],
  panels: [
    ["mcc", "MCC panel", "Шкаф MCC", "glyph", 64, 80, "MCC"],
    ["distribution", "Distribution panel", "Распределительный щит", "glyph", 64, 80, "DB"],
    ["control-panel", "Control panel", "Шкаф управления", "glyph", 64, 80, "CP"],
    ["junction-box", "Junction box", "Распределительная коробка", "glyph", 48, 40, "JB"],
    ["terminal-box", "Terminal box", "Клеммная коробка", "glyph", 48, 40, "TB"],
  ],
  chemical: [
    ["reactor", "Chemical reactor", "Реактор", "glyph", 64, 56, "RX"],
    ["dist-column", "Distillation column", "Ректификационная колонна", "glyph", 40, 96, "DC"],
    ["scrubber", "Scrubber", "Скруббер", "glyph", 56, 72, "SC"],
    ["crystallizer", "Crystallizer", "Кристаллизатор", "glyph", 64, 56, "CR"],
    ["dryer", "Dryer", "Сушилка", "glyph", 72, 48, "DR"],
    ["absorber", "Absorber", "Абсорбер", "glyph", 56, 72, "AB"],
  ],
  piping: [
    ["pipe-run", "Pipe run", "Участок трубопровода", "seg-pipe", 120, 16],
    ["manifold", "Manifold", "Коллектор", "glyph", 80, 32, "MF"],
    ["header", "Header", "Гребёнка", "glyph", 96, 24, "HD"],
    ["bleed", "Bleed point", "Точка сброса", "glyph", 32, 32, "BL"],
    ["drain-pt", "Drain point", "Дренаж", "glyph", 32, 32, "DR"],
    ["sample-pt", "Sample point", "Точка отбора пробы", "glyph", 32, 32, "SA"],
  ],
  containers: [
    ["ibc", "IBC tote", "Еврокуб", "glyph", 48, 56, "IBC"],
    ["drum", "Drum", "Бочка", "glyph", 40, 56, "DRM"],
    ["barrel", "Barrel", "Бочонок", "glyph", 36, 48, "BRL"],
    ["cryo-tank", "Cryogenic tank", "Криогенная ёмкость", "glyph", 56, 72, "CRY"],
    ["iso-container", "ISO container", "ISO-контейнер", "glyph", 96, 48, "ISO"],
    ["bulk-bag", "Bulk bag", "Мешок Big-Bag", "glyph", 48, 56, "BB"],
  ],
  structures: [
    ["building", "Building", "Здание", "structure", 80, 64],
    ["pipe-rack", "Pipe rack", "Трубопроводный эстакад", "structure", 96, 48],
    ["platform", "Platform", "Площадка", "glyph", 80, 32, "PLT"],
    ["tank-farm", "Tank farm", "Парк резервуаров", "glyph", 96, 48, "TF"],
    ["chimney", "Chimney", "Труба", "structure", 32, 80],
    ["steel-frame", "Steel frame", "Металлокаркас", "structure", 80, 56],
  ],
  controllers: [
    ["dcs", "DCS controller", "DCS", "glyph", 64, 48, "DCS"],
    ["rtu", "RTU", "RTU", "glyph", 56, 40, "RTU"],
    ["io-module", "I/O module", "Модуль ввода-вывода", "glyph", 48, 32, "IO"],
    ["fieldbus", "Fieldbus coupler", "Полевой coupler", "glyph", 56, 40, "FB"],
    ["edge-gateway", "Edge gateway", "Edge-шлюз", "glyph", 64, 40, "EG"],
  ],
  "power-systems": [
    ["switchgear", "Switchgear", "Распредустройство", "glyph", 80, 64, "SWG"],
    ["substation", "Substation", "Подстанция", "structure", 96, 64],
    ["bus-tie", "Bus tie", "Секционный выключатель", "glyph", 48, 48, "BT"],
    ["sync-gen", "Synchronous generator", "Синхронный генератор", "glyph", 72, 56, "GEN"],
    ["grid-meter", "Grid meter", "Узловой счётчик", "glyph", 48, 48, "GM"],
  ],
  "control-blocks": [
    ["interlock", "Interlock", "Блокировка", "glyph", 56, 40, "IL"],
    ["sequence", "Sequence block", "Блок последовательности", "glyph", 64, 40, "SEQ"],
    ["timer", "Timer block", "Таймер", "glyph", 48, 32, "TM"],
    ["compare", "Compare block", "Комparator", "glyph", 48, 32, "CMP"],
    ["limit", "Limit block", "Ограничитель", "glyph", 48, 32, "LIM"],
  ],
  "process-cooling": [
    ["cw-tower", "Cooling tower", "Градирня", "structure", 56, 72],
    ["cw-pump", "Cooling water pump", "Насос охлаждения", "glyph", 56, 48, "CP"],
    ["cw-hx", "Cooling HX", "Теплообменник охлаждения", "glyph", 72, 40, "HX"],
    ["cw-header", "CW header", "Коллектор охлаждения", "seg-pipe", 100, 16],
    ["chiller-plant", "Chiller plant", "Холодильная станция", "glyph", 80, 56, "CHP"],
  ],
  conveyors: [
    ["belt", "Belt conveyor", "Ленточный конвейер", "seg-pipe", 120, 24],
    ["roller", "Roller conveyor", "Роликовый конвейер", "glyph", 120, 32, "RC"],
    ["chain", "Chain conveyor", "Цепной конвейер", "glyph", 120, 32, "CHC"],
    ["bucket", "Bucket conveyor", "Ковшовый конвейер", "glyph", 40, 80, "BC"],
    ["screw", "Screw conveyor", "Шнековый конвейер", "glyph", 100, 32, "SCC"],
  ],
  "process-heating": [
    ["electric-heater", "Electric heater", "Электронагреватель", "glyph", 64, 40, "EH"],
    ["steam-heater", "Steam heater", "Паровой подогреватель", "glyph", 72, 40, "SH"],
    ["heat-trace", "Heat tracing", "Обогрев трасс", "wire", 80, 16],
    ["fired-heater", "Fired heater", "Нагреватель с горелкой", "glyph", 72, 48, "FH"],
    ["thermal-oil", "Thermal oil system", "Система ТН", "glyph", 80, 48, "TO"],
  ],
  chutes: [
    ["chute", "Material chute", "Желоб", "chute", 48, 64],
    ["slide-gate", "Slide gate", "Задвижка желоба", "glyph", 48, 32, "SG"],
    ["rotary-feed", "Rotary feeder", "Роторный питатель", "glyph", 48, 48, "RF"],
    ["diverter", "Diverter", "Перекидной клапан", "glyph", 48, 48, "DV"],
  ],
  "pulp-paper": [
    ["pulper", "Pulper", "Просеиватель", "glyph", 64, 56, "PUL"],
    ["refiner", "Refiner", "Рефайнер", "glyph", 64, 48, "REF"],
    ["paper-machine", "Paper machine", "Бумагоделательная машина", "glyph", 120, 48, "PM"],
    ["headbox", "Headbox", "Головной ящик", "glyph", 80, 40, "HB"],
    ["press-sec", "Press section", "Прессовая часть", "glyph", 80, 40, "PR"],
    ["dryer-sec", "Dryer section", "Сушильная часть", "glyph", 96, 40, "DRY"],
  ],
  "electrical-systems": [
    ["switchboard", "Switchboard", "Главный щит", "glyph", 80, 64, "SB"],
    ["motor-control", "Motor control center", "Центр управления двигателями", "glyph", 80, 64, "MCC"],
    ["power-factor", "Power factor correction", "Компенсация РМ", "glyph", 64, 48, "PFC"],
    ["transformer-st", "Station transformer", "Силовой трансформатор", "glyph", 64, 48, "TR"],
    ["cable-tray", "Cable tray section", "Кабельный лоток", "glyph", 100, 24, "CT"],
  ],
  pumps: [
    ["centrifugal", "Centrifugal pump", "Центробежный насос", "glyph", 56, 56, "CP"],
    ["positive", "Positive displacement", "Объёмный насос", "glyph", 56, 56, "PD"],
    ["submersible", "Submersible pump", "Погружной насос", "glyph", 48, 56, "SP"],
    ["booster", "Booster pump", "Бустерный насос", "glyph", 56, 48, "BP"],
    ["circulation", "Circulation pump", "Циркуляционный насос", "glyph", 56, 48, "CIR"],
    ["vacuum", "Vacuum pump", "Вакуумный насос", "glyph", 56, 48, "VP"],
  ],
  finishing: [
    ["coater", "Coater", "Коатер", "glyph", 80, 48, "CT"],
    ["calender", "Calender", "Каландр", "glyph", 80, 48, "CAL"],
    ["cutter", "Cutter", "Резак", "glyph", 64, 48, "CUT"],
    ["winder", "Winder", "Намотчик", "glyph", 72, 48, "WND"],
    ["inspection", "Inspection station", "Контроль качества", "glyph", 72, 48, "QC"],
  ],
  "segmented-pipe": [
    ["segment-a", "Pipe segment A", "Сегмент A", "seg-pipe", 80, 16],
    ["segment-b", "Pipe segment B", "Сегмент B", "seg-pipe", 80, 16],
    ["insulated-seg", "Insulated segment", "Изолированный сегмент", "glyph", 80, 20, "INS"],
    ["traced-seg", "Heat-traced segment", "Сегмент с обогревом", "wire", 80, 20],
  ],
  "flexible-pipe": [
    ["flex-hose", "Flexible hose", "Гибкий шланг", "flex-pipe", 80, 24],
    ["bellows", "Bellows", "Сильфон", "flex-pipe", 48, 32],
    ["expansion-loop", "Expansion loop", "Компенсационная петля", "flex-pipe", 64, 40],
  ],
  sensors: [
    ["temp", "Temperature sensor", "Датчик температуры", "glyph", 40, 40, "TT"],
    ["pressure", "Pressure sensor", "Датчик давления", "glyph", 40, 40, "PT"],
    ["level", "Level sensor", "Датчик уровня", "glyph", 40, 40, "LT"],
    ["flow", "Flow sensor", "Датчик расхода", "glyph", 40, 40, "FT"],
    ["proximity", "Proximity sensor", "Датчик приближения", "glyph", 32, 32, "PX"],
    ["vibration", "Vibration sensor", "Датчик вибрации", "glyph", 40, 32, "VT"],
  ],
  "flow-meters": [
    ["magnetic", "Magnetic flowmeter", "Электромагнитный расходомер", "glyph", 64, 32, "EM"],
    ["turbine-fm", "Turbine flowmeter", "Турбинный расходомер", "glyph", 56, 32, "TF"],
    ["coriolis", "Coriolis meter", "Кориолисовый расходомер", "glyph", 56, 40, "CM"],
    ["ultrasonic", "Ultrasonic meter", "Ультразвуковой расходомер", "glyph", 64, 32, "US"],
    ["orifice-plate", "Orifice plate", "Диафрагма", "glyph", 48, 32, "OR"],
  ],
  tanks: [
    ["vertical-tank", "Vertical tank", "Вертикальный резервуар", "glyph", 56, 80, "VT"],
    ["horizontal-tank", "Horizontal tank", "Горизонтальный резервуар", "glyph", 80, 56, "HT"],
    ["sphere-tank", "Sphere tank", "Сферический резервуар", "glyph", 56, 56, "SP"],
    ["day-tank", "Day tank", "Дневной бак", "glyph", 48, 48, "DT"],
    ["buffer-tank", "Buffer tank", "Буферная ёмкость", "glyph", 56, 64, "BT"],
  ],
  food: [
    ["pasteurizer", "Pasteurizer", "Пастеризатор", "glyph", 80, 48, "PAS"],
    ["filler", "Filler", "Розлив", "glyph", 72, 48, "FIL"],
    ["seamer", "Seamer", "Закаточная машина", "glyph", 64, 48, "SEA"],
    ["bottling", "Bottling line", "Линия розлива", "seg-pipe", 120, 24],
    ["mixer-food", "Food mixer", "Смеситель пищевой", "glyph", 56, 56, "MX"],
  ],
  valves: [
    ["gate-valve", "Gate valve", "Задвижка", "glyph", 36, 56, "GV"],
    ["ball-valve", "Ball valve", "Шаровой кран", "glyph", 40, 56, "BV"],
    ["butterfly", "Butterfly valve", "Дисковая задвижка", "glyph", 40, 56, "BF"],
    ["check-valve", "Check valve", "Обратный клапан", "glyph", 36, 36, "CV"],
    ["control-valve", "Control valve", "Регулирующий клапан", "glyph", 40, 72, "CTRL"],
    ["safety-valve", "Safety valve", "Предохранительный клапан", "glyph", 32, 56, "PSV"],
  ],
  general: [
    ["generic-equip", "Generic equipment", "Оборудование", "glyph", 64, 48, "EQ"],
    ["generic-pump", "Generic pump", "Насос", "glyph", 56, 48, "P"],
    ["generic-valve", "Generic valve", "Клапан", "glyph", 40, 48, "V"],
    ["generic-tank", "Generic tank", "Ёмкость", "glyph", 56, 64, "T"],
    ["utility-station", "Utility station", "Утилитарный пост", "glyph", 72, 48, "UT"],
  ],
  vehicles: [
    ["truck", "Truck", "Грузовик", "vehicle", 80, 40],
    ["rail-wagon", "Rail wagon", "Вагон", "vehicle", 96, 40],
    ["forklift", "Forklift", "Погрузчик", "vehicle", 64, 48],
    ["agv", "AGV", "AGV", "vehicle", 56, 40],
    ["tanker", "Tanker truck", "Автоцистерна", "vehicle", 96, 40],
  ],
  "district-heating": [
    ["dh-substation", "DH substation", "Тепловой пункт", "glyph", 80, 56, "TP"],
    ["dh-hx", "District heat exchanger", "Теплообменник ТС", "glyph", 72, 40, "HX"],
    ["dh-pump", "DH circulation pump", "Насос ТС", "glyph", 56, 48, "CP"],
    ["heat-meter", "Heat meter", "Теплосчётчик", "glyph", 48, 40, "HM"],
    ["dh-header", "DH header", "Коллектор ТС", "seg-pipe", 100, 16],
  ],
  water: [
    ["intake", "Water intake", "Водозабор", "glyph", 64, 48, "INT"],
    ["clarifier", "Clarifier", "Отстойник", "glyph", 72, 56, "CL"],
    ["pump-station", "Pump station", "Насосная станция", "glyph", 80, 48, "PS"],
    ["filter-bed", "Filter bed", "Фильтр", "glyph", 72, 48, "FLT"],
    ["chlorination", "Chlorination", "Хлорирование", "glyph", 56, 48, "CL2"],
    ["lift-station", "Sewage lift", "КНС", "glyph", 64, 56, "KNS"],
  ],
  hvac: [
    ["ahu", "Air handling unit", "Приточно-вытяжная установка", "glyph", 96, 56, "AHU"],
    ["fcu", "Fan coil unit", "Фанкойл", "glyph", 64, 40, "FCU"],
    ["radiator", "Radiator", "Радиатор", "glyph", 64, 32, "RAD"],
    ["ufh", "Underfloor heating", "Тёплый пол", "glyph", 80, 24, "UFH"],
    ["hrv", "Heat recovery unit", "Рекуператор", "glyph", 72, 48, "HRV"],
    ["boiler-hvac", "HVAC boiler", "Котёл ОВК", "glyph", 64, 56, "BLR"],
  ],
  wires: [
    ["power-cable", "Power cable", "Силовой кабель", "wire", 80, 16],
    ["control-cable", "Control cable", "Кабель управления", "wire", 80, 12],
    ["fiber", "Fiber optic", "Оптоволокно", "wire", 80, 12],
    ["bus-cable", "Fieldbus cable", "Полевой кабель", "wire", 80, 12],
    ["cable-tray", "Cable tray", "Кабельный лоток", "glyph", 100, 24, "CT"],
    ["junction", "Cable junction", "Кабельная муфта", "glyph", 32, 32, "JM"],
  ],
};

export const DOMAIN_SYMBOLS: DomainRegisteredSymbol[] = Object.entries(CATALOG).flatMap(([cat, items]) =>
  rows(cat, items),
);

export const DOMAIN_CATEGORY_IDS = DOMAIN_CATEGORY_META.map((c) => c.id);

export function buildDomainI18n(): Record<string, Record<string, string>> {
  const en: Record<string, string> = {};
  const ru: Record<string, string> = {};
  for (const cat of DOMAIN_CATEGORY_META) {
    en[`categories.${cat.id}`] = cat.nameEn;
    ru[`categories.${cat.id}`] = cat.nameRu;
  }
  for (const sym of DOMAIN_SYMBOLS) {
    en[sym.nameKey] = sym._nameEn ?? sym.id;
    ru[sym.nameKey] = sym._nameRu ?? sym._nameEn ?? sym.id;
  }
  return { en, ru };
}
