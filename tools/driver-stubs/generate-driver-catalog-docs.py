#!/usr/bin/env python3
"""Regenerate full driver catalog tables in docs/en/drivers.md and docs/ru/drivers.md."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

PURPOSE_EN = {
    "virtual": "Simulator / virtual device profiles for demos and tests",
    "mqtt": "MQTT client: subscribe topics and optional publish/write",
    "modbus-tcp": "Modbus TCP master (FC read/write holding/input/coils)",
    "modbus-rtu": "Modbus RTU master over serial",
    "modbus-udp": "Modbus UDP master",
    "snmp": "SNMP v1/v2c/v3 GET/SET poll client",
    "http": "HTTP/HTTPS client poll (GET/POST JSON/text)",
    "email": "Outbound email via HTTP relay gateway",
    "sms": "Outbound SMS via HTTP relay gateway",
    "webhook": "Outbound webhook POST JSON notifications",
    "haystack": "Project Haystack HTTP JSON client",
    "http-server": "Embedded HTTP server endpoint for inbound requests",
    "icmp": "ICMP ping reachability / RTT probe",
    "ssh": "SSH remote command execution (JSch)",
    "coap": "CoAP GET client (read-only)",
    "opcua": "OPC UA client (Eclipse Milo): poll/subscribe/write/browse",
    "opcua-server": "OPC UA server (Eclipse Milo) exposing ISPF variables",
    "opc-da": "OPC Classic DA connectivity shell (needs Windows DCOM/bridge)",
    "opc-bridge": "OPC/LON TCP bridge connectivity shell",
    "s7": "Siemens S7 ISO-on-TCP PLC read/write",
    "iec104": "IEC 60870-5-104 client (telecontrol)",
    "iec104-server": "IEC 60870-5-104 server/slave",
    "bacnet": "BACnet/IP client (clean-room codec)",
    "dnp3": "DNP3 TCP master — class poll/read (write not implemented)",
    "ethernet-ip": "EtherNet/IP CIP UCMM Read/Write Tag (Allen-Bradley class)",
    "dlms": "DLMS/COSEM meter master (TCP WRAPPER)",
    "jmx": "JMX local/remote MBean attribute poll",
    "jdbc": "SQL JDBC SELECT poll",
    "odbc": "ODBC via external JDBC bridge JAR (SQL read)",
    "file": "Local file metadata/content poll",
    "folder": "Local directory listing poll",
    "application": "Local shell/script execution mapped to variables",
    "message-stream": "Generic TCP/UDP message stream framing",
    "nmea": "NMEA 0183 GNSS/sensor sentence parse",
    "telnet": "Telnet remote command session",
    "soap": "SOAP HTTP client",
    "ip-host": "Multi-check host probe (PING/HTTP/TCP/DNS/SMTP/FTP)",
    "ldap": "LDAP search probe",
    "dhcp": "DHCP discover probe",
    "imap": "IMAP mailbox poll",
    "pop3": "POP3 mailbox poll",
    "radius": "RADIUS authentication check",
    "ipmi": "IPMI LAN BMC probe",
    "wmi": "Windows WMI via PowerShell (Windows only)",
    "kafka": "Apache Kafka consumer/poll",
    "jms": "JMS client (ActiveMQ-class)",
    "cwmp": "TR-069/CWMP Inform + Get/SetParameterValues",
    "web-transaction": "Multi-step HTTP transaction script",
    "graph-db": "Graph DB query (Neo4j / Gremlin)",
    "vmware": "VMware vSphere SOAP (Login + RetrieveProperties)",
    "smi-s": "SMI-S storage CIM-XML poll",
    "gps-tracker": "GPS/M2M TCP tracker listener",
    "flexible": "Flexible TCP/UDP custom framing poller",
    "mbus": "M-Bus meter protocol (read-only v0.1)",
    "modem-at": "GSM/cellular modem AT commands over TCP/serial",
    "omron-fins": "Omron FINS PLC (read-only v0.1)",
    "asterisk": "Asterisk Manager Interface (AMI) commands",
    "sip": "SIP OPTIONS/REGISTER reachability probe",
    "xmpp": "XMPP messaging client (Smack)",
    "smpp": "SMPP SMSC client",
    "smb": "SMB/CIFS file share access",
    "corba": "CORBA IIOP TCP reachability shell (no ORB in modern JDK)",
    "ingress-syslog": "Syslog UDP listener (raw capture ingress)",
    "ingress-snmp-trap": "SNMP trap UDP listener (raw capture ingress)",
    "ingress-sflow": "sFlow v5 UDP listener (raw capture ingress)",
}

PURPOSE_RU = {
    "virtual": "Симулятор / виртуальные профили устройств для демо и тестов",
    "mqtt": "MQTT-клиент: подписка на топики и опциональная запись/publish",
    "modbus-tcp": "Modbus TCP master (чтение/запись holding/input/coils)",
    "modbus-rtu": "Modbus RTU master по последовательному порту",
    "modbus-udp": "Modbus UDP master",
    "snmp": "SNMP v1/v2c/v3 клиент GET/SET",
    "http": "HTTP/HTTPS клиент (GET/POST JSON/text)",
    "email": "Исходящая почта через HTTP relay",
    "sms": "Исходящие SMS через HTTP relay",
    "webhook": "Исходящие webhook POST JSON уведомления",
    "haystack": "Клиент Project Haystack HTTP JSON",
    "http-server": "Встроенный HTTP-сервер для входящих запросов",
    "icmp": "ICMP ping / проверка доступности и RTT",
    "ssh": "Удалённое выполнение SSH (JSch)",
    "coap": "CoAP GET клиент (только чтение)",
    "opcua": "OPC UA клиент (Eclipse Milo): poll/subscribe/write/browse",
    "opcua-server": "OPC UA сервер (Eclipse Milo), публикация переменных ISPF",
    "opc-da": "Оболочка OPC Classic DA (нужен Windows DCOM/bridge)",
    "opc-bridge": "Оболочка OPC/LON TCP bridge",
    "s7": "Siemens S7 ISO-on-TCP чтение/запись ПЛК",
    "iec104": "IEC 60870-5-104 клиент (телемеханика)",
    "iec104-server": "IEC 60870-5-104 сервер/slave",
    "bacnet": "BACnet/IP клиент (clean-room codec)",
    "dnp3": "DNP3 TCP master — class poll/read (запись не реализована)",
    "ethernet-ip": "EtherNet/IP CIP UCMM Read/Write Tag (класс Allen-Bradley)",
    "dlms": "DLMS/COSEM master счётчиков (TCP WRAPPER)",
    "jmx": "JMX poll атрибутов MBean (local/remote)",
    "jdbc": "SQL JDBC SELECT poll",
    "odbc": "ODBC через внешний JDBC bridge JAR (SQL read)",
    "file": "Опрос локального файла (метаданные/содержимое)",
    "folder": "Опрос содержимого локальной директории",
    "application": "Локальные shell/скрипты → переменные ISPF",
    "message-stream": "Универсальный TCP/UDP поток сообщений",
    "nmea": "Разбор NMEA 0183 (GNSS/датчики)",
    "telnet": "Telnet-сессия удалённых команд",
    "soap": "SOAP HTTP клиент",
    "ip-host": "Мультипроверка хоста (PING/HTTP/TCP/DNS/SMTP/FTP)",
    "ldap": "LDAP search probe",
    "dhcp": "DHCP discover probe",
    "imap": "Опрос IMAP почтового ящика",
    "pop3": "Опрос POP3 почтового ящика",
    "radius": "Проверка RADIUS authentication",
    "ipmi": "IPMI LAN BMC probe",
    "wmi": "Windows WMI через PowerShell (только Windows)",
    "kafka": "Apache Kafka consumer/poll",
    "jms": "JMS клиент (класс ActiveMQ)",
    "cwmp": "TR-069/CWMP Inform + Get/SetParameterValues",
    "web-transaction": "Многошаговый HTTP transaction script",
    "graph-db": "Запросы к графовой БД (Neo4j / Gremlin)",
    "vmware": "VMware vSphere SOAP (Login + RetrieveProperties)",
    "smi-s": "SMI-S storage CIM-XML poll",
    "gps-tracker": "TCP listener GPS/M2M трекеров",
    "flexible": "Гибкий TCP/UDP poller с настраиваемым framing",
    "mbus": "M-Bus протокол счётчиков (read-only v0.1)",
    "modem-at": "GSM/cellular AT-команды (TCP/serial)",
    "omron-fins": "Omron FINS ПЛК (read-only v0.1)",
    "asterisk": "Asterisk Manager Interface (AMI)",
    "sip": "SIP OPTIONS/REGISTER probe доступности",
    "xmpp": "XMPP клиент (Smack)",
    "smpp": "SMPP SMSC клиент",
    "smb": "Доступ к SMB/CIFS шарам",
    "corba": "CORBA IIOP TCP shell (без ORB в современном JDK)",
    "ingress-syslog": "Syslog UDP listener (сырой ingress)",
    "ingress-snmp-trap": "SNMP trap UDP listener (сырой ingress)",
    "ingress-sflow": "sFlow v5 UDP listener (сырой ingress)",
}

BETA = {"opc-da", "opc-bridge", "corba"}


def load_stub_meta() -> dict[str, dict[str, str]]:
    stub_meta: dict[str, dict[str, str]] = {}
    cur = None
    for line in (ROOT / "tools/driver-stubs/protocol-stubs.yaml").read_text(encoding="utf-8").splitlines():
        if line.startswith("  - id:"):
            cur = line.split(":", 1)[1].strip()
            stub_meta[cur] = {}
        elif cur and line.startswith("    name:"):
            stub_meta[cur]["name"] = line.split(":", 1)[1].strip()
        elif cur and line.startswith("    description:"):
            stub_meta[cur]["description"] = line.split(":", 1)[1].strip()
    return stub_meta


def maturity(driver_id: str, stubs: set[str]) -> str:
    if driver_id in stubs:
        return "STUB"
    if driver_id in BETA:
        return "BETA"
    return "PRODUCTION"


def en_purpose(driver_id: str, module: str, entry: dict, stub_meta: dict) -> str:
    if driver_id in PURPOSE_EN:
        return PURPOSE_EN[driver_id]
    if driver_id in stub_meta:
        return stub_meta[driver_id].get("description") or stub_meta[driver_id].get("name") or driver_id
    cls = entry["driverClass"]
    path = ROOT / "packages" / module / "src/main/java" / (cls.replace(".", "/") + ".java")
    if path.exists():
        text = path.read_text(encoding="utf-8", errors="ignore")
        match = re.search(
            r'new DriverMetadata\(\s*"[^"]+"\s*,\s*"[^"]+"\s*,\s*"[^"]*"\s*,\s*"([^"]*)"',
            text,
            re.S,
        )
        if match and match.group(1).strip():
            return match.group(1).strip()
    return f"{driver_id} device driver"


def ru_purpose(driver_id: str, en: str, stub_meta: dict) -> str:
    if driver_id in PURPOSE_RU:
        return PURPOSE_RU[driver_id]
    if driver_id in stub_meta:
        name = stub_meta[driver_id].get("name", driver_id)
        desc = stub_meta[driver_id].get("description", "")
        return f"{name}: {desc} (stub TCP-доступности; codec пока не реализован)"
    return en


def build_rows():
    catalog = json.loads((ROOT / "gradle/driver-packs.json").read_text(encoding="utf-8"))
    stubs = set(
        json.loads(
            (ROOT / "packages/ispf-server/src/main/resources/driver-pack/protocol-stub-ids.json").read_text(
                encoding="utf-8"
            )
        )["driverIds"]
    )
    stub_meta = load_stub_meta()
    rows = []
    for module, entry in sorted(catalog.items(), key=lambda item: item[1]["driverId"]):
        driver_id = entry["driverId"]
        en = en_purpose(driver_id, module, entry, stub_meta)
        rows.append(
            (
                driver_id,
                module,
                maturity(driver_id, stubs),
                entry.get("licenseType", "Apache-2.0"),
                en,
                ru_purpose(driver_id, en, stub_meta),
            )
        )
    return rows


def render_en(rows) -> str:
    lines = [
        "## Registered driver catalog (162)",
        "",
        "The `maturity` field in `GET /api/v1/drivers`: `PRODUCTION` (default), `BETA`, `STUB`. Labels are set in `DriverMaturityRegistry` on the server and shown in the Web Console when selecting a driver.",
        "",
        "The `capabilities` field — string set from `DriverProductionMatrix` (ADR-0022): `read`, `write`, `subscribe`, `discovery`, `observed_at`, `quality`. Example: `opcua` → `read`, `write`, `subscribe`, `discovery`, `observed_at`.",
        "",
        "### Stub promotion (demand-driven)",
        "",
        "58 core `driverId` values are in the production matrix; **97** additional protocol-catalog stubs ship as individual `ispf-driver-<id>` packs (**STUB**, TCP reachability only, **Apache-2.0**). Promotion to **PRODUCTION** is **not** on a roadmap schedule, but **on request from the app team** through the gate [0002-dogfooding-gate](decisions/0002-dogfooding-gate.md):",
        "",
        "1. The app team describes the scenario (device, point mapping, acceptance test).",
        "2. A platform PR adds protocol logic (replace the stub class in `ispf-driver-<id>`).",
        "3. `DriverMaturityRegistry` / stub id list is updated; documentation in this file.",
        "",
        "Current STUB/BETA candidates:",
        "",
        "| `driverId` | Maturity | Note |",
        "|------------|----------|------|",
        "| `corba` | BETA | CORBA IIOP TCP shell — needs a third-party ORB |",
        "| `opc-da`, `opc-bridge` | BETA | Classic OPC shells — prefer `opcua` or external DA→UA |",
        "| Protocol catalog (`sparkplug-b`, `iec61850`, `profinet`, `beckhoff-ads`, `knx`, `lorawan`, …) | STUB | Generated one pack per id from `tools/driver-stubs/protocol-stubs.yaml` (shared base `ispf-driver-stub-kit`) |",
        "| `vmware` | PRODUCTION | vSphere SOAP: Login + RetrieveProperties |",
        "| `smi-s` | PRODUCTION | SMI-S CIM-XML parse |",
        "",
        "Loopback tests (BL-26): `EthernetIpDeviceDriverTest`, `OpcDaDeviceDriverTest`, `OpcBridgeDeviceDriverTest`, `CorbaDeviceDriverTest`, `VmwareDeviceDriverTest` (`useHttp`), `SmisDeviceDriverTest` (`useHttp`).",
        "",
        "See [ROADMAP.md § Phase 17.4](roadmap.md).",
        "",
        "### Complete `driverId` catalog",
        "",
        "What each driver does (all packs from `gradle/driver-packs.json`):",
        "",
        "| `driverId` | Module | Maturity | License | What it does |",
        "|------------|--------|----------|---------|--------------|",
    ]
    for driver_id, module, mat, lic, en, _ru in rows:
        lines.append(f"| `{driver_id}` | `{module}` | {mat} | {lic} | {en.replace('|', '\\|')} |")
    lines.append("")
    lines.append(
        "Detailed configs for base drivers — in the sections below. Others follow the same pattern: `driverConfigJson` + `driverPointMappingsJson`, see `DriverMetadata` in the module."
    )
    return "\n".join(lines) + "\n"


def render_ru(rows) -> str:
    lines = [
        "## Зарегистрированный каталог драйверов (162)",
        "",
        "Поле `maturity` в `GET /api/v1/drivers`: `PRODUCTION` (по умолчанию), `BETA`, `STUB`. Метки задаются в `DriverMaturityRegistry` на сервере и отображаются в Web Console при выборе драйвера.",
        "",
        "Поле `capabilities` — строковый набор из `DriverProductionMatrix` (ADR-0022): `read`, `write`, `subscribe`, `discovery`, `observed_at`, `quality`. Пример: `opcua` → `read`, `write`, `subscribe`, `discovery`, `observed_at`.",
        "",
        "### Продвижение stub (по запросу)",
        "",
        "58 основных `driverId` в production-матрице; **97** дополнительных stub-протоколов — отдельные пакеты `ispf-driver-<id>` (**STUB**, только TCP, **Apache-2.0**). Продвижение до **PRODUCTION** — **не** по расписанию roadmap, а **по запросу команды приложения** через gate [0002-dogfooding-gate](decisions/0002-dogfooding-gate.md):",
        "",
        "1. Команда приложения описывает сценарий (устройство, маппинг точек, приёмочный тест).",
        "2. PR платформы добавляет протокольную логику (замена stub-класса в `ispf-driver-<id>`).",
        "3. Обновляется `DriverMaturityRegistry` / список stub id; документация в этом файле.",
        "",
        "Текущие кандидаты STUB/BETA:",
        "",
        "| `driverId` | Зрелость | Примечание |",
        "|------------|----------|---------|",
        "| `corba` | BETA | CORBA IIOP TCP shell — нужна сторонняя ORB |",
        "| `opc-da`, `opc-bridge` | BETA | Classic OPC shells — предпочтительнее `opcua` или внешний DA→UA |",
        "| Каталог протоколов (`sparkplug-b`, `iec61850`, `profinet`, `beckhoff-ads`, `knx`, `lorawan`, …) | STUB | Генерация: один pack на id из `tools/driver-stubs/protocol-stubs.yaml` (база `ispf-driver-stub-kit`) |",
        "| `vmware` | PRODUCTION | vSphere SOAP: Login + RetrieveProperties |",
        "| `smi-s` | PRODUCTION | парсинг SMI-S CIM-XML |",
        "",
        "Loopback-тесты (BL-26): `EthernetIpDeviceDriverTest`, `OpcDaDeviceDriverTest`, `OpcBridgeDeviceDriverTest`, `CorbaDeviceDriverTest`, `VmwareDeviceDriverTest` (`useHttp`), `SmisDeviceDriverTest` (`useHttp`).",
        "",
        "См. [ROADMAP.md § Phase 17.4](roadmap.md).",
        "",
        "### Полный каталог `driverId`",
        "",
        "Что делает каждый драйвер (все packs из `gradle/driver-packs.json`):",
        "",
        "| `driverId` | Модуль | Зрелость | Лицензия | Назначение |",
        "|------------|--------|----------|----------|------------|",
    ]
    for driver_id, module, mat, lic, _en, ru in rows:
        lines.append(f"| `{driver_id}` | `{module}` | {mat} | {lic} | {ru.replace('|', '\\|')} |")
    lines.append("")
    lines.append(
        "Подробные конфиги базовых драйверов — в разделах ниже. Остальные следуют тому же шаблону: `driverConfigJson` + `driverPointMappingsJson`, см. `DriverMetadata` в модуле."
    )
    return "\n".join(lines) + "\n"


def splice(path: Path, start_markers: tuple[str, ...], end_marker: str, body: str) -> None:
    text = path.read_text(encoding="utf-8")
    start = None
    used = None
    for marker in start_markers:
        if marker in text:
            start = text.index(marker)
            used = marker
            break
    if start is None:
        raise SystemExit(f"Start marker not found in {path}: {start_markers}")
    end = text.index(end_marker, start)
    path.write_text(text[:start] + body.rstrip() + "\n\n" + text[end:], encoding="utf-8")
    print(f"updated {path} (from {used})")


def main() -> None:
    rows = build_rows()
    assert len(rows) == 162, len(rows)
    en_body = render_en(rows)
    ru_body = render_ru(rows)
    (ROOT / "tools/driver-stubs/generated-catalog-en.md").write_text(en_body, encoding="utf-8")
    (ROOT / "tools/driver-stubs/generated-catalog-ru.md").write_text(ru_body, encoding="utf-8")
    splice(
        ROOT / "docs/en/drivers.md",
        ("## Registered driver catalog (162)", "## Registered driver catalog (58)"),
        "### v0.1 limitations (native / full stack required)",
        en_body,
    )
    splice(
        ROOT / "docs/ru/drivers.md",
        ("## Зарегистрированный каталог драйверов (162)", "## Каталог зарегистрированных драйверов (58)"),
        "### Ограничения v0.1 (нужен native / полный стек)",
        ru_body,
    )
    print(f"catalog rows: {len(rows)}")


if __name__ == "__main__":
    main()
