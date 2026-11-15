# ISPF CLI

English, then Russian. The same commands.

The CLI packs a solution directory, checks it, and deploys it to a running ISPF server. It is not published to npm. Run it from this repository.

```bash
cd tools/ispf-cli
npm install
node bin/ispf.mjs --version
```

From the repository root, after that install:

```bash
node tools/ispf-cli/bin/ispf.mjs validate examples/demo-app --local --strict
```

Node.js 20 or newer. The local check uses the bundle schema in this repository, so the CLI has to run from a checkout, not from a copied `bin/` alone.

## First session

Start the server from the repository root (`./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"`, or the release scripts). The local profile answers `admin` / `admin`.

```bash
node bin/ispf.mjs login --username admin --password admin
node bin/ispf.mjs doctor
```

`login` writes the token to `~/.ispf/credentials.json` (or `$ISPF_HOME/.ispf/credentials.json`). It does not print the token. Later commands read that file. `ISPF_API_TOKEN` overrides the file. A saved token is ignored when `ISPF_BASE_URL` is different from the URL used at login.

`doctor` checks four things and exits 1 if any of them fail:

- `GET /api/v1/info` answers with a version
- `GET /actuator/health` is `UP`
- a token is available and the server accepts it
- `GET /api/v1/drivers` includes the `virtual` driver (the one Path A needs)

## Commands

| Command | What it does |
|---|---|
| `ispf --version` | Prints the CLI version. |
| `ispf login --username <user> --password <pass> [--totp <code>]` | Local-profile login. Saves the token. |
| `ispf doctor` | Server, health, token, virtual driver. |
| `ispf cert check <bank.json>` | Check a certification question bank. |
| `ispf cert run <bank.json> [--answers <file>]` | Grade the bank. Terminal reads option numbers starting at 1. `--answers` uses the 0-based `correctIndex`. |
| `ispf pack <dir> [-o bundle.json]` | Pack a solution directory into one bundle. |
| `ispf unpack <bundle.json> <dir>` | Unpack a bundle back into a directory. |
| `ispf validate <dir\|bundle.json> [--local] [--app <id>] [--strict]` | Check the bundle. |
| `ispf diff <dir> --app <id>` | Compare a directory with the deployed application. |
| `ispf deploy <dir> --app <id> [--strict] [--with-seeds]` | Deploy. `--with-seeds` runs the bundle's seed events. |
| `ispf fork <bundle.json\|dir> <dest> --app-id <plant-id>` | Copy a solution and give it a new application id. |
| `ispf init-solution <dest> --app-id <id> [--name <display>]` | Scaffold a new solution directory. |

`--local` checks the JSON against the schema in this repository and does not call the server. `--app` also asks the server (`POST /api/v1/applications/{id}/bundle/validate`). `--strict` turns the ship-canon warning about logic hosted on a `DEVICE` object into an error, locally and on the server.

`diff` and `deploy` need a running server and a token. `cert check` and `cert run` read a local JSON file and do not call the server.

## Environment

| Variable | Default | Meaning |
|---|---|---|
| `ISPF_BASE_URL` | `http://localhost:8080` | Server the remote commands call. |
| `ISPF_API_TOKEN` | empty | Bearer token. When set, the credentials file is not used. |
| `ISPF_HOME` | the user home directory | Parent of `.ispf/credentials.json`. |
| `ISPF_USERNAME`, `ISPF_PASSWORD` | empty | Used by `login` when the flags are omitted. |

---

# CLI ISPF

Английский текст выше, русский здесь. Команды те же.

CLI собирает каталог решения в бандл, проверяет его и выкладывает на запущенный сервер ISPF. В npm пакет не публикуется. Запускайте из этого репозитория.

```bash
cd tools/ispf-cli
npm install
node bin/ispf.mjs --version
```

Из корня репозитория, после этой установки:

```bash
node tools/ispf-cli/bin/ispf.mjs validate examples/demo-app --local --strict
```

Нужен Node.js 20 или новее. Локальная проверка берёт схему бандла из этого репозитория, поэтому одного скопированного `bin/` недостаточно.

## Первая сессия

Запустите сервер из корня репозитория (`./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"` или скрипты релиза). Локальный профиль принимает `admin` / `admin`.

```bash
node bin/ispf.mjs login --username admin --password admin
node bin/ispf.mjs doctor
```

`login` записывает токен в `~/.ispf/credentials.json` (или `$ISPF_HOME/.ispf/credentials.json`) и не печатает его. Следующие команды читают этот файл. `ISPF_API_TOKEN` важнее файла. Сохранённый токен не используется, если `ISPF_BASE_URL` отличается от адреса, с которым выполнялся вход.

`doctor` проверяет четыре вещи и завершается с кодом 1, если хотя бы одна не сошлась:

- `GET /api/v1/info` отвечает версией
- `GET /actuator/health` равен `UP`
- токен есть и сервер его принимает
- `GET /api/v1/drivers` содержит драйвер `virtual` (он нужен для Path A)

## Команды

| Команда | Что делает |
|---|---|
| `ispf --version` | Печатает версию CLI. |
| `ispf login --username <user> --password <pass> [--totp <code>]` | Вход в локальный профиль. Сохраняет токен. |
| `ispf doctor` | Сервер, здоровье, токен, драйвер virtual. |
| `ispf cert check <bank.json>` | Проверить банк вопросов сертификации. |
| `ispf cert run <bank.json> [--answers <file>]` | Прогнать банк. В терминале номер варианта с 1. `--answers` — индекс с нуля, как `correctIndex`. |
| `ispf pack <dir> [-o bundle.json]` | Собрать каталог решения в один бандл. |
| `ispf unpack <bundle.json> <dir>` | Разобрать бандл обратно в каталог. |
| `ispf validate <dir\|bundle.json> [--local] [--app <id>] [--strict]` | Проверить бандл. |
| `ispf diff <dir> --app <id>` | Сравнить каталог с уже развёрнутым приложением. |
| `ispf deploy <dir> --app <id> [--strict] [--with-seeds]` | Развернуть. `--with-seeds` выполняет seed-события бандла. |
| `ispf fork <bundle.json\|dir> <dest> --app-id <plant-id>` | Скопировать решение и задать новый идентификатор приложения. |
| `ispf init-solution <dest> --app-id <id> [--name <display>]` | Создать заготовку нового решения. |

`--local` сверяет JSON со схемой из этого репозитория и не обращается к серверу. `--app` дополнительно спрашивает сервер (`POST /api/v1/applications/{id}/bundle/validate`). `--strict` превращает предупреждение о логике на объекте `DEVICE` в ошибку, и локально, и на сервере.

`diff` и `deploy` требуют запущенный сервер и токен. `cert check` и `cert run` читают локальный JSON и к серверу не обращаются.

## Переменные

| Переменная | По умолчанию | Смысл |
|---|---|---|
| `ISPF_BASE_URL` | `http://localhost:8080` | Адрес сервера для удалённых команд. |
| `ISPF_API_TOKEN` | пусто | Bearer-токен. Если задан, файл учётных данных не читается. |
| `ISPF_HOME` | домашний каталог пользователя | Каталог, внутри которого лежит `.ispf/credentials.json`. |
| `ISPF_USERNAME`, `ISPF_PASSWORD` | пусто | Для `login`, если флаги не указаны. |
