import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const HUB = "root.platform.devices.mes-hub-01";

const MIGRATION_SQL = `
CREATE TABLE IF NOT EXISTS mes_line (
  id UUID PRIMARY KEY,
  line_code VARCHAR(32) NOT NULL UNIQUE,
  line_type VARCHAR(8) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  unit_a_busy BOOLEAN NOT NULL DEFAULT false,
  feed_capacity_pct INTEGER NOT NULL DEFAULT 100,
  transport_load_pct INTEGER NOT NULL DEFAULT 0,
  machine_status VARCHAR(32) NOT NULL DEFAULT 'running'
);
CREATE TABLE IF NOT EXISTS mes_order (
  id UUID PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL UNIQUE,
  line_code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  defect_kg NUMERIC(10,2) NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS mes_defect_event (
  id UUID PRIMARY KEY,
  line_code VARCHAR(32) NOT NULL,
  order_no VARCHAR(32),
  volume_kg NUMERIC(10,2) NOT NULL,
  is_special_scrap BOOLEAN NOT NULL DEFAULT false,
  is_transitional_remainder BOOLEAN NOT NULL DEFAULT false,
  order_scenario VARCHAR(32) NOT NULL DEFAULT 'active',
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS mes_recommendation (
  id UUID PRIMARY KEY,
  event_id UUID NOT NULL,
  route_type VARCHAR(32) NOT NULL,
  target VARCHAR(32) NOT NULL,
  priority INTEGER NOT NULL,
  rationale VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'recommended'
);
CREATE TABLE IF NOT EXISTS mes_hub_context (
  id INTEGER PRIMARY KEY,
  current_event_id UUID,
  line_type VARCHAR(8),
  is_transitional BOOLEAN NOT NULL DEFAULT false,
  route_target VARCHAR(32),
  route_priority INTEGER,
  route_rationale VARCHAR(512)
);
INSERT INTO mes_hub_context (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM mes_hub_context WHERE id = 1);
INSERT INTO mes_line (id, line_code, line_type, display_name, unit_a_busy, feed_capacity_pct, transport_load_pct, machine_status)
SELECT 'a0000001-0000-0000-0000-000000000001', 'LINE-A01', 'A', 'Demo line A (rework + feed)', false, 60, 40, 'running'
WHERE NOT EXISTS (SELECT 1 FROM mes_line WHERE line_code = 'LINE-A01');
INSERT INTO mes_line (id, line_code, line_type, display_name, unit_a_busy, feed_capacity_pct, transport_load_pct, machine_status)
SELECT 'a0000002-0000-0000-0000-000000000002', 'LINE-B01', 'B', 'Demo line B (feed only)', false, 60, 30, 'running'
WHERE NOT EXISTS (SELECT 1 FROM mes_line WHERE line_code = 'LINE-B01');
INSERT INTO mes_line (id, line_code, line_type, display_name, unit_a_busy, feed_capacity_pct, transport_load_pct, machine_status)
SELECT 'a0000003-0000-0000-0000-000000000003', 'LINE-D01', 'D', 'Demo line D (source)', false, 0, 60, 'running'
WHERE NOT EXISTS (SELECT 1 FROM mes_line WHERE line_code = 'LINE-D01');
INSERT INTO mes_order (id, order_no, line_code, status, defect_kg)
SELECT 'b0000001-0000-0000-0000-000000000001', 'DEMO-A-01', 'LINE-A01', 'closing', 10
WHERE NOT EXISTS (SELECT 1 FROM mes_order WHERE order_no = 'DEMO-A-01');
INSERT INTO mes_order (id, order_no, line_code, status, defect_kg)
SELECT 'b0000002-0000-0000-0000-000000000002', 'DEMO-A-02', 'LINE-A01', 'open', 2
WHERE NOT EXISTS (SELECT 1 FROM mes_order WHERE order_no = 'DEMO-A-02');
INSERT INTO mes_order (id, order_no, line_code, status, defect_kg)
SELECT 'b0000003-0000-0000-0000-000000000003', 'DEMO-B-01', 'LINE-B01', 'open', 1
WHERE NOT EXISTS (SELECT 1 FROM mes_order WHERE order_no = 'DEMO-B-01');
INSERT INTO mes_order (id, order_no, line_code, status, defect_kg)
SELECT 'b0000004-0000-0000-0000-000000000004', 'DEMO-D-01', 'LINE-D01', 'open', 0
WHERE NOT EXISTS (SELECT 1 FROM mes_order WHERE order_no = 'DEMO-D-01');
`.trim().replace(/\n/g, " ");

const SANITIZE_MIGRATION_SQL = `
DROP TABLE IF EXISTS mes_recommendation;
DROP TABLE IF EXISTS mes_defect_event;
DROP TABLE IF EXISTS mes_hub_context;
DROP TABLE IF EXISTS mes_order;
DROP TABLE IF EXISTS mes_line;
${MIGRATION_SQL}
`.trim().replace(/\n/g, " ");

const ROUTE_CALC_SQL =
  "SELECT e.id::text AS event_id, " +
  "CASE WHEN l.line_type = 'A' AND (l.unit_a_busy = true OR e.volume_kg > 10) THEN 'PEER_LINE' " +
  "WHEN l.line_type = 'A' THEN 'REWORK_A' " +
  "WHEN l.line_type = 'B' AND e.volume_kg > (l.feed_capacity_pct / 10.0) THEN 'FEED_QUEUE' " +
  "WHEN l.line_type = 'B' THEN 'FEED_B' " +
  "WHEN l.line_type = 'D' AND e.is_special_scrap = true THEN 'SPECIAL_ROUTE' " +
  "ELSE 'TRANSPORT_HUB' END AS route_type, " +
  "CASE WHEN l.line_type = 'A' AND (l.unit_a_busy = true OR e.volume_kg > 10) THEN 'PEER' " +
  "WHEN l.line_type = 'A' THEN 'REWORK_A' " +
  "WHEN l.line_type = 'B' AND e.volume_kg > (l.feed_capacity_pct / 10.0) THEN 'FEED_QUEUE' " +
  "WHEN l.line_type = 'B' THEN 'FEED_B' " +
  "WHEN l.line_type = 'D' AND e.is_special_scrap = true THEN 'SPECIAL_ROUTE' " +
  "ELSE 'TRANSPORT_HUB' END AS target, " +
  "(CASE WHEN l.line_type = 'D' AND e.is_special_scrap = false AND l.transport_load_pct > 70 THEN 3 " +
  "WHEN l.line_type = 'D' AND e.is_special_scrap = false AND l.transport_load_pct > 40 THEN 2 " +
  "WHEN l.line_type = 'A' AND (l.unit_a_busy = true OR e.volume_kg > 10) THEN 2 " +
  "WHEN l.line_type = 'B' AND e.volume_kg > (l.feed_capacity_pct / 10.0) THEN 2 ELSE 1 END)::text AS priority, " +
  "CASE WHEN l.line_type = 'D' AND e.is_special_scrap = true THEN 'Demo: special scrap — alternate route' " +
  "WHEN l.line_type = 'D' THEN 'Demo: transport to reprocessing hub' " +
  "WHEN l.line_type = 'B' AND e.volume_kg > (l.feed_capacity_pct / 10.0) THEN 'Demo: feed capacity exceeded — queue' " +
  "WHEN l.line_type = 'B' THEN 'Demo: feed on line B' " +
  "WHEN l.line_type = 'A' AND (l.unit_a_busy = true OR e.volume_kg > 10) THEN 'Demo: rework unit busy — peer line' " +
  "ELSE 'Demo: rework on line A' END AS rationale " +
  "FROM mes_defect_event e JOIN mes_line l ON l.line_code = e.line_code " +
  "WHERE e.id = (SELECT current_event_id FROM mes_hub_context WHERE id = 1)";

const SCRIPTS = {
  mes_simulateDefect: {
    steps: [
      { type: "exec", sql: "UPDATE mes_defect_event SET status = 'cancelled' WHERE status IN ('pending', 'processing', 'recommended', 'confirmed')" },
      { type: "selectOne", var: "line", sql: "SELECT line_code, line_type FROM mes_line WHERE line_code = ?", params: ["${input.lineCode}"] },
      { type: "failIfNull", var: "line", message: "Line not found" },
      { type: "exec", sql: "INSERT INTO mes_defect_event (id, line_code, volume_kg, is_special_scrap, order_scenario, status) VALUES (gen_random_uuid(), ?, ?, CASE WHEN ? IN ('1','true','TRUE') THEN true ELSE false END, ?, 'pending')", params: ["${input.lineCode}", "${input.volumeKg}", "${input.isSpecialScrap}", "${input.orderScenario}"] },
      { type: "selectOne", var: "event", sql: "SELECT id::text AS event_id, line_code FROM mes_defect_event WHERE status = 'pending' ORDER BY created_at DESC LIMIT 1" },
      { type: "exec", sql: "UPDATE mes_hub_context SET current_event_id = ?::uuid, line_type = ?, is_transitional = false, route_target = NULL, route_priority = NULL, route_rationale = NULL WHERE id = 1", params: ["${event.event_id}", "${line.line_type}"] },
      { type: "return", fields: { error_code: "OK", error_message: "", eventId: "${event.event_id}", lineCode: "${event.line_code}" } },
    ],
  },
  mes_receiveDefect: {
    steps: [
      { type: "selectOne", var: "event", sql: "SELECT e.id::text AS event_id, e.line_code, e.volume_kg::text AS volume_kg, l.line_type FROM mes_defect_event e JOIN mes_line l ON l.line_code = e.line_code WHERE e.status = 'pending' ORDER BY e.created_at ASC LIMIT 1" },
      { type: "failIfNull", var: "event", message: "No pending defect event" },
      { type: "exec", sql: "UPDATE mes_defect_event SET status = 'processing' WHERE id = ?::uuid", params: ["${event.event_id}"] },
      { type: "exec", sql: "UPDATE mes_hub_context SET current_event_id = ?::uuid, line_type = ?, is_transitional = false WHERE id = 1", params: ["${event.event_id}", "${event.line_type}"] },
      { type: "return", fields: { error_code: "OK", error_message: "", eventId: "${event.event_id}", lineCode: "${event.line_code}", lineType: "${event.line_type}", volumeKg: "${event.volume_kg}" } },
    ],
  },
  mes_resolveOrder: {
    steps: [
      { type: "exec", sql: "UPDATE mes_defect_event e SET order_no = CASE WHEN e.order_scenario = 'closing' OR EXISTS (SELECT 1 FROM mes_order o WHERE o.line_code = e.line_code AND o.status = 'closing') THEN COALESCE((SELECT order_no FROM mes_order WHERE line_code = e.line_code AND status = 'closing' ORDER BY order_no DESC LIMIT 1), (SELECT order_no FROM mes_order WHERE line_code = e.line_code AND status IN ('open','in_progress') ORDER BY order_no DESC LIMIT 1)) ELSE (SELECT order_no FROM mes_order WHERE line_code = e.line_code AND status IN ('open','in_progress') ORDER BY order_no DESC LIMIT 1) END, is_transitional_remainder = CASE WHEN (e.order_scenario = 'closing' OR EXISTS (SELECT 1 FROM mes_order o WHERE o.line_code = e.line_code AND o.status = 'closing')) AND (e.volume_kg > 10 OR COALESCE((SELECT l.unit_a_busy FROM mes_line l WHERE l.line_code = e.line_code), false) = true) THEN true ELSE false END WHERE e.id = (SELECT current_event_id FROM mes_hub_context WHERE id = 1)" },
      { type: "exec", sql: "UPDATE mes_hub_context SET is_transitional = (SELECT e.is_transitional_remainder FROM mes_defect_event e WHERE e.id = current_event_id), line_type = (SELECT l.line_type FROM mes_defect_event e JOIN mes_line l ON l.line_code = e.line_code WHERE e.id = current_event_id) WHERE id = 1" },
      { type: "selectOne", var: "resolved", sql: "SELECT e.id::text AS event_id, e.order_no, e.is_transitional_remainder::text AS is_transitional, l.line_type FROM mes_defect_event e JOIN mes_line l ON l.line_code = e.line_code WHERE e.id = (SELECT current_event_id FROM mes_hub_context WHERE id = 1)" },
      { type: "failIfNull", var: "resolved", message: "Defect event not found" },
      { type: "return", fields: { error_code: "OK", error_message: "", eventId: "${resolved.event_id}", orderNo: "${resolved.order_no}", isTransitionalRemainder: "${resolved.is_transitional}", lineType: "${resolved.line_type}" } },
    ],
  },
  mes_calculateRoute: {
    steps: [
      { type: "selectOne", var: "route", sql: ROUTE_CALC_SQL },
      { type: "failIfNull", var: "route", message: "Event not found for route calculation" },
      { type: "exec", sql: "INSERT INTO mes_recommendation (id, event_id, route_type, target, priority, rationale, status) VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, ?, 'recommended')", params: ["${route.event_id}", "${route.route_type}", "${route.target}", "${route.priority}", "${route.rationale}"] },
      { type: "exec", sql: "UPDATE mes_defect_event SET status = 'recommended' WHERE id = ?::uuid", params: ["${route.event_id}"] },
      { type: "exec", sql: "UPDATE mes_hub_context SET route_target = ?, route_priority = ?, route_rationale = ? WHERE id = 1", params: ["${route.target}", "${route.priority}", "${route.rationale}"] },
      { type: "return", fields: { error_code: "OK", error_message: "", eventId: "${route.event_id}", routeType: "${route.route_type}", target: "${route.target}", priority: "${route.priority}", rationale: "${route.rationale}" } },
    ],
  },
  mes_confirmRoute: {
    steps: [
      { type: "selectOne", var: "event", sql: "SELECT id::text AS event_id, order_no, volume_kg FROM mes_defect_event WHERE id = (SELECT current_event_id FROM mes_hub_context WHERE id = 1) AND status = 'recommended'" },
      { type: "failIfNull", var: "event", message: "No recommended defect to confirm" },
      { type: "exec", sql: "UPDATE mes_recommendation SET status = 'confirmed' WHERE event_id = ?::uuid AND status = 'recommended'", params: ["${event.event_id}"] },
      { type: "exec", sql: "UPDATE mes_defect_event SET status = 'confirmed' WHERE id = ?::uuid", params: ["${event.event_id}"] },
      { type: "exec", sql: "UPDATE mes_order SET defect_kg = defect_kg + ? WHERE order_no = ?", params: ["${event.volume_kg}", "${event.order_no}"] },
      { type: "return", fields: { error_code: "OK", error_message: "", eventId: "${event.event_id}", status: "confirmed" } },
    ],
  },
  mes_finalizeDefect: {
    steps: [
      { type: "selectOne", var: "event", sql: "SELECT id::text AS event_id FROM mes_defect_event WHERE id = COALESCE((SELECT current_event_id FROM mes_hub_context WHERE id = 1), '00000000-0000-0000-0000-000000000000'::uuid)" },
      { type: "failIfNull", var: "event", message: "Event not found" },
      { type: "exec", sql: "UPDATE mes_defect_event SET status = 'routed' WHERE id = ?::uuid", params: ["${event.event_id}"] },
      { type: "exec", sql: "UPDATE mes_hub_context SET current_event_id = NULL, is_transitional = false, route_target = NULL, route_priority = NULL, route_rationale = NULL WHERE id = 1" },
      { type: "return", fields: { error_code: "OK", error_message: "", eventId: "${event.event_id}", status: "routed" } },
    ],
  },
  mes_listLines: {
    steps: [
      { type: "selectMany", var: "rows", sql: "SELECT l.line_code, l.line_type, l.display_name, l.machine_status, l.unit_a_busy::text AS unit_a_busy, l.feed_capacity_pct::text AS feed_capacity_pct, l.transport_load_pct::text AS transport_load_pct, COALESCE(h.route_target, '') AS route_target, COALESCE(h.route_rationale, '') AS route_rationale FROM mes_line l LEFT JOIN mes_hub_context h ON h.id = 1 ORDER BY l.line_code" },
      { type: "return", fields: { error_code: "OK", error_message: "", rows: "${rows}" } },
    ],
  },
};

const FUNCTION_SPECS = {
  mes_simulateDefect: {
    in: [
      { name: "lineCode", type: "STRING" },
      { name: "volumeKg", type: "STRING" },
      { name: "isSpecialScrap", type: "STRING" },
      { name: "orderScenario", type: "STRING" },
    ],
    out: [
      { name: "error_code", type: "STRING" },
      { name: "error_message", type: "STRING" },
      { name: "eventId", type: "STRING" },
      { name: "lineCode", type: "STRING" },
    ],
  },
  mes_receiveDefect: {
    in: [],
    out: [
      { name: "error_code", type: "STRING" },
      { name: "error_message", type: "STRING" },
      { name: "eventId", type: "STRING" },
      { name: "lineCode", type: "STRING" },
      { name: "lineType", type: "STRING" },
      { name: "volumeKg", type: "STRING" },
    ],
  },
  mes_resolveOrder: {
    in: [{ name: "eventId", type: "STRING" }],
    out: [
      { name: "error_code", type: "STRING" },
      { name: "error_message", type: "STRING" },
      { name: "eventId", type: "STRING" },
      { name: "orderNo", type: "STRING" },
      { name: "isTransitionalRemainder", type: "STRING" },
      { name: "lineType", type: "STRING" },
    ],
  },
  mes_calculateRoute: {
    in: [{ name: "eventId", type: "STRING" }],
    out: [
      { name: "error_code", type: "STRING" },
      { name: "error_message", type: "STRING" },
      { name: "eventId", type: "STRING" },
      { name: "routeType", type: "STRING" },
      { name: "target", type: "STRING" },
      { name: "priority", type: "STRING" },
      { name: "rationale", type: "STRING" },
    ],
  },
  mes_confirmRoute: {
    in: [],
    out: [
      { name: "error_code", type: "STRING" },
      { name: "error_message", type: "STRING" },
      { name: "eventId", type: "STRING" },
      { name: "status", type: "STRING" },
    ],
  },
  mes_finalizeDefect: {
    in: [{ name: "eventId", type: "STRING" }],
    out: [
      { name: "error_code", type: "STRING" },
      { name: "error_message", type: "STRING" },
      { name: "eventId", type: "STRING" },
      { name: "status", type: "STRING" },
    ],
  },
  mes_listLines: {
    in: [],
    out: [
      { name: "error_code", type: "STRING" },
      { name: "error_message", type: "STRING" },
      {
        name: "rows",
        type: "RECORD_LIST",
        nestedSchema: {
          name: "line_row",
          fields: [
            { name: "line_code", type: "STRING" },
            { name: "line_type", type: "STRING" },
            { name: "display_name", type: "STRING" },
            { name: "machine_status", type: "STRING" },
            { name: "unit_a_busy", type: "STRING" },
            { name: "feed_capacity_pct", type: "STRING" },
            { name: "transport_load_pct", type: "STRING" },
            { name: "route_target", type: "STRING" },
            { name: "route_rationale", type: "STRING" },
          ],
        },
      },
    ],
  },
};

function makeFunction(name) {
  const spec = FUNCTION_SPECS[name];
  return {
    objectPath: HUB,
    functionName: name,
    version: "1",
    descriptor: {
      inputSchema: { name: "in", fields: spec.in },
      outputSchema: { name: "out", fields: spec.out },
    },
    source: { type: "script", body: JSON.stringify(SCRIPTS[name]) },
  };
}

const bpmnXml = fs.readFileSync(path.join(ROOT, "bpmn", "defect-distribution.bpmn.xml"), "utf8");

const SIM_HELP_HTML =
  "<p><strong>Два шага для оператора</strong></p>" +
  "<ol style='margin:0.5rem 0 0 1.1rem;padding:0;font-size:0.9em'>" +
  "<li><strong>Шаг 1</strong> — отправьте сообщение о браке с линии (имитация SCADA).</li>" +
  "<li><strong>Шаг 2</strong> — нажмите «Запустить распределение». Задача диспетчера появится на вкладке «Сводка».</li>" +
  "</ol>" +
  "<ul style='margin:0.5rem 0 0 1.1rem;padding:0;font-size:0.9em'>" +
  "<li><strong>Объём</strong> — масса брака в кг (например 12)</li>" +
  "<li><strong>Особый тип брака</strong> — только для линии D (демо-флаг)</li>" +
  "<li><strong>Сценарий</strong> — активный ордер или закрывающий</li>" +
  "</ul>";

const simFieldsJson = JSON.stringify([
  {
    name: "lineCode",
    label: "Производственная линия",
    type: "select",
    defaultValue: "LINE-A01",
    selectOptions: [
      { value: "LINE-A01", label: "A01 — тип A (переработка + подача)" },
      { value: "LINE-B01", label: "B01 — тип B (подача)" },
      { value: "LINE-D01", label: "D01 — тип D (источник)" },
    ],
  },
  {
    name: "volumeKg",
    label: "Объём брака (кг)",
    type: "text",
    defaultValue: "12",
    hint: "Число, например 12",
  },
  {
    name: "isSpecialScrap",
    label: "Особый тип брака",
    type: "select",
    defaultValue: "0",
    selectOptions: [
      { value: "0", label: "Нет" },
      { value: "1", label: "Да (только линия D)" },
    ],
    hint: "Демо-флаг для альтернативного маршрута на линии D",
  },
  {
    name: "orderScenario",
    label: "Сценарий ордера",
    type: "select",
    defaultValue: "active",
    selectOptions: [
      { value: "active", label: "Активный ордер" },
      { value: "closing", label: "Закрывающий ордер" },
    ],
  },
]);

const overviewLayout = {
  columns: 12,
  rowHeight: 72,
  widgets: [
    { id: "lines-report", type: "report", title: "Линии и рекомендации", x: 0, y: 0, w: 8, h: 5, reportPath: "root.platform.reports.mes-defect-lines-status", emptyMessage: "Нет данных" },
    { id: "work-queue", type: "work-queue", title: "Задачи диспетчера", x: 8, y: 0, w: 4, h: 5, operatorId: "operator", operatorAppId: "mes-defect-demo", maxItems: 10 },
    { id: "pending-rec", type: "report", title: "Ожидающие рекомендации", x: 0, y: 5, w: 12, h: 3, reportPath: "root.platform.reports.mes-defect-pending-recommendations", emptyMessage: "Нет активных рекомендаций" },
  ],
};

const simulatorLayout = {
  columns: 12,
  rowHeight: 72,
  widgets: [
    { id: "sim-help", type: "html-snippet", title: "Как это работает", x: 0, y: 0, w: 12, h: 2, htmlJson: SIM_HELP_HTML },
    {
      id: "sim-form",
      type: "function-form",
      title: "Сообщение о браке",
      x: 0,
      y: 2,
      w: 5,
      h: 6,
      objectPath: HUB,
      functionName: "mes_simulateDefect",
      buttonLabel: "Отправить в SCADA",
      fieldsJson: simFieldsJson,
    },
    {
      id: "start-distribution",
      type: "function",
      title: "Распределение брака",
      x: 0,
      y: 8,
      w: 5,
      h: 2,
      objectPath: HUB,
      workflowPath: "root.platform.workflows.mes-defect-distribution",
      buttonLabel: "Запустить распределение",
      confirmMessage: "Запустить процесс распределения для ожидающего брака?",
    },
    {
      id: "events-feed",
      type: "event-feed",
      title: "Журнал событий",
      x: 5,
      y: 2,
      w: 7,
      h: 6,
      objectPath: HUB,
      eventNamesJson: '["mesDefectDetected","mesDefectRouted"]',
      maxItems: 30,
    },
  ],
};

const ordersLayout = {
  columns: 12,
  rowHeight: 72,
  widgets: [
    { id: "orders-report", type: "report", title: "Ордера на линиях", x: 0, y: 0, w: 12, h: 6, reportPath: "root.platform.reports.mes-defect-orders-detail", emptyMessage: "Нет ордеров" },
  ],
};

const DOUBLE_SCHEMA = { name: "doubleValue", fields: [{ name: "value", type: "DOUBLE" }] };
const VOID_SCHEMA = { name: "void", fields: [] };

const bundle = {
  version: "1.0.0",
  displayName: "MES Defect Distribution Demo",
  tablePrefix: "",
  schemaName: "app_mes_defect",
  migrations: [
    { id: "mes_defect_schema", sql: MIGRATION_SQL },
    { id: "mes_defect_sanitize_v2", sql: SANITIZE_MIGRATION_SQL },
  ],
  models: [
    {
      name: "mes-defect-hub-v1",
      description: "MES defect hub with SCADA bindings and workflow events",
      type: "RELATIVE",
      targetObjectType: "DEVICE",
      variables: [
        { name: "defectPending", description: "Pending defect events", group: "runtime", schema: DOUBLE_SCHEMA, readable: true, writable: false, defaultValue: { schema: DOUBLE_SCHEMA, rows: [{ value: 0 }] } },
        { name: "lineTypeCode", description: "Line type code 1=A 2=B 3=D", group: "runtime", schema: DOUBLE_SCHEMA, readable: true, writable: false, defaultValue: { schema: DOUBLE_SCHEMA, rows: [{ value: 0 }] } },
        { name: "isTransitionalFlag", description: "Transitional remainder flag", group: "runtime", schema: DOUBLE_SCHEMA, readable: true, writable: false, defaultValue: { schema: DOUBLE_SCHEMA, rows: [{ value: 0 }] } },
      ],
      events: [
        { name: "mesDefectDetected", description: "Defect event received from SCADA", payloadSchema: VOID_SCHEMA, level: "INFO" },
        { name: "mesDefectRouted", description: "Defect routed in MES", payloadSchema: VOID_SCHEMA, level: "INFO" },
      ],
      functions: [],
      bindings: [],
      parameters: {},
    },
  ],
  objects: [
    { parentPath: "root.platform.devices", name: "mes-hub-01", type: "DEVICE", displayName: "MES Demo Hub", description: "Fictional MES hub for defect distribution demo", templateId: "mes-defect-hub-v1" },
    { parentPath: "root.platform.devices", name: "mes-line-A01", type: "DEVICE", displayName: "Demo Line A01", description: "Fictional type A line (rework + feed)" },
    { parentPath: "root.platform.devices", name: "mes-line-B01", type: "DEVICE", displayName: "Demo Line B01", description: "Fictional type B line (feed only)" },
    { parentPath: "root.platform.devices", name: "mes-line-D01", type: "DEVICE", displayName: "Demo Line D01", description: "Fictional type D line (defect source)" },
  ],
  functions: Object.keys(SCRIPTS).map(makeFunction),
  bindings: [
    { objectPath: HUB, variable: "defectPending", query: "SELECT COALESCE(CAST(COUNT(*) AS DOUBLE), 0) AS v FROM mes_defect_event WHERE status = 'pending'", refresh: "on_function_success", refreshIntervalMs: 500, valueField: "v", triggerObjectPath: HUB, triggerFunctionName: "mes_simulateDefect,mes_receiveDefect,mes_finalizeDefect", enabled: true },
    { objectPath: HUB, variable: "lineTypeCode", query: "SELECT COALESCE((SELECT CASE line_type WHEN 'A' THEN 1.0 WHEN 'B' THEN 2.0 ELSE 3.0 END FROM mes_hub_context WHERE id = 1), 0.0) AS v", refresh: "on_function_success", refreshIntervalMs: 1000, valueField: "v", triggerObjectPath: HUB, triggerFunctionName: "mes_resolveOrder", enabled: true },
    { objectPath: HUB, variable: "isTransitionalFlag", query: "SELECT CASE WHEN COALESCE(is_transitional, false) THEN 1 ELSE 0 END AS v FROM mes_hub_context WHERE id = 1", refresh: "on_function_success", refreshIntervalMs: 1000, valueField: "v", triggerObjectPath: HUB, triggerFunctionName: "mes_resolveOrder", enabled: true },
  ],
  reports: [
    { reportId: "mes-defect-lines-status", title: "Статус линий", description: "Линии с текущей рекомендацией маршрута", query: "SELECT l.line_code, l.line_type, l.display_name, l.machine_status, COALESCE(c.route_target, '') AS route_target, COALESCE(c.route_rationale, '') AS route_rationale FROM mes_line l LEFT JOIN mes_hub_context c ON c.id = 1 ORDER BY l.line_code", parameters: [], columns: [{ field: "line_code", label: "Линия" }, { field: "line_type", label: "Тип" }, { field: "display_name", label: "Название" }, { field: "machine_status", label: "Статус машины" }, { field: "route_target", label: "Маршрут" }, { field: "route_rationale", label: "Рекомендация" }], maxRows: 50 },
    { reportId: "mes-defect-orders-detail", title: "Ордера", description: "Ордера с объёмом брака", query: "SELECT order_no, line_code, status, defect_kg FROM mes_order ORDER BY line_code, order_no", parameters: [], columns: [{ field: "order_no", label: "Ордер" }, { field: "line_code", label: "Линия" }, { field: "status", label: "Статус" }, { field: "defect_kg", label: "Брак кг" }], maxRows: 100 },
    { reportId: "mes-defect-pending-recommendations", title: "Рекомендации", description: "Активные рекомендации маршрута", query: "SELECT e.line_code, e.order_no, e.volume_kg, r.target, r.priority, r.rationale, r.status FROM mes_recommendation r JOIN mes_defect_event e ON e.id = r.event_id WHERE r.status IN ('recommended', 'confirmed') ORDER BY r.priority, e.created_at DESC", parameters: [], columns: [{ field: "line_code", label: "Линия" }, { field: "order_no", label: "Ордер" }, { field: "volume_kg", label: "Объём" }, { field: "target", label: "Цель" }, { field: "priority", label: "Приоритет" }, { field: "rationale", label: "Обоснование" }, { field: "status", label: "Статус" }], maxRows: 50 },
  ],
  alertRules: [
    { name: "MES defect pending alert", objectPath: HUB, watchVariable: "defectPending", conditionExpr: 'self.defectPending["value"] > 0', eventName: "mesDefectDetected", payloadVariable: "defectPending", enabled: false, edgeTrigger: false, delaySeconds: 0, sustainWhileTrue: false },
  ],
  events: [
    { id: "mesDefectDetected", roles: ["operator", "admin"] },
    { id: "mesDefectRouted", roles: ["operator", "admin"] },
  ],
  correlators: [
    { name: "MES defect distribution workflow", objectPath: HUB, patternType: "COUNT", eventName: "mesDefectDetected", windowSeconds: 0, minOccurrences: 1, cooldownSeconds: 2, sequenceGapSeconds: 0, actionType: "RUN_WORKFLOW", actionTarget: "root.platform.workflows.mes-defect-distribution", enabled: false },
  ],
  workflows: [
    { path: "root.platform.workflows.mes-defect-distribution", bpmnXml, status: "ACTIVE", operatorAppId: "mes-defect-demo" },
  ],
  dashboards: [
    { path: "root.platform.dashboards.mes-defect-overview", title: "MES Defect Overview", refreshIntervalMs: 5000, layoutJson: JSON.stringify(overviewLayout) },
    { path: "root.platform.dashboards.mes-defect-simulator", title: "SCADA Simulator", refreshIntervalMs: 3000, layoutJson: JSON.stringify(simulatorLayout) },
    { path: "root.platform.dashboards.mes-defect-orders", title: "Orders", refreshIntervalMs: 10000, layoutJson: JSON.stringify(ordersLayout) },
  ],
  operatorUi: {
    appId: "mes-defect-demo",
    title: "MES Defect Demo",
    defaultDashboard: "root.platform.dashboards.mes-defect-overview",
    dashboards: [
      { path: "root.platform.dashboards.mes-defect-overview", title: "Сводка" },
      { path: "root.platform.dashboards.mes-defect-simulator", title: "Симулятор SCADA" },
      { path: "root.platform.dashboards.mes-defect-orders", title: "Ордера" },
    ],
    eventJournalObjectPath: HUB,
  },
};

const outPath = path.join(ROOT, "bundle.json");

/** ASCII-only JSON so Windows PowerShell deploy does not mojibake Cyrillic labels. */
function stringifyBundle(obj) {
  return JSON.stringify(obj, null, 2).replace(
    /[^\x00-\x7F]/g,
    (ch) => `\\u${ch.charCodeAt(0).toString(16).padStart(4, "0")}`
  );
}

fs.writeFileSync(outPath, stringifyBundle(bundle), "utf8");
console.log("Wrote", outPath);
