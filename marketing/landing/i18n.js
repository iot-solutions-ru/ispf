(function () {
  "use strict";

  var STORAGE_THEME = "iot-site-theme";
  var STORAGE_LANG = "iot-site-lang";

  var I18N = {
    ru: {
      "meta.title": "IoT Solutions — системный интегратор IoT · SCADA · MES · ISPF",
      "meta.description":
        "IoT Solutions — системный интегратор IoT и IIoT. Автоматизация технологических и бизнес-процессов, поставка оборудования и ПО. Платформа ISPF: SCADA, MES, marketplace.",
      "logo.alt": "IoT Solutions — системный интегратор",
      "logo.home": "IoT Solutions — на главную",
      "nav.menu": "Меню",
      "nav.services": "Услуги",
      "nav.industries": "Отрасли",
      "nav.platform": "ISPF",
      "nav.demos": "Примеры",
      "nav.compare": "Сравнение",
      "nav.ecosystem": "Экосистема",
      "nav.process": "Процесс",
      "nav.contacts": "Контакты",
      "nav.more": "Ещё ▾",
      "nav.writeUs": "Напишите нам",
      "theme.light": "Светлая тема",
      "theme.dark": "Тёмная тема",
      "lang.ru": "Русский",
      "lang.en": "English",
      "hero.badge": "IoT Solutions · системный интегратор · ISPF",
      "hero.manifesto": "Инженерия. Сигналы. Процессы. Факты.",
      "hero.title": 'Создаём и внедряем<br><em>IoT-технологии и системы управления</em>',
      "hero.lead":
        'Решения IoT и IIoT для автоматизации технологических и бизнес-процессов частных предприятий и государственных структур — поставка оборудования, ПО и внедрение на платформе <strong>ISPF</strong> (IoT · SCADA · MES): от датчика Modbus до мнемосхемы и MES-отчёта.',
      "hero.cta.contact": "Напишите нам →",
      "hero.cta.demos": "Примеры решений",
      "hero.cta.ispf": "Демо ISPF",
      "hero.stat1": "конкурентный scorecard",
      "hero.stat2": "PRODUCTION драйверов",
      "hero.stat3": "P&ID символов",
      "stats.drivers": "Драйверов · 20 PRODUCTION",
      "stats.symbols": "P&ID символов · mimic editor",
      "stats.mes": "ISA-95 · OEE · batch · SPC",
      "stats.mfa": "TOTP · per-variable ACL",
      "stats.ai": "AI agent regression",
      "stats.bundles": "Bundle в marketplace IoT Solutions",
      "services.label": "Услуги",
      "services.title": "Системная интеграция IoT и IIoT",
      "services.desc":
        "Современные платформы и технологии индустриального интернета вещей для частных предприятий и госсектора — от проектирования АСУ ТП до поставки оборудования и программного обеспечения.",
      "services.c1t": "Системная интеграция для промышленности",
      "services.c1d": "IIoT в автоматизации технологических процессов — рост конкурентоспособности и прибыльности предприятия.",
      "services.c2t": "Автоматизация систем управления",
      "services.c2d": "Разработка и внедрение АСУ на базе IoT/IIoT — преимущество перед конкурентами и инвестиционная привлекательность.",
      "services.c3t": "Мониторинг производственного оборудования",
      "services.c3d": "Выявление и устранение причин простоев — рост производительности за счёт телеметрии и аналитики.",
      "services.c4t": "Система контроля ресурсов",
      "services.c4d": "Учёт производственных и энергоресурсов: прозрачность затрат и управление эффективностью.",
      "services.c5t": "Интеграция и инсталляция IT-оборудования",
      "services.c5d": "Объединение оборудования и ПО в инфраструктуре заказчика — готовый программно-аппаратный комплекс.",
      "services.c6t": "Аудит ИТ",
      "services.c6d": "Выявление проблем и развитие IT-отдела — ожидаемый ROI от вложенных средств для заказчика и владельца.",
      "services.cta": "Заказать услугу",
      "industries.label": "Отрасли",
      "industries.title": "Работаем в отраслях",
      "industries.desc":
        "Помогаем автоматизировать бизнес-процессы в производстве, энергетике, логистике, ЖКХ и других секторах. Используем проверенные технологии и собственную платформу ISPF.",
      "platform.label": "IoT · SCADA · MES",
      "platform.title": "Три уровня автоматизации — один продукт",
      "platform.desc":
        "Классический проект: OPC-сервер + historian + SCADA + MES + интеграция. ISPF закрывает весь стек declarative-конфигурацией на дереве объектов — от полевого уровня до производственных отчётов и operator HMI.",
      "demos.label": "Примеры на платформе",
      "demos.title": "Готовые решения на ISPF",
      "demos.desc":
        'Профессиональные системы автоматизации и мониторинга — реальные проекты IoT Solutions, развёрнутые на платформе. Каталог живых demo на <a href="https://demo.iot-solutions.ru/" target="_blank" rel="noopener">demo.iot-solutions.ru</a>.',
      "demos.c1t": "Мониторинг инженерных систем",
      "demos.c1d": "Дистанционный сбор данных с приборов учёта и датчиков. Автоматический сбор показаний для ЖКХ и эксплуатации зданий.",
      "demos.c2t": "LIMS",
      "demos.c2d": "Автоматизация лабораторных процессов: заявки, пробы, оборудование и учёт результатов в едином контуре.",
      "demos.c3t": "CoffeeGate",
      "demos.c3d": "Удалённый контроль кофейных аппаратов: состояние оборудования, уровень ингредиентов, продажи и ошибки в реальном времени.",
      "demos.view": "Смотреть demo →",
      "demos.all": "Все примеры на demo.iot-solutions.ru →",
      "process.label": "Как мы работаем",
      "process.title": "От заявки до внедрения",
      "process.desc":
        "Не стесняйтесь писать и звонить — мы любим общаться с клиентами. Научим ваших специалистов, поможем разобраться в современных решениях, выдаём сертификат по IoT-платформе.",
      "process.s1n": "Шаг 1",
      "process.s1t": "Заявка или звонок",
      "process.s1d": "В тот же день приступаем к поиску решения вашей задачи.",
      "process.s2n": "Шаг 2",
      "process.s2t": "Анализ задачи",
      "process.s2d": "Разбираем и анализируем требования, чтобы предложить оптимальный вариант.",
      "process.s3n": "Шаг 3",
      "process.s3t": "Предлагаем решение",
      "process.s3d": "Формируем решение в удобном для вас виде — архитектура, смета, сроки.",
      "process.s4n": "Шаг 4",
      "process.s4t": "Договор",
      "process.s4d": "Оговариваем и закрепляем все аспекты сотрудничества в контракте.",
      "process.s5n": "Шаг 5",
      "process.s5t": "Старт работ",
      "process.s5d": "Приступаем к выполнению взятых обязательств — внедрение, интеграция, обучение.",
      "contacts.label": "Контакты",
      "contacts.title": "Наши контакты",
      "contacts.desc": "Не стесняйтесь писать и звонить нам. Мы очень любим общаться с нашими клиентами.",
      "contacts.card1t": "Связаться с нами",
      "contacts.card1d": "ООО «ИоТ Решения» — системный интегратор IoT и IIoT.",
      "contacts.card1loc": "г. Тверь · работаем с заказчиками по всей России",
      "contacts.card2t": "Платформа и демо",
      "contacts.card2d": "Посмотрите ISPF на demo-стенде или установите bundle из marketplace.",
      "contacts.demoLink": "demo.iot-solutions.ru — примеры решений",
      "contacts.ispfLink": "ispf.iot-solutions.ru — demo-стенд ISPF",
      "cta.title": "Готовы автоматизировать производство?",
      "cta.desc":
        "Закажите консультацию интегратора или откройте demo-стенд ISPF: <strong>Мини-ТЭЦ</strong>, mes-platform, SNMP-мониторинг и BPMN.",
      "cta.contact": "Напишите нам →",
      "cta.demos": "Примеры решений",
      "cta.ispf": "Демо-стенд ISPF",
      "cta.marketplace": "Marketplace",
      "cta.partners": "Партнёрам",
      "footer.copy": "© 2026 ООО «ИоТ Решения» · IoT Solutions Platform Framework (ISPF)",
      "footer.examples": "Примеры",
      "footer.demoIspf": "Demo ISPF",
    },
    en: {
      "meta.title": "IoT Solutions — IoT · SCADA · MES system integrator · ISPF",
      "meta.description":
        "IoT Solutions — IoT/IIoT system integrator. Industrial and business process automation, hardware and software delivery. ISPF platform: SCADA, MES, marketplace.",
      "logo.alt": "IoT Solutions — system integrator",
      "logo.home": "IoT Solutions — home",
      "nav.menu": "Menu",
      "nav.services": "Services",
      "nav.industries": "Industries",
      "nav.platform": "ISPF",
      "nav.demos": "Demos",
      "nav.compare": "Compare",
      "nav.ecosystem": "Ecosystem",
      "nav.process": "Process",
      "nav.contacts": "Contact",
      "nav.more": "More ▾",
      "nav.writeUs": "Contact us",
      "theme.light": "Light theme",
      "theme.dark": "Dark theme",
      "lang.ru": "Russian",
      "lang.en": "English",
      "hero.badge": "IoT Solutions · system integrator · ISPF",
      "hero.manifesto": "Engineering. Signals. Processes. Facts.",
      "hero.title": 'We build and deploy<br><em>IoT technologies and control systems</em>',
      "hero.lead":
        'IoT and IIoT solutions for automating industrial and business processes for private companies and public sector — hardware, software, and deployment on the <strong>ISPF</strong> platform (IoT · SCADA · MES): from Modbus sensors to mimics and MES reports.',
      "hero.cta.contact": "Contact us →",
      "hero.cta.demos": "Solution demos",
      "hero.cta.ispf": "ISPF demo",
      "hero.stat1": "competitive scorecard",
      "hero.stat2": "PRODUCTION drivers",
      "hero.stat3": "P&ID symbols",
      "stats.drivers": "Drivers · 20 PRODUCTION",
      "stats.symbols": "P&ID symbols · mimic editor",
      "stats.mes": "ISA-95 · OEE · batch · SPC",
      "stats.mfa": "TOTP · per-variable ACL",
      "stats.ai": "AI agent regression",
      "stats.bundles": "Marketplace bundles",
      "services.label": "Services",
      "services.title": "IoT and IIoT system integration",
      "services.desc":
        "Modern IIoT platforms and technologies for enterprises and public sector — from control-system design to hardware and software delivery.",
      "services.c1t": "Industrial system integration",
      "services.c1d": "IIoT in process automation — higher competitiveness and profitability.",
      "services.c2t": "Control system automation",
      "services.c2d": "Design and deployment of IoT/IIoT-based control systems — competitive and investment edge.",
      "services.c3t": "Production equipment monitoring",
      "services.c3d": "Find and fix downtime root causes — productivity through telemetry and analytics.",
      "services.c4t": "Resource control system",
      "services.c4d": "Production and energy resource accounting — cost transparency and efficiency.",
      "services.c5t": "IT hardware integration",
      "services.c5d": "Unified hardware and software in your infrastructure — ready-to-run stack.",
      "services.c6t": "IT audit",
      "services.c6d": "Issue discovery and IT team growth — expected ROI for owners and customers.",
      "services.cta": "Request a service",
      "industries.label": "Industries",
      "industries.title": "Industries we serve",
      "industries.desc":
        "We automate business processes in manufacturing, energy, logistics, utilities, and more — with proven tech and our ISPF platform.",
      "platform.label": "IoT · SCADA · MES",
      "platform.title": "Three automation layers — one product",
      "platform.desc":
        "Classic stack: OPC server + historian + SCADA + MES + integration. ISPF covers the full stack with declarative object-tree configuration — from field to operator HMI and MES reports.",
      "demos.label": "Platform examples",
      "demos.title": "Ready-made ISPF solutions",
      "demos.desc":
        'Professional automation and monitoring systems — live IoT Solutions projects on the platform. Catalog at <a href="https://demo.iot-solutions.ru/" target="_blank" rel="noopener">demo.iot-solutions.ru</a>.',
      "demos.c1t": "Building systems monitoring",
      "demos.c1d": "Remote meter and sensor data collection for utilities and facility operations.",
      "demos.c2t": "LIMS",
      "demos.c2d": "Lab process automation: requests, samples, equipment, and results in one system.",
      "demos.c3t": "CoffeeGate",
      "demos.c3d": "Remote coffee machine control: equipment state, ingredients, sales, and errors in real time.",
      "demos.view": "View demo →",
      "demos.all": "All demos at demo.iot-solutions.ru →",
      "process.label": "How we work",
      "process.title": "From request to rollout",
      "process.desc":
        "Feel free to call or write — we enjoy talking to customers. We train your team, explain modern solutions, and issue IoT platform certificates.",
      "process.s1n": "Step 1",
      "process.s1t": "Request or call",
      "process.s1d": "We start looking for a solution the same day.",
      "process.s2n": "Step 2",
      "process.s2t": "Task analysis",
      "process.s2d": "We analyze requirements to propose the best option.",
      "process.s3n": "Step 3",
      "process.s3t": "Proposal",
      "process.s3d": "Architecture, estimate, and timeline in a format that works for you.",
      "process.s4n": "Step 4",
      "process.s4t": "Contract",
      "process.s4d": "All cooperation terms are agreed and documented.",
      "process.s5n": "Step 5",
      "process.s5t": "Kickoff",
      "process.s5d": "Deployment, integration, and training — we deliver on our commitments.",
      "contacts.label": "Contact",
      "contacts.title": "Get in touch",
      "contacts.desc": "Feel free to call or email — we love talking to our customers.",
      "contacts.card1t": "Contact us",
      "contacts.card1d": "IoT Solutions LLC — IoT/IIoT system integrator.",
      "contacts.card1loc": "Tver · serving customers across Russia",
      "contacts.card2t": "Platform & demo",
      "contacts.card2d": "Try ISPF on the demo stand or install a bundle from the marketplace.",
      "contacts.demoLink": "demo.iot-solutions.ru — solution examples",
      "contacts.ispfLink": "ispf.iot-solutions.ru — ISPF demo stand",
      "cta.title": "Ready to automate production?",
      "cta.desc":
        "Request integrator consulting or open the ISPF demo: <strong>mini-TEC</strong>, mes-platform, SNMP monitoring, and BPMN.",
      "cta.contact": "Contact us →",
      "cta.demos": "Solution demos",
      "cta.ispf": "ISPF demo stand",
      "cta.marketplace": "Marketplace",
      "cta.partners": "Partners",
      "footer.copy": "© 2026 IoT Solutions LLC · IoT Solutions Platform Framework (ISPF)",
      "footer.examples": "Demos",
      "footer.demoIspf": "ISPF demo",
    },
  };

  var currentLang = "ru";

  function t(key) {
    var pack = I18N[currentLang] || I18N.ru;
    return pack[key] != null ? pack[key] : I18N.ru[key] || key;
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem(STORAGE_THEME, theme);
    var btn = document.getElementById("theme-toggle");
    if (btn) {
      var isLight = theme === "light";
      btn.setAttribute("aria-label", t(isLight ? "theme.dark" : "theme.light"));
      btn.textContent = isLight ? "☀" : "☾";
    }
  }

  function applyLanguage(lang) {
    if (!I18N[lang]) lang = "ru";
    currentLang = lang;
    document.documentElement.lang = lang;
    localStorage.setItem(STORAGE_LANG, lang);

    document.querySelectorAll("[data-i18n]").forEach(function (el) {
      el.textContent = t(el.getAttribute("data-i18n"));
    });
    document.querySelectorAll("[data-i18n-html]").forEach(function (el) {
      el.innerHTML = t(el.getAttribute("data-i18n-html"));
    });
    document.querySelectorAll("[data-i18n-alt]").forEach(function (el) {
      el.setAttribute("alt", t(el.getAttribute("data-i18n-alt")));
    });
    document.querySelectorAll("[data-i18n-aria]").forEach(function (el) {
      el.setAttribute("aria-label", t(el.getAttribute("data-i18n-aria")));
    });

    document.title = t("meta.title");
    var meta = document.querySelector('meta[name="description"]');
    if (meta) meta.setAttribute("content", t("meta.description"));

    document.querySelectorAll("[data-lang-btn]").forEach(function (btn) {
      btn.classList.toggle("active", btn.getAttribute("data-lang-btn") === lang);
      btn.setAttribute("aria-pressed", btn.classList.contains("active") ? "true" : "false");
    });

    var themeBtn = document.getElementById("theme-toggle");
    if (themeBtn) {
      var theme = document.documentElement.getAttribute("data-theme") || "dark";
      themeBtn.setAttribute("aria-label", t(theme === "light" ? "theme.dark" : "theme.light"));
    }
  }

  function initThemeLang() {
    var theme = localStorage.getItem(STORAGE_THEME);
    if (!theme) {
      theme = window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
    }
    applyTheme(theme);

    var lang = localStorage.getItem(STORAGE_LANG) || "ru";
    applyLanguage(lang);

    var themeBtn = document.getElementById("theme-toggle");
    if (themeBtn) {
      themeBtn.addEventListener("click", function () {
        var next = document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light";
        applyTheme(next);
      });
    }

    document.querySelectorAll("[data-lang-btn]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        applyLanguage(btn.getAttribute("data-lang-btn"));
      });
    });
  }

  window.IotSiteI18n = { applyLanguage: applyLanguage, applyTheme: applyTheme, t: t };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initThemeLang);
  } else {
    initThemeLang();
  }
})();
