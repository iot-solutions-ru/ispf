#!/usr/bin/env python3
"""Apply IoT/SCADA/MES focus to index.html (source with asset paths)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
HTML = ROOT / "index.html"

PLATFORM_SECTION = """
  <section id="platform" class="platform-section">
    <div class="wrap">
      <div class="reveal">
        <div class="section-label industrial">IoT · SCADA · MES</div>
        <h2 class="section-title">Три уровня автоматизации — один продукт</h2>
        <p class="section-desc">
          Классический проект: OPC-сервер + historian + SCADA + MES + интеграция.
          ISPF закрывает весь стек declarative-конфигурацией на дереве объектов —
          от полевого уровня до производственных отчётов и operator HMI.
        </p>
      </div>
      <div class="platform-triad reveal">
        <div class="platform-card">
          <span class="tag">IoT · полевой уровень</span>
          <h3>Подключение и нормализация данных</h3>
          <p>Modbus, OPC UA, SNMP, MQTT, JDBC, Kafka — 58 драйверов. Auto-provisioning, virtual profiles, CEL-bindings.</p>
          <ul>
            <li>MQTT Meter Bus · auto-create instances</li>
            <li>Per-object ACL и federation bind</li>
            <li>WebSocket live-updates переменных</li>
          </ul>
        </div>
        <div class="platform-card">
          <span class="tag">SCADA · оперативный контур</span>
          <h3>Мониторинг, HMI и управление</h3>
          <p>Dashboard builder, operator shell, alarm rules, correlators, SLD, map, network graph, historian.</p>
          <ul>
            <li>Мини-ТЭЦ: ГПУ, ГРПБ, РУМБ, ДГУ</li>
            <li>SNMP / IT + промышленные объекты</li>
            <li>Work queue · журнал событий · alarm bar</li>
          </ul>
        </div>
        <div class="platform-card">
          <span class="tag">MES · производственный слой</span>
          <h3>Учёт, процессы и отчёты</h3>
          <p>Application platform: bundle deploy, SQL-отчёты, BPMN, JSON-функции, scheduler — без Java в ядре.</p>
          <ul>
            <li>warehouse-app · mes-reference · mini-tec</li>
            <li>User tasks и эскалация в BPMN</li>
            <li>Operator reports · Excel export</li>
          </ul>
        </div>
      </div>
    </div>
  </section>
"""


def extract_section(html: str, section_id: str) -> tuple[str, str, str] | None:
    pat = rf'(\s*<section id="{section_id}"[^>]*>.*?</section>)'
    m = re.search(pat, html, re.DOTALL)
    if not m:
        return None
    return html[: m.start()], m.group(1), html[m.end() :]


def main() -> None:
    html = HTML.read_text(encoding="utf-8")
    if "data:image/png;base64," in html:
        raise SystemExit("Run _restore_assets.py first — index.html must use assets/screenshots paths")

    # CSS
    if ".platform-section" not in html:
        css = """
    .platform-section {
      padding: 100px 0;
      background: linear-gradient(180deg, rgba(59,130,246,0.07) 0%, transparent 55%);
      border-bottom: 1px solid var(--border);
    }
    .platform-triad {
      display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; margin-top: 48px;
    }
    .platform-card {
      padding: 28px; border-radius: var(--radius-lg); border: 1px solid var(--border);
      background: var(--bg-card); transition: border-color 0.25s var(--ease), transform 0.25s var(--ease);
    }
    .platform-card:hover { border-color: rgba(59,130,246,0.4); transform: translateY(-2px); }
    .platform-card .tag {
      font-size: 0.68rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em;
      color: var(--cyan); margin-bottom: 12px; display: block;
    }
    .platform-card h3 { font-size: 1.2rem; font-weight: 800; margin-bottom: 10px; }
    .platform-card p { font-size: 0.88rem; color: var(--text-muted); margin-bottom: 14px; }
    .platform-card ul { list-style: none; display: flex; flex-direction: column; gap: 6px; }
    .platform-card li { font-size: 0.82rem; color: var(--text-muted); padding-left: 14px; position: relative; }
    .platform-card li::before { content: "•"; position: absolute; left: 0; color: var(--accent); }
    .section-label.industrial { color: var(--cyan); }
"""
        html = html.replace("    .ai-section {", css + "\n    .ai-section {")
        html = html.replace(
            ".hero-grid, .feature-row, .compare, .gallery, .ai-dual { grid-template-columns: 1fr; }",
            ".hero-grid, .feature-row, .compare, .gallery, .ai-dual, .platform-triad { grid-template-columns: 1fr; }",
        )

    html = html.replace(
        "<title>ISPF — SCADA-платформа с ИИ-студией и операторским агентом</title>",
        "<title>ISPF — IoT · SCADA · MES на одной платформе</title>",
    )
    html = html.replace(
        'content="ISPF: ИИ-студия для разработчиков (tree-first agent, bundle deploy) и ИИ-агент в operator HMI с базой знаний — SCADA, HMI, BPMN на одном дереве объектов."',
        'content="ISPF — единая платформа IoT, SCADA и MES: 58 драйверов, HMI, historian, BPMN, bundle deploy. Энергетика, нефтегаз, производство."',
    )

    html = re.sub(
        r"<ul class=\"nav-links\">.*?</ul>",
        """<ul class="nav-links">
        <li><a href="#platform">IoT · SCADA · MES</a></li>
        <li><a href="#features">Возможности</a></li>
        <li><a href="#screenshots">Интерфейс</a></li>
        <li><a href="#ai">ИИ</a></li>
        <li><a href="#stack">Стек</a></li>
      </ul>""",
        html,
        count=1,
        flags=re.DOTALL,
    )

    # Hero text + image
    html = html.replace(
        '<div class="badge ai"><span class="badge-dot"></span> ИИ-студия + операторский агент · v0.9.41</div>',
        '<div class="badge"><span class="badge-dot"></span> IoT · SCADA · MES · Production v0.9.41</div>',
    )
    html = html.replace(
        "<h1>SCADA с <em>встроенным ИИ</em> —<br>от промпта до operator HMI</h1>",
        "<h1>Единая платформа<br><em>IoT, SCADA и MES</em></h1>",
    )
    html = html.replace(
        """        <p class="hero-lead">
          <strong>ИИ-студия</strong> создаёт устройства, дашборды и bundle deploy на живом дереве объектов.
          <strong>ИИ-агент</strong> в operator app отвечает оператору на языке вашей отрасли —
          с базой знаний, отчётами и навигацией по HMI.
        </p>""",
        """        <p class="hero-lead">
          Сбор данных с поля, operator HMI, historian, алерты и производственные процессы —
          вокруг одного дерева объектов. Энергетика, нефтегаз, дискретное производство:
          от датчика Modbus до MES-отчёта без зоопарка OPC, historian и отдельного MES.
        </p>""",
    )
    html = html.replace('href="#ai">Как работает ИИ', 'href="#platform">IoT · SCADA · MES')
    html = html.replace(
        """        <div class="hero-stats">
          <div class="stat-item"><strong>2</strong><span>ИИ-роли: студия и оператор</span></div>
          <div class="stat-item"><strong>Tree-first</strong><span>агент на object tree</span></div>
          <div class="stat-item"><strong>RAG</strong><span>база знаний приложения</span></div>
        </div>""",
        """        <div class="hero-stats">
          <div class="stat-item"><strong>58</strong><span>протокольных драйверов</span></div>
          <div class="stat-item"><strong>Historian</strong><span>TimescaleDB · CSV export</span></div>
          <div class="stat-item"><strong>MES</strong><span>bundle deploy · SQL reports</span></div>
        </div>""",
    )
    html = html.replace("screenshot-frame ai-frame reveal", "screenshot-frame reveal", 1)
    html = html.replace(
        '<span class="chrome-url">?mode=operator · mini-tec · ИИ-помощник</span>',
        '<span class="chrome-url">ispf.iot-solutions.ru · SNMP Host Monitoring</span>',
        1,
    )
    html = html.replace(
        'src="assets/screenshots/operator-hmi.png" alt="Operator HMI Мини-ТЭЦ: оператор спрашивает ИИ-помощника о сменном отчёте"',
        'src="assets/screenshots/hero-dashboard.png" alt="SCADA дашборд SNMP Host Monitoring: CPU, память, сеть"',
        1,
    )
    html = html.replace(
        "<figcaption class=\"screenshot-caption\">«Запусти сменный отчёт и кратко опиши цифры» — агент знает контекст приложения</figcaption>",
        "<figcaption class=\"screenshot-caption\">SCADA-мониторинг: CPU, MEM, NET · historian · live charts</figcaption>",
        1,
    )

    # Stats
    html = re.sub(
        r'<div class="stats-grid reveal">.*?</div>\s*</div>\s*</div>\s*\n\n  <section id="ai"',
        """<div class="stats-grid reveal">
        <div><div class="num">IoT</div><div class="lbl">58 драйверов · MQTT · OPC UA</div></div>
        <div><div class="num">SCADA</div><div class="lbl">HMI · historian · алерты</div></div>
        <div><div class="num">MES</div><div class="lbl">Bundle · отчёты · workflow</div></div>
        <div><div class="num">58</div><div class="lbl">Device drivers</div></div>
        <div><div class="num">40+</div><div class="lbl">HMI-виджетов</div></div>
        <div><div class="num">BPMN</div><div class="lbl">Work queue · user tasks</div></div>
      </div>
    </div>
  </div>

  <section id="ai" """,
        html,
        count=1,
        flags=re.DOTALL,
    )

    # Platform section before features (after ai block we'll reorder)
    if 'id="platform"' not in html:
        html = html.replace("\n  <section id=\"features\">", PLATFORM_SECTION + "\n\n  <section id=\"features\">", 1)

    # AI section tone down
    html = html.replace(
        '<div class="section-label ai">Artificial Intelligence</div>',
        '<div class="section-label ai">ИИ-слой · дополнительно</div>',
    )
    html = html.replace(
        "<h2 class=\"section-title\">Два ИИ — одна платформа, разные роли</h2>",
        "<h2 class=\"section-title\">ИИ ускоряет внедрение SCADA и MES</h2>",
    )
    html = html.replace(
        """        <p class="section-desc">
          ISPF не «прикручивает ChatGPT сбоку». ИИ встроен в продукт: администратор строит решения
          в ИИ-студии, оператор работает с контекстным агентом прямо на HMI — без отдельного приложения.
        </p>""",
        """        <p class="section-desc">
          ИИ-студия ускоряет настройку IoT/SCADA (устройства, дашборды, bundle).
          ИИ-агент на operator HMI помогает с отчётами и регламентами — поверх готового MES/HMI.
        </p>""",
    )
    html = html.replace(
        '<div class="ai-flow-step"><strong>Промпт</strong><span>«Создай SNMP-мониторинг и дашборд»</span></div>',
        '<div class="ai-flow-step"><strong>IoT</strong><span>Драйвер · device · variables</span></div>',
    )
    html = html.replace(
        '<div class="ai-flow-step"><strong>ИИ-студия</strong><span>tree-first agent + tools</span></div>',
        '<div class="ai-flow-step"><strong>SCADA</strong><span>Dashboard · alerts · historian</span></div>',
    )
    html = html.replace(
        '<div class="ai-flow-step"><strong>Дерево объектов</strong><span>device · dashboard · alerts</span></div>',
        '<div class="ai-flow-step"><strong>MES</strong><span>Reports · BPMN · functions</span></div>',
    )
    html = html.replace(
        '<div class="ai-flow-step"><strong>Operator HMI</strong><span>ИИ-агент + knowledge base</span></div>',
        '<div class="ai-flow-step"><strong>+ ИИ</strong><span>Студия · operator agent</span></div>',
    )

    # Features
    html = html.replace(
        "<h2 class=\"section-title\">Всё, что нужно промышленной автоматизации — в одном месте</h2>",
        "<h2 class=\"section-title\">SCADA/MES без зоопарка модулей</h2>",
    )
    html = html.replace(
        """        <p class="section-desc">
          Типичная SCADA разрастается отдельными OPC-сервером, historian, HMI и MES-модулями.
          ISPF заменяет этот зоопарк единой платформой с одной моделью данных и одним Web Console.
        </p>""",
        """        <p class="section-desc">
          OPC-сервер, historian, HMI, workflow и MES-логика — узлы одного дерева объектов
          с единым REST/WebSocket API. Reference apps: энергетика (mini-tec), склад (warehouse), MES.
        </p>""",
    )
    html = html.replace(
        "<li>5–10 отдельных продуктов и интеграций</li>",
        "<li>OPC + historian + SCADA + MES — отдельные вендоры</li>",
    )
    html = html.replace(
        "<li>Нет ИИ на HMI — оператор ищет отчёты вручную</li>",
        "<li>Интеграция поля и учёта — ручной ETL и скрипты</li>",
    )
    html = html.replace(
        "<li>ИИ-студия: от промпта до deploy за минуты</li>",
        "<li>IoT + SCADA + MES на одном object tree</li>",
    )
    html = html.replace(
        "<li>ИИ-агент на HMI с базой знаний приложения</li>",
        "<li>58 драйверов: Modbus, OPC UA, MQTT, SNMP…</li>",
    )
    html = html.replace(
        "<li>Declarative-конфигурация: модели, CEL, BPMN, bundle</li>",
        "<li>Historian, алерты, BPMN — без отдельных серверов</li>",
    )

    # Cards - replace block
    old_cards = """      <div class="cards-grid reveal" style="margin-top:64px">
        <div class="card">
          <div class="card-icon" style="background:var(--purple-soft);color:#c084fc">✦</div>
          <h3>ИИ-студия</h3>
          <p>Tree-first agent, bundle generate, validate/publish, Context Pack, MCP — разработка решений на естественном языке.</p>
        </div>
        <div class="card">
          <div class="card-icon" style="background:rgba(168,85,247,0.22);color:#e9d5ff">◉</div>
          <h3>ИИ-агент оператора</h3>
          <p>Knowledge base, instructions, отчёты и навигация по HMI — контекстный помощник на каждом operator app.</p>
        </div>
        <div class="card">
          <div class="card-icon" style="background:rgba(59,130,246,0.15);color:#60a5fa">⬡</div>
          <h3>Единое дерево объектов</h3>
          <p>Устройство, дашборд, workflow, alert rule и приложение — узлы одного дерева с единым REST/WebSocket API.</p>
        </div>
        <div class="card">
          <div class="card-icon" style="background:rgba(6,182,212,0.15);color:#22d3ee">⚡</div>
          <h3>58 протокольных драйверов</h3>
          <p>Modbus, OPC UA, SNMP, MQTT, JDBC, Kafka — plug-and-play через SPI без изменения ядра.</p>
        </div>
        <div class="card">
          <div class="card-icon" style="background:rgba(245,158,11,0.15);color:#fbbf24">⟳</div>
          <h3>BPMN Workflow</h3>
          <p>Визуальный редактор, user tasks, work queue, CEL-шлюзы и интеграция с NATS.</p>
        </div>
        <div class="card">
          <div class="card-icon" style="background:rgba(34,197,94,0.15);color:#4ade80">▣</div>
          <h3>Application Platform</h3>
          <p>Bundle deploy, JSON-функции, SQL-отчёты, BFF, scheduler — без Java в server.</p>
        </div>
      </div>"""
    new_cards = """      <div class="cards-grid reveal" style="margin-top:64px">
        <div class="card">
          <div class="card-icon" style="background:rgba(6,182,212,0.15);color:#22d3ee">⚡</div>
          <h3>IoT · 58 драйверов</h3>
          <p>Modbus, OPC UA, SNMP, MQTT, JDBC, Kafka — полевой уровень без Java в ядре.</p>
        </div>
        <div class="card">
          <div class="card-icon" style="background:rgba(59,130,246,0.15);color:#60a5fa">◈</div>
          <h3>SCADA · HMI + historian</h3>
          <p>40+ виджетов, operator shell, CEL alert rules, correlators, TimescaleDB historian.</p>
        </div>
        <div class="card">
          <div class="card-icon" style="background:rgba(34,197,94,0.15);color:#4ade80">▣</div>
          <h3>MES · Application Platform</h3>
          <p>Bundle deploy, SQL-отчёты, BPMN, work queue, JSON-функции — declarative MES.</p>
        </div>
        <div class="card">
          <div class="card-icon" style="background:rgba(59,130,246,0.15);color:#60a5fa">⬡</div>
          <h3>Дерево объектов</h3>
          <p>Устройство, дашборд, workflow, alert и приложение — единая модель данных.</p>
        </div>
        <div class="card">
          <div class="card-icon" style="background:rgba(245,158,11,0.15);color:#fbbf24">⟳</div>
          <h3>BPMN + Work Queue</h3>
          <p>User tasks для оператора, service tasks, CEL-шлюзы, эскалация без кода.</p>
        </div>
        <div class="card">
          <div class="card-icon" style="background:rgba(239,68,68,0.15);color:#f87171">◎</div>
          <h3>Федерация</h3>
          <p>Edge + hub, catalog sync, bind overlay — распределённые площадки.</p>
        </div>
      </div>"""
    html = html.replace(old_cards, new_cards)

    # Screenshot labels
    html = html.replace('<div class="section-label">Object Tree</div>', '<div class="section-label">SCADA · Object Tree</div>')
    html = html.replace('<div class="section-label">Dashboard Builder</div>', '<div class="section-label">SCADA · HMI Builder</div>')
    html = html.replace('<div class="section-label">BPMN Automation</div>', '<div class="section-label">MES · BPMN</div>')
    html = html.replace('<div class="section-label">Alert Rules</div>', '<div class="section-label">SCADA · Alert Rules</div>')

    html = html.replace("<h3>JSON Functions</h3>", "<h3>MES Functions</h3>")
    html = html.replace(
        "<p>Script runtime с setVar, when/if, INVOKE_FUNCTION — без компиляции Java.</p>",
        "<p>JSON script runtime: setVar, when/if, INVOKE_FUNCTION — бизнес-логика без Java в server.</p>",
        1,
    )

    # Stack + CTA
    html = html.replace(
        "<h2 class=\"section-title\">Современный стек без компромиссов</h2>",
        "<h2 class=\"section-title\">Промышленный стек 2026</h2>",
    )
    html = html.replace(
        """      <p class="section-desc">
        Java 25, Spring Boot 4.0, React 19, PostgreSQL + TimescaleDB — production-ready
        с первого дня. Keycloak OIDC, NATS, ClickHouse — опционально.
      </p>""",
        """      <p class="section-desc">
        Протоколы поля, historian на TimescaleDB, BPMN и web-HMI на React —
        production-ready для энергетики, нефтегаза и дискретного производства.
      </p>""",
    )
    html = re.sub(
        r"<div class=\"stack-grid\">.*?</div>\s*</div>\s*</section>\s*\n\n  <section class=\"cta\"",
        """<div class="stack-grid">
        <span class="stack-tag">Modbus</span>
        <span class="stack-tag">OPC UA</span>
        <span class="stack-tag">MQTT</span>
        <span class="stack-tag">SNMP</span>
        <span class="stack-tag">TimescaleDB</span>
        <span class="stack-tag">Historian</span>
        <span class="stack-tag">BPMN 2.0</span>
        <span class="stack-tag">Google CEL</span>
        <span class="stack-tag">PostgreSQL</span>
        <span class="stack-tag">Java 25</span>
        <span class="stack-tag">Spring Boot 4.0</span>
        <span class="stack-tag">React 19</span>
        <span class="stack-tag">Keycloak OIDC</span>
        <span class="stack-tag">NATS</span>
        <span class="stack-tag">ИИ-студия</span>
        <span class="stack-tag">Operator Agent</span>
      </div>
    </div>
  </section>

  <section class="cta" """,
        html,
        count=1,
        flags=re.DOTALL,
    )
    html = html.replace(
        "<h2>Попробуйте ИИ-студию и операторского агента</h2>",
        "<h2>Готовы заменить зоопарк SCADA/MES?</h2>",
    )
    html = html.replace(
        """        <p>
          На demo-стенде: вкладка <strong>ИИ-студия</strong> в admin console и
          <strong>ИИ-помощник</strong> в operator app «Мини-ТЭЦ». Промпт → платформа → HMI за один сеанс.
        </p>""",
        """        <p>
          Demo-стенд: <strong>Мини-ТЭЦ</strong> (энергетика), SNMP-мониторинг, warehouse и BPMN.
          IoT → SCADA → MES на одной платформе — попробуйте на своих сценариях.
        </p>""",
    )
    html = html.replace(
        'href="https://github.com/Michaael/IoT-Solutions-Platform/blob/main/docs/en/ai-development.md"',
        'href="https://github.com/Michaael/IoT-Solutions-Platform/blob/main/docs/en/product.md"',
    )
    html = html.replace(">Документация ИИ<", ">Документация<")
    html = html.replace(
        "<p style=\"margin-top:8px\">Middleware для IoT, SCADA и промышленной автоматизации</p>",
        "<p style=\"margin-top:8px\">IoT · SCADA · MES — единая платформа промышленной автоматизации</p>",
    )

    html = html.replace(
        "background: linear-gradient(135deg, rgba(168,85,247,0.14), rgba(59,130,246,0.1));",
        "background: linear-gradient(135deg, rgba(59,130,246,0.14), rgba(6,182,212,0.08));",
    )
    html = html.replace(
        "border: 1px solid rgba(168, 85, 247, 0.28);",
        "border: 1px solid rgba(59, 130, 246, 0.28);",
    )

    # Reorder: features, platform, screenshots, architecture, ai, stack
    ai = extract_section(html, "ai")
    if not ai:
        raise SystemExit("ai section not found")
    before_ai, ai_block, after_ai = ai
    html_no_ai = before_ai + after_ai

    # Remove platform from wrong place if duplicated inside after_ai - platform should be before features
    # Current: stats, ai, features... after removing ai: stats, features...
    # We inserted platform before features in text - good

    # Move ai after architecture
    arch = extract_section(html_no_ai, "architecture")
    if arch:
        b, arch_block, a = arch
        stack_pos = a.find('<section id="stack"')
        if stack_pos == -1:
            raise SystemExit("stack section not found")
        html_no_ai = b + arch_block + a[:stack_pos] + "\n\n" + ai_block + "\n" + a[stack_pos:]
    else:
        html_no_ai = html_no_ai + "\n\n" + ai_block

    HTML.write_text(html_no_ai, encoding="utf-8")
    order = [m.group(1) for m in re.finditer(r'<section id="([^"]+)"', html_no_ai)]
    print("Updated index.html, sections:", order)


if __name__ == "__main__":
    main()
