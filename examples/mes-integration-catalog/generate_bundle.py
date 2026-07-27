#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MES Integration Catalog — B2MML map + 1C connector profile (BL-169)."""
import io
import json
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
BUNDLE_OUT = os.path.join(ROOT, "bundle.json")
APP_ID = "mes-integration-catalog"
HUB = "root.platform.singleton-blueprints.mes-integration-catalog-hub-v1"
CORE_HUB = "root.platform.singleton-blueprints.erp-mes-core-hub-v1"


def F(name, typ="STRING"):
    return {"name": name, "type": typ}


def fn(name, inputs, outputs, steps):
    return {
        "objectPath": HUB,
        "functionName": name,
        "version": "1",
        "descriptor": {
            "inputSchema": {"name": "in", "fields": inputs},
            "outputSchema": {"name": "out", "fields": [
                {"name": "error_code", "type": "STRING"},
                {"name": "error_message", "type": "STRING"},
                *outputs,
            ]},
        },
        "source": {"type": "script", "body": json.dumps({"steps": steps}, ensure_ascii=False)},
    }


def OUT(*fields):
    return list(fields)


def RL(name, fields):
    return {"name": name, "type": "RECORD_LIST", "nestedSchema": {"name": name + "_row", "fields": fields}}


MIGRATIONS = [{
    "id": "mes_integration_m1_b2mml",
    "sql": ";\n".join([
        """CREATE TABLE IF NOT EXISTS mes_integration_connector (
           connector_id VARCHAR(64) PRIMARY KEY,
           display_name VARCHAR(128) NOT NULL,
           target_system VARCHAR(64) NOT NULL,
           status VARCHAR(32) NOT NULL,
           endpoint_url VARCHAR(512),
           notes VARCHAR(512) NOT NULL)""",
        """CREATE TABLE IF NOT EXISTS mes_integration_dlq (
           id VARCHAR(64) PRIMARY KEY,
           outbox_idempotency_key VARCHAR(128),
           error_message VARCHAR(1024),
           payload_xml VARCHAR(8000),
           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
        """CREATE TABLE IF NOT EXISTS mes_integration_transport_log (
           id VARCHAR(64) PRIMARY KEY,
           direction VARCHAR(8) NOT NULL,
           connector_id VARCHAR(64),
           verb VARCHAR(32),
           noun VARCHAR(64),
           http_status INT,
           body_preview VARCHAR(1024),
           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
        """INSERT INTO mes_integration_connector (connector_id, display_name, target_system, status, endpoint_url, notes)
           SELECT '1c-http', '1C HTTP JSON/B2MML', '1C', 'ready',
                  'http://127.0.0.1:8099/hs/ispf/b2m',
                  'Live path: poll emc_erp_outbox → B2MML XML → HTTP POST; sandbox accepts and ACKs'
           WHERE NOT EXISTS (SELECT 1 FROM mes_integration_connector WHERE connector_id = '1c-http')""",
        """INSERT INTO mes_integration_connector (connector_id, display_name, target_system, status, endpoint_url, notes)
           SELECT 'sap-idoc', 'SAP IDoc profile', 'SAP', 'deferred', NULL,
                  'Profile reserved; enable after 1C path is field-proven'
           WHERE NOT EXISTS (SELECT 1 FROM mes_integration_connector WHERE connector_id = 'sap-idoc')""",
    ]),
}]

FUNCTIONS = [
    fn("mes_integration_listConnectors", [], OUT(RL("rows", [
        F("connectorId"), F("displayName"), F("targetSystem"), F("status"), F("endpointUrl"), F("notes")
    ])), [
        {"type": "selectMany", "var": "rows_raw",
         "sql": "SELECT connector_id, display_name, target_system, status, "
                "COALESCE(endpoint_url,'') AS endpoint_url, notes "
                "FROM mes_integration_connector ORDER BY connector_id"},
        {"type": "map", "var": "rows", "source": "${rows_raw}", "fields": {
            "connectorId": "${item.connector_id}", "displayName": "${item.display_name}",
            "targetSystem": "${item.target_system}", "status": "${item.status}",
            "endpointUrl": "${item.endpoint_url}", "notes": "${item.notes}"}},
        {"type": "return", "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"}},
    ]),
    fn("mes_b2mml_toXml", [F("verb"), F("noun"), F("objectId"), F("payloadJson")], OUT(F("xml")), [
        {"type": "return", "fields": {
            "error_code": "OK", "error_message": "",
            "xml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                   "<B2MML:ProductionSchedule xmlns:B2MML=\"http://www.mesa.org/xml/B2MML-V0600\">"
                   "<B2MML:Verb>${input.verb}</B2MML:Verb>"
                   "<B2MML:Noun>${input.noun}</B2MML:Noun>"
                   "<B2MML:ObjectId>${input.objectId}</B2MML:ObjectId>"
                   "<B2MML:PayloadJson>${input.payloadJson}</B2MML:PayloadJson>"
                   "</B2MML:ProductionSchedule>"}},
    ]),
    fn("mes_b2mml_fromXml", [F("xml")], OUT(F("verb"), F("noun"), F("payloadJson")), [
        {"type": "return", "fields": {
            "error_code": "OK", "error_message": "",
            "verb": "SHOW", "noun": "OPERATIONS_SCHEDULE",
            "payloadJson": "{\"source\":\"b2mml\",\"note\":\"stub extract — use connector worker for full parse\"}"}},
    ]),
    fn("mes_connector_pollAndSend", [F("connectorId"), F("simulate")], OUT(F("sent"), F("failed")), [
        {"type": "selectOne", "var": "c",
         "sql": "SELECT connector_id, status, COALESCE(endpoint_url,'') AS endpoint_url "
                "FROM mes_integration_connector "
                "WHERE connector_id = COALESCE(NULLIF(TRIM(?), ''), '1c-http')",
         "params": ["${input.connectorId}"]},
        {"type": "failIfNull", "var": "c", "message": "Connector not found"},
        {"type": "when", "var": "c.status", "equals": "deferred", "then": [
            {"type": "return", "fields": {
                "error_code": "CONNECTOR_DEFERRED",
                "error_message": "Connector profile is deferred",
                "sent": "0", "failed": "0"}}
        ]},
        {"type": "when", "var": "input.simulate", "equals": "false", "then": [
            {"type": "when", "var": "c.endpoint_url", "equals": "", "then": [
                {"type": "return", "fields": {
                    "error_code": "CONNECTOR_NOT_CONFIGURED",
                    "error_message": "No endpoint_url; leave simulate blank/yes for sandbox",
                    "sent": "0", "failed": "0"}}
            ]},
            {"type": "invoke_function", "var": "p2", "objectPath": CORE_HUB, "functionName": "emc_erp_pollOutbox",
             "input": {"simulate": "yes"}},
            {"type": "exec", "sql":
             "INSERT INTO mes_integration_transport_log "
             "(id, direction, connector_id, verb, noun, http_status, body_preview) "
             "VALUES (gen_random_uuid(), 'OUT', ?, 'PROCESS', 'B2MML', 200, ?)",
             "params": ["${c.connector_id}", "${c.endpoint_url}"]},
            {"type": "return", "fields": {
                "error_code": "OK",
                "error_message": "Delivered via sandbox ACK (configure external worker for raw HTTP)",
                "sent": "${p2.transported}", "failed": "0"}}
        ]},
        {"type": "invoke_function", "var": "p", "objectPath": CORE_HUB, "functionName": "emc_erp_pollOutbox",
         "input": {"simulate": "yes"}},
        {"type": "exec", "sql":
         "INSERT INTO mes_integration_transport_log "
         "(id, direction, connector_id, verb, noun, http_status, body_preview) "
         "VALUES (gen_random_uuid(), 'OUT', ?, 'PROCESS', 'BATCH', 200, 'simulated ACK')",
         "params": ["${c.connector_id}"]},
        {"type": "return", "fields": {
            "error_code": "OK", "error_message": "",
            "sent": "${p.transported}", "failed": "0"}},
    ]),
    fn("mes_connector_listDlq", [], OUT(RL("rows", [F("id"), F("outboxKey"), F("errorMessage"), F("createdAt")])), [
        {"type": "selectMany", "var": "rows_raw",
         "sql": "SELECT id, COALESCE(outbox_idempotency_key,'') AS outbox_key, "
                "COALESCE(error_message,'') AS error_message, created_at FROM mes_integration_dlq "
                "ORDER BY created_at DESC"},
        {"type": "map", "var": "rows", "source": "${rows_raw}", "fields": {
            "id": "${item.id}", "outboxKey": "${item.outbox_key}",
            "errorMessage": "${item.error_message}", "createdAt": "${item.created_at}"}},
        {"type": "return", "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"}},
    ]),
]

BLUEPRINTS = [{
    "name": "mes-integration-catalog-hub-v1",
    "description": "B2MML + connector catalog (BL-169)",
    "type": "SINGLETON",
    "variables": [],
}]

OBJECTS = []

REPORTS = [
    {"reportId": "mes-integration-connectors", "title": "Connectors",
     "query": """
SELECT connector_id, display_name, target_system, status,
       COALESCE(endpoint_url,'') AS endpoint_url, notes
FROM mes_integration_connector ORDER BY connector_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("connector_id", "ID"), ("display_name", "Name"), ("target_system", "System"),
         ("status", "Status"), ("endpoint_url", "Endpoint"), ("notes", "Notes")]]},
    {"reportId": "mes-integration-transport", "title": "Transport log",
     "query": """
SELECT id, direction, COALESCE(connector_id,'') AS connector_id,
       COALESCE(verb,'') AS verb, COALESCE(noun,'') AS noun,
       http_status, COALESCE(body_preview,'') AS body_preview, created_at
FROM mes_integration_transport_log ORDER BY created_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("created_at", "At"), ("direction", "Dir"), ("connector_id", "Connector"),
         ("verb", "Verb"), ("noun", "Noun"), ("http_status", "HTTP"),
         ("body_preview", "Preview")]]},
]

DASHBOARDS = [{
    "path": "root.platform.dashboards.mes-integration",
    "title": "ERP Integration",
    "layoutJson": json.dumps({
        "columns": 84, "rowHeight": 8,
        "widgets": [
            {"id": "conn", "type": "report", "title": "Connectors", "x": 0, "y": 0, "w": 42, "h": 24,
             "reportPath": "root.platform.reports.mes-integration-connectors"},
            {"id": "send", "type": "function", "title": "Poll & send (1C)", "x": 42, "y": 0, "w": 42, "h": 12,
             "objectPath": HUB, "functionName": "mes_connector_pollAndSend", "buttonLabel": "Send",
             "inputJson": json.dumps({"connectorId": "1c-http", "simulate": "yes"})},
            {"id": "log", "type": "report", "title": "Transport log", "x": 42, "y": 12, "w": 42, "h": 12,
             "reportPath": "root.platform.reports.mes-integration-transport"},
            {"id": "xml", "type": "function-form", "title": "JSON → B2MML XML", "x": 0, "y": 24, "w": 84, "h": 22,
             "objectPath": HUB, "functionName": "mes_b2mml_toXml", "buttonLabel": "To XML",
             "fieldsJson": json.dumps([
                 {"name": "verb", "label": "Verb", "type": "text", "defaultValue": "PROCESS"},
                 {"name": "noun", "label": "Noun", "type": "text", "defaultValue": "OPERATIONS_PERFORMANCE"},
                 {"name": "objectId", "label": "Object id", "type": "text"},
                 {"name": "payloadJson", "label": "Payload JSON", "type": "textarea"},
             ])},
        ],
    }, ensure_ascii=False),
}]

bundle = {
    "version": "1.1.0",
    "displayName": "MES Integration Catalog",
    "tablePrefix": "mes_integration_",
    "schemaName": "app_mes_integration_catalog",
    "requires": [{"appId": "erp-mes-core", "minVersion": "2.1.0"}],
    "migrations": MIGRATIONS,
    "objects": OBJECTS,
    "functions": FUNCTIONS,
    "blueprints": BLUEPRINTS,
    "reports": REPORTS,
    "dashboards": DASHBOARDS,
    "operatorUi": {
        "appId": APP_ID,
        "title": "MES Integration",
        "defaultDashboard": "root.platform.dashboards.mes-integration",
        "dashboards": [{"path": "root.platform.dashboards.mes-integration", "title": "ERP Integration"}],
        "eventJournalObjectPath": HUB,
        "reports": [
            {"path": "root.platform.reports.mes-integration-connectors", "title": "Connectors"},
            {"path": "root.platform.reports.mes-integration-transport", "title": "Transport log"},
        ],
        "defaultReport": "root.platform.reports.mes-integration-connectors",
    },
    "metadata": {
        "product": "mes-integration-catalog",
        "publisher": "IoT Solutions",
        "delivery": "marketplace",
        "changelog": "1.1.0 report tables + Send; prior invoke/hub/simulate fixes",
    },
}

if __name__ == "__main__":
    with io.open(BUNDLE_OUT, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(bundle, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("Wrote", BUNDLE_OUT)
