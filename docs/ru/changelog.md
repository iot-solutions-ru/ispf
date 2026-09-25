> **Language:** Russian summary. Canonical changelog: [../../CHANGELOG.md](../../CHANGELOG.md) (Keep a Changelog, English).

# Журнал изменений (ISPF)

Краткое русское зеркало. Полные формулировки и ссылки — в корневом `CHANGELOG.md`.

**Область:** ядро платформы, server, web-console, драйверы, AI agent.  
Changelog отдельных application bundles — в манифестах пакетов.

## [Unreleased]

### Исправлено

- Невычислимое условие правила считалось ложью: `conditionPasses` ловил `ExpressionException` и возвращал `false`. Опечатка и честный `false` выглядели одинаково. Теперь это ошибка пересчёта с путём объекта и текстом условия. Посчитанная ложь по-прежнему пропускает правило; пустое условие по-прежнему значит «выполнять всегда».
- Удаление `DEVICE` (HTTP / UI / bulk) оставляло драйвер в памяти: опросы продолжали вставать в очередь, а `POST .../drivers/runtime/stop` падал с 404 при записи `driverStatus`. Теперь перед удалением останавливаются все активные драйверы под путём (включая потомков); `stop` и сиротский poll больше не требуют узел в дереве.
- Правило с `call()`, `queryScalar()`, `queryRows()` или `fire()` прятало сбой как пустой результат: правило выглядело успешным, цель не менялась. Теперь ошибка содержит цель и исходный текст. Вложенный `call()` во время уже идущего вызова запрещён. Успешный `fire()` без полезной нагрузки остаётся успехом.
- Схема выхода Java-функции не применялась: скрипт сворачивает ответ в `outputSchema`, Java отдавала запись, которую собрал класс. Вызывающий мог получить поля и типы, которых в схеме нет, либо не получить обязательное поле. Теперь ответ приводится к объявленной схеме: лишние поля отбрасываются, нет обязательного поля — ошибка вызова, числа приводятся как у скрипта.
- Цепочка правил обрывалась после 8 проходов или 16 вложений без ошибки: запись исходного поля выглядела успешной, хвост оставался прежним. Потолки те же; при обрыве теперь ошибка с путём объекта и текстом, что цепочка обрезана.
- Поле `DOUBLE` в `DataRecord` принимало `Double`, `Float` и `Integer` и отвергало `Long`. Целое больше 32 бит из JSON падало с «must be double», меньшее целое проходило. Теперь любое конечное число хранится как дробь.
- Java-функции при `bootRun`: сохранение `sourceType=java` падало с
  `classpath missing ispf-core (entries=1)`, потому что Gradle кладёт один classpath-jar
  в `java.class.path`, а `JavaFunctionCompileClasspath` не читал его манифест `Class-Path`.
  Записи манифеста теперь попадают в `javac`.
- Драйвер `flexible` по UDP: `exchangeUdpBytes` собирал `DatagramPacket` через
  `InetSocketAddress.createUnresolved(...)`, который JDK отвергает (`IllegalArgumentException:
  unresolved address`) — любой UDP-обмен падал до отправки запроса. Адрес теперь резолвится;
  покрыто UDP-loopback в новом `FlexibleDeviceDriverTest`.
- Marketplace install: символ/UI/analytics/bundle — только configurator; operator → 403.
- Driver `runtime/write`: учитывает `writeRoles` переменной точки (иначе object `WRITE`).
- Alert rules / event filters / correlators: мутации требуют object `WRITE` ACL; list/get — `READ`.
- Data sources / SQL bindings: мутации требуют object `WRITE` ACL; чтение — `READ`.
- Driver runtime: мутации устройства требуют object `WRITE` ACL; status/browse/poll — `READ`;
  загрузка shared catalog — configurator.
- Система → Настройки: запись sensitive (`ai.api-key`) из UI; кнопка перезапуска сервера (`POST /runtime-settings/restart`).
- AI Studio чат: nginx HTML 404 при вызове LLM — host-only `base-url` теперь получает `/v1`, HTML шлюза заменяется подсказкой с URL запроса.
- Runtime settings из UI снова применяются ко **всем** ключам каталога (не только AI): Boot 4 не читал старый SPI EnvironmentPostProcessor, yaml/env затирали `runtime-settings.properties`. Профиль `local` больше не пинит lab LLM URL.
- Auth logout AuthZ: `POST /api/v1/auth/logout` снова `permitAll` (операторы могут отзывать Bearer).
- Marketplace `mqtt-temperature`: H2-совместимый SQL как в lab bundle.
- Modbus TCP/UDP/RTU `readConfig`: сначала `configuration()`, затем переменные устройства (fix bind host/port из `driverConfigJson`).

### Добавлено

- Web Console: два модуля по 2000+ строк разбиты на реестры (поведение не менялось):
  `ispfSheetEval.ts` (2166 строк) → фасад 45 строк над `sheetEvalCore` / `Tokenizer` / `Parser` /
  `Functions` и шестью реестрами функций по категориям; if-цепочка из 118 веток стала таблицами
  `Record<string, SheetFunction>`; characterization-тест воспроизводит 135 формул со значениями,
  снятыми с прежней реализации. `widgetEditorFields.tsx` (2492 строки) → диспетчер 31 строка над
  реестрами `widgetTypeFields{Display,Chart,Data,Layout}.tsx` по типу виджета; тест проверяет,
  что реестр покрывает ровно 43 типа старого `switch`, и монтирует каждый рендерер.
- Критерий доказательности зрелости драйверов (F-03): `DriverProductionMatrixTest` проверяет
  каждую запись `PRODUCTION` механически (`DriverMaturityEvidence`): ≥ 100 строк кода драйвера без
  комментариев, тест `*Driver*Test`, который гоняет сам драйвер, и проверяемый peer (fixture
  interop-lab, эмулированный peer в тесте или драйвер без транспорта). BETA-записи, проходящие
  критерий, должны быть перечислены в `BETA_BY_DECISION` с причиной. `icmp`, `smb`, `wmi` понижены до
  **BETA** (156 PRODUCTION / 6 BETA); у `modbus-udp`, `flexible`, `webhook` появились настоящие
  loopback-тесты драйвера вместо тестов парсера. `tools/driver-readiness-audit.py` даёт одинаковый
  вывод на любой ОС (POSIX-пути, детерминированный выбор теста).
- Типизированные исключения драйверов в top-20 industrial (F-05): все 204 места `throw new
  DriverException(...)` в `virtual`, `mqtt`, `modbus-tcp/rtu/udp`, `opcua`, `opcua-server`, `snmp`, `bacnet`,
  `s7`, `http`, `flexible`, `iec104`, `iec104-server`, `dnp3`, `dlms`, `ethernet-ip`, `opc-da`, `opc-bridge`,
  `gps-tracker` теперь объявляют `DriverErrorKind` (`DriverTransient` / `DriverConfiguration` /
  `DriverPermanent` / `DriverUnsupportedOperationException`) — `ispf.driver.errors.total{kind}` и статус
  драйвера перестают показывать `unclassified` для них. `DriverTypedExceptionsTest` защищает паки от регресса.
- Error Prone на каждом `javac`: `net.ltgt.errorprone` 5.1.1 / `error_prone_core` 2.50.0 во всех Java-модулях,
  ERROR-паттерны валят компиляцию. Восемь паттернов поднято из WARNING в ERROR после того, как sweep исправил
  все вхождения (`DefaultCharset`, `StreamResourceLeak`, `NonAtomicVolatileUpdate`, `OrphanedFormatString`,
  `ArgumentSelectionDefectChecker`, `AlreadyChecked`, `DuplicateBranches`, `MissingOverride`); среди правок —
  байты MQTT publish / Basic-auth / DLMS больше не зависят от default charset JVM, закрыты потоки `Files.walk`,
  гоночные `volatile`-счётчики в тестовых фикстурах, мёртвые условия в `PlatformUserService` /
  `EventCorrelatorService`. `-Pispf.errorprone=false` для быстрой локальной итерации; см.
  [testing § Статический анализ](docs/ru/testing.md).
- Ночной гейт уязвимостей зависимостей: `./gradlew cyclonedxBom` собирает один CycloneDX SBOM по `runtimeClasspath`
  всех модулей; job `dependency-vulnerabilities` в nightly сканирует его Trivy и падает на исправимых
  CRITICAL/HIGH (полный отчёт и SBOM — артефакт). Первый прогон закрыт пинами Tomcat 11.0.26, Netty 4.2.18 для
  драйвера Neo4j, Bouncy Castle 1.86, lz4-java 1.11.3, commons-configuration2 2.15.1 — все внесены в реестр
  пинов ADR-0059 с условиями снятия.
- Бенчмарк синка путей ObjectManager + аудит `synchronized` (F-08): `ObjectManagerPathSyncBenchmarkTest`
  прогоняет 8 followers × 20 синков по разным путям через монитор экземпляра (до F-08) и через текущий
  RW-лок + per-path монитор: **в 8,2 раза быстрее** (402,9 → 49,0 мс при имитации DB round-trip 2 мс); тест
  требует ≥ 2×, чтобы разделение не откатилось незаметно. Все 76 оставшихся `synchronized` в `ispf-server`
  просмотрены — других мониторов на весь экземпляр на горячем пути нет; один пункт «watch»
  (`RecentEventCache`, сканы на чтение). Отчёт: `docs/evidence/quality/2026-09-20-synchronized-audit.md`.
- Крупные сервисы сервера разбиты на компоненты (API без изменений):
  `ApplicationBundleDeployService` 1459 → 674 строк (`BundleTreeArtifactsApplier`,
  `BundleArtifactDeployers`, `BundleOperatorUiSync`); `WorkflowService` 1329 → 996
  (`WorkflowTaskExecutor`, `WorkflowInstanceStatePublisher`, общий `resumeWaitingInstance`/`finishStep`
  вместо пяти копий цикла «загрузить → шаг движка → сохранить/опубликовать»);
  `ReportService` 1183 → 830 (`ReportSqlQuery`, `ReportTableExport`, `TreeVariablesReportRows`).
  Новые тесты: `WorkflowTaskExecutorTest`, `ReportTableExportTest`.
- Lint-гейт Web Console на нуле предупреждений: `npm run lint` с `--max-warnings 0` (было 180);
  исправлены все `exhaustive-deps`, `no-non-null-assertion` и `only-export-components`
  (хуки контекстов и хелперы вынесены в соседние `.ts`-модули, `!` заменён на `required()`).
- Опциональный модуль Parquet-экспорта `packages/ispf-export-parquet` (parquet-mr/Avro/hadoop вынесены из `ispf-server`, SPI `HistoryParquetExporter` в `ispf-core`); `-Pispf.exportParquet=false` собирает сервер без него — `format=parquet` отвечает 501, cold archive — `skipped` (ADR-0059 §4).
- Гейт покрытия JaCoCo: `./gradlew coverageVerify` (CI pr-fast) падает при снижении LINE/BRANCH
  покрытия модуля ниже порога из `coverageFloors` (корневой `build.gradle.kts`); пороги только повышаются.
- Пакетный invoke функций (`POST .../functions/invoke-batch`) и «Подтвердить все» в alarm bar.
- Поиск по полному дереву объектов в Web Console (#204).
- CEL-фильтр ленты журнала событий (#205).
- Гранулы historian для auto-бакетов графиков (#207).
- OPC UA Sign / SignAndEncrypt + PKI trust lists (#208).
- Пакетная очистка журнала: `POST /api/v1/events/journal/purge` (#211).
- Внутренний engineering security review 2026-09-09 (defensive; **≠ G-01**).
- Enterprise L tooling в `tools/historian-scale/`: seed/count history-enabled,
  `GET /history-enabled-count`, gate считает переменные (не `/tags` binding rules).
- `npm run pwa:offline-field-soak` — lab 2h CDP offline soak.
- **OT Trust Wave 2** — `redis`, `mitsubishi-slmp`, `yaskawa-memobus`, `sparkplug-b`: STUB → **PRODUCTION**
  (реальные кодеки + loopback-тесты); evidence `docs/evidence/ot-trust/2026-09-05-wave2-codec-promotion.md`.

### Docs

- **Quality hold** — черновик письма G-01, чеклист dogfood Web Console, заметка ACL-аудита. Prep ≠ pass.
- MQTT: wire = **3.1.1**; MQTT 5 — non-goal (#209). Nested dashboards: inheritContext (#206).
- **Внешняя AI IDE / hosted SPA** — how-to Cursor/VS Code: MCP, BFF, Vite `base`, ui-pack. Хаб [`docs/ru/external-ide.md`](external-ide.md) (канон EN [`docs/en/external-ide.md`](../en/external-ide.md)).
- **OT Trust BL-140 lab soak pull 2026-09-10…14** — P1/P2/P3 `pass: true` / RUNNING; закрывает SSH-блокер [#212](https://github.com/iot-solutions-ru/ispf/pull/212). Lab ≠ field / не OT 10/10. Evidence `docs/evidence/ot-trust/2026-09-14-bl140-lab-soak-pull.md`.
- **OT Trust BL-140 lab soak stopped** — остановка 2026-09-14; P-OT учтён (lab); таймеры сняты. Не field Done / не OT 10/10. Evidence `docs/evidence/ot-trust/2026-09-14-bl140-lab-soak-stopped.md`.
- **OT Trust BL-140 Pilot #1 soak day 2** — auto soak-check 2026-09-07 06:00 MSK pass. Evidence `docs/evidence/ot-trust/2026-09-07-bl140-pilot1-day2.md`.
- **OT Trust BL-140 Pilot #1 C5 + daily soak check** — disconnect/reconnect на lab VLAN; `tools/ot-trust/pilot1-modbus-soak-check.py` + timer 06:00 MSK. Не field Done / не OT 10/10. Evidence `docs/evidence/ot-trust/2026-09-06-bl140-pilot1-c5-disconnect.md`.
- **OT Trust BL-140 Pilot #1 lab day 1** — площадка `lab-ot-vlan-192.168.100`; Modbus peer `:1502`; 50 тегов RUNNING; write+historian; journal day 1. Не field Done / не OT 10/10. Evidence `docs/evidence/ot-trust/2026-09-06-bl140-pilot1-lab-day1.md`.
- **OT Trust BL-140 Pilot #1 kickoff** — полевой prep-пакет Modbus: чеклист C1–C6 + intake площадки, soak-журнал. Evidence `docs/evidence/ot-trust/2026-09-06-bl140-pilot1-kickoff.md`.
- **OT Trust BL-141 GPS tracker fixture** — NMEA TCP listener stand-in + feed→`/last` smoke (compose **14/20**). Evidence `docs/evidence/ot-trust/2026-09-06-bl141-gps-tracker-fixture.md`.
- **OT Trust BL-141 Modbus RTU fixture** — RTU ADU over TCP + FC6/FC16→FC3 smoke (compose **13/20**). Evidence `docs/evidence/ot-trust/2026-09-06-bl141-modbus-rtu-fixture.md`.
- **OT Trust BL-141 S7 SoftPlc fixture** — SoftPlc ISO-on-TCP `:102` + REST DB1 REAL @80 (compose **12/20**). Evidence `docs/evidence/ot-trust/2026-09-06-bl141-s7-fixture.md`.
- **OT Trust BL-141 DNP3 fixture** — stdlib TCP outstation + integrity-poll smoke (compose **11/20**, poll-only ADR-0057). Evidence `docs/evidence/ot-trust/2026-09-06-bl141-dnp3-fixture.md`.
- **OT Trust BL-141 Modbus UDP fixture** — stdlib MBAP-over-UDP peer + FC6/FC16→FC3 smoke (compose **10/20**). Evidence `docs/evidence/ot-trust/2026-09-06-bl141-modbus-udp-fixture.md`.
- **OT Trust BL-141 DLMS fixture** — stdlib TCP WRAPPER peer + SET/GET smoke (compose **9/20**). Evidence `docs/evidence/ot-trust/2026-09-06-bl141-dlms-fixture.md`.
- **OT Trust BL-141 EtherNet/IP fixture** — stdlib CIP UCMM peer + Write/Read Tag smoke (compose **8/20**). Evidence `docs/evidence/ot-trust/2026-09-06-bl141-ethernet-ip-fixture.md`.
- **OT Trust BL-141 IEC 104 fixture** — stdlib IEC 60870-5-104 TCP outstation + smoke (compose **7/20**). Evidence `docs/evidence/ot-trust/2026-09-06-bl141-iec104-fixture.md`.
- **OT Trust BL-141 BACnet fixture** — stdlib BACnet/IP UDP lab agent + Read/WriteProperty smoke (compose **6/20**). Evidence `docs/evidence/ot-trust/2026-09-06-bl141-bacnet-fixture.md`.
- **OT Trust BL-141 fixture depth** — SNMP + HTTP lab docker fixtures + smoke (compose **5/20**); stdlib agents; self-test без docker. Evidence `docs/evidence/ot-trust/2026-09-06-bl141-snmp-http-fixtures.md`.
- **OT Trust post-merge honesty** — после Waves 1–11 / каталог **162/162** (#144): lab ≠ field; inventory TOP-20 docker fixtures; дальше глубина BL-141 + plant soaks. Evidence `docs/evidence/ot-trust/2026-09-06-post-merge-lab-vs-field.md`.
- **OT Trust Wave 3** — 13 clean-room кодеков → PRODUCTION (ADS/MELSEC/MQTT-SN/NATS/KNX/…); без GPL и проприетарных SDK.
- **OT Trust — поднятие готовности всех 162 паков** — stub-kit **v0.2** (TCP probe + memory loopback) +
  contract-тесты на каталог STUB → метка аудита **`STUB_LAB`** (без фейкового PRODUCTION);
  `tools/driver-stubs/raise-stub-readiness.py`.
- **OT Trust — аудит всех 162 драйверов** — `tools/driver-readiness-audit.py`, отчёт
  `docs/evidence/ot-trust/driver-readiness.md` (honesty: matrix ↔ packs).
- **OT Trust Wave 1 hardening** — writable Modbus fixture; FC6/FC16 smoke; writePoint gate; lab day-1 journal.
- **OT Trust Wave 1** — снят парк P-OT; ADR-0057 (DNP3 PRODUCTION poll-only); evidence `docs/evidence/ot-trust/`.
- G-01 pen-test prep (углублённо+): RoE/опросник EN, расширенные кейсы, шаблоны case-results/evidence-index, preflight (OpenAPI status + headers); [pen-test-prep.md](pen-test-prep.md); SOW [pen-test-scope.md](pen-test-scope.md); ADR-0056 WebAuthn/IdP MFA (BL-194 Proposed).
- Доска parked: [parked-backlog.md](parked-backlog.md); Enterprise L lab PASS (50k / 1B / multi-tag) в `docs/evidence/historian-scale/`.
- Enterprise L playbook → `tools/historian-scale/`; Historian JVM archive.

## [0.9.207] - 2026-09-01

### Исправлено

- Разбиение SQL по `;` в однострочных batch-миграциях (quoted literals).
- H2-совместимые типы в `lab-mqtt-temperature` и `mini-tec`.
- Bundle suite: import только при strict `OK`.

### Docs

- Scorecard pin demostand **0.9.207**.

## [0.9.206] - 2026-09-01

### Исправлено

- Migration applyPending scoped по dataSource (без cross-app pollution).
- H1-full: compensating cleanup при FAILED/PARTIAL без active snapshot.

### Docs

- Scorecard pin demostand **0.9.206**.

## [0.9.205] - 2026-09-01

### Исправлено

- H1-lite: честный статус deploy (`OK`/`PARTIAL`/`FAILED`); marketplace и AI
  playbook больше не форсят `OK`; active snapshot только после успешного
  `syncApplicationTree`.

### Docs

- Scorecard pin demostand **0.9.205**.

## [0.9.204] - 2026-08-31

### Исправлено

- Periodic binding disable после N consecutive failures (default 5).

### Docs

- HMI live FPS re-smoke demostand 0.9.203 (60 FPS, 517 WS updates).

## [0.9.203] - 2026-08-31

### Исправлено

- Workflow trigger index prune после ObjectNotFound.
- SQL binding orphan auto-disable (tree + app).

### Docs

- Scorecard pin 0.9.202; CEL + AI re-soak evidence.

## [0.9.202] - 2026-08-31

### Исправлено

- Analytics scheduler retry loop; workflow trigger soft-fail; binding schedule advance;
  orphan alert disable-once; process program / analytics quality soft-fail; boot order H8;
  metrics probe + derived-tag idle gates.

### Изменено

- Corrupt roles JSON → WARN log; agent saveTurn @Transactional; platform scheduler fail log.

## [0.9.201] - 2026-08-30

### Исправлено

- Idle gate для platform schedule / process program / workflow retry+cron.
- Orphan alert auto-disable вместо WARN каждый poll.

### Изменено

- `objectTree.ready` в platform metrics; docs для `ispf.object_tree.ready`.

## [0.9.200] - 2026-08-30

### Исправлено

- Self-diagnostics bootstrap: INTEGER zero без ternary-promotion в Double.

## [0.9.199] - 2026-08-30

### Исправлено

- Alert soft-fail + idle gate до `ObjectManager.isInitialized()`.
- Periodic binding `fireDue` удаляет stale schedule при ObjectNotFound.

### Добавлено

- Prometheus gauge `ispf.object_tree.ready`.

## [0.9.198] - 2026-08-30

### Исправлено

- Analytics tag catalog не сканирует `@bindingRules` до `ObjectManager.isInitialized()`.

## [0.9.197] - 2026-08-30

### Исправлено

- Historian/periodic `@bindingRules` только для существующих object_nodes.
- Cascade disable SQL bindings при DELETE объекта.
- UTF-8 mojibake в AI agent UI-строках.
- Prometheus gauge `ispf.websocket.clients`.

## [0.9.196] - 2026-08-30

### Исправлено

- MEMBER ACL на tree-variables reports (API/agent/export); CEL smoke evidence 0.9.195.

## [0.9.195] - 2026-08-30

### Исправлено

- Scheduler idle gate — periodic/application SQL/analytics ждут `ObjectManager.isInitialized()`.
- SQL binding soft-fail — отсутствующий target → warn + skip.
- Legacy `root.users.<user>` → `root.platform.security.users.<user>` при syncUser.

## [0.9.194] - 2026-08-30

### Исправлено

- **WebSocket `/ws/objects`** — local `JwtDecoder`/`example.invalid` больше не роняет handshake (500 + log spam); opaque platform tokens не идут в JWT decode.

### Ранее Unreleased

- CI nightly ghost failures на push в `main` — лёгкий `push-ack`, тяжёлые job только schedule/dispatch.
- Live FPS gate на реальном operator mimic + evidence demostand 0.9.193.

## [0.9.193] - 2026-08-30

### Исправлено / изменено

- Общий `parsePrivateKey` для PEM signing; docs honesty Post-S33.

### Изменено

- Версия платформы **0.9.193**.

## [0.9.192] - 2026-08-30

### Исправлено

- **PEM в EnvironmentFile** — signing/verify терпят литеральные `\n`
  (AI apply 503 `Illegal base64 character 5c`); VPS enable script пишет
  single-line PEM.

### Evidence

- Soft re-soak demostand **0.9.191** HVAC/MES/SCADA (~19s, signed).

### Изменено

- Версия платформы **0.9.192**.

## [0.9.191] - 2026-08-30

### Исправлено

- **BL-154: остаток Object Query/function/binding** — interactive invoke/evaluate
  в `MEMBER`; OQ omit denied live/historian; ref write / script bridge ACL;
  backup admin-only regression.
- CI nightly больше не стартует на каждый push в `main` (только schedule + dispatch).

### Изменено

- Версия платформы **0.9.191**.

## [0.9.190] - 2026-08-30

### Исправлено

- **BL-154 trusted-channel close** — `MEMBER` ACL на analytics, agent, WebSocket,
  editor, expression, Haystack/Brick.
- **Federation tunnel on-behalf-of** — делегированный principal; anonymous
  value/history запрещены.
- **HTTP peer on-behalf-of (G-05)** — заголовки `X-ISPF-On-Behalf-Of-*`,
  пересечение ролей канала ∩ claimed (без escalation).
- **Hub federated proxy fail-closed** — omit remote-only vars без local ACL;
  proxy write без local var → 403 (кроме admin).

### Изменено

- Версия платформы **0.9.190**; обновлены security/federation/compliance docs.

## [0.9.189] - 2026-08-30

### Добавлено

- **Формальная верификация CEL** (продуктовый gate, ADR-0055): unsatisfiable / tautology,
  equivalence, runtime-настройки, REST `/expressions/verify` (+ equivalence),
  AI-tool `verify_cel_condition`, enforce на apply алертов/bindings.
- **Design-time gate для BPMN workflow** — условия sequence flow на `saveBpmn` / activate.
- **Historian formal rewrite** — `avg`/`live`/… → коррелированные `self.__histN` для SMT.
- **97 protocol stub-драйверов** отдельными packs (Apache-2.0, STUB) — каталог **162** packs.
- Корневой Keep a Changelog + это русское зеркало.

### Изменено

- CEL **0.14.0**; protobuf-java **4.36.0**.
- Dependabot: Spring Boot 4.1.1, Gradle 9.7.1, web-console npm и др.
- **`@vitejs/plugin-react` → 6.1.0** (+ `.npmrc` `legacy-peer-deps=true`).
- Context pack парсит полный каталог драйверов из docs.
- Версия платформы **0.9.189**.

### Исправлено

- V89 `random_uuid` на H2 (placeholder block comment).
- Парсер catalog в `tools/ai-pack/build.py` после переписывания `drivers.md`.

## [0.9.188] - 2026-08-25

### Добавлено

- Signed bundles / VPS evidence; MES GA smoke; Flyway V89 для PostgreSQL.

### Исправлено

- Проверка `contentSha256` лицензии бандла по raw JSON.

### Изменено

- Версия платформы **0.9.188**.
