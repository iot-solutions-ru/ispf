> **Язык:** русская версия (вычитка). Канонический английский: [en/scada-symbol-library.md](../en/scada-symbol-library.md).

# Библиотека символов SCADA (BL-94)

Полный справочник по **символам пакета P&ID**, **загрузке пользовательского SVG** и **библиотеке документов** в редакторе мнемосхем ISPF.

См. также: [SCADA.md](scada.md), [SCADA_MIMIC.md](scada-mimic.md), [tools/symbol-pack-isa](readme.md).

---

## Обзор каталога

| Source | Palette category | `symbolId` | Count |
|--------|------------------|------------|-------|
| ISA/ISO P&ID pack | `pack-valves`, `pack-pumps`, … | `pack.ispf-pid.*` | **218** (`manifest.json` `totalSymbols`) |
| Inline SVG | `common` | `custom.svg` | 1 template |
| User library | `custom` | `custom:{id}` | Per mimic document |

Pack manifest: `apps/web-console/src/scada/symbols/packs/ispf-pid-v1/manifest.json`  
Regenerate: `npm run build:pid-symbols --prefix apps/web-console`

---

## Символы стандартной упаковки

1. Откройте редактор мнемосхем (Проводник → Мнемосхема → **Редактировать диаграмму**).
2. Инструмент **Место** → категория **упаковочные баки** / **упаковочные клапаны** / …
3. Нажмите холст → элемент получит `symbolId: pack.ispf-pid.vertical-tank` (пример).
4. Привязки в панели свойств → переменные устройства.

Символы пакета в репозитории представлены в виде **статического SVG**; они не копируются в `diagramJson`, если вы не конвертируете их в библиотеку (ниже).

---

## Пользовательский SVG — три режима

| Режим | Когда использовать | Хранение |
|------|-------------|---------|
| **Inline** | One-off graphic | `element.props.svg` on element with `symbolId: custom.svg` |
| **Document library** | Reuse across one mimic | `customSymbols[]` + `symbolId: custom:{id}` |
| **Настройка пакета** | Начните со стандарта, подкорректируйте геометрию | Преобразовать пакет → запись в библиотеке |

---

## Загрузить SVG (пользовательскую библиотеку)

1. Редактор мнемосхем → категория палитры **Пользовательская** (Свои SVG).
2. **Загрузить SVG** → выберите файл `.svg` (рекомендуемый максимальный размер — 256 КБ).
3. Символ появится на палитре `inUserLibrary: true`.
4. Разместите на холсте, как любой символ упаковки.

**Серверная сторона:** SVG хранится в мнемосхеме `diagramJson` объекта MIMIC (отдельного хранилища ресурсов нет).

### Правила загрузки (применяются в пользовательском интерфейсе)

`sanitizeSvgMarkup()` strips:

- Tags: `script`, `iframe`, `object`, `embed`, `link`, `meta`, `style`, `foreignObject`
- Event handlers: `onclick`, `onload`, …
- Dangerous `href` / `xlink:href` (`javascript:`, `data:text/html`)

Используйте простую векторную разметку (`path`, `rect`, `circle`, `g`, `text`). Предпочитайте `fill="var(--text)"` для цветов с учетом темы.

### Пример минимального файла SVG

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">
  <rect x="4" y="4" width="56" height="56" rx="8" fill="#161b22" stroke="#30363d" stroke-width="2"/>
  <text x="32" y="36" text-anchor="middle" fill="#8b949e" font-size="10">PUMP</text>
</svg>
```

После загрузки: внутренний HTML сохраняется в `customSymbols[].svg`, **порты** по умолчанию** находятся в средних точках С/Ю/В/З.

---

## Преобразование символа пакета в редактируемую библиотеку

1. Разместите символ упаковки (например, задвижку).
2. Выберите элемент → **Редактировать как SVG** (Редактировать как SVG).
3. Отредактируйте разметку на панели кода или загрузите замену.
4. **Обновить символ в библиотеке** → устанавливает `inUserLibrary: true`, добавляет в **Пользовательскую** палитру.
5. В новых экземплярах используется `symbolId: custom:{id}`; исходный идентификатор пакета сохраняется в `props.sourceSymbolId` при преобразовании через редактор.

Массовое преобразование (устаревшие диаграммы): меню редактора **Преобразовать встроенные модули в библиотеку** — однократная миграция для старых идентификаторов символов React.

---

## Поведения и привязки

Для живого HMI (цвет, уровень, видимость):

```json
{
  "id": "tank-1",
  "symbolId": "custom:lib-tank-a",
  "bindings": [
    { "target": "fill", "objectPath": "root.platform.devices.t1", "variable": "level", "field": "value" }
  ],
  "props": { "behaviors": [{ "type": "tankLevel", "binding": "level" }] }
}
```

Define `bindingSchema` on `customSymbols[]` entry for editor hints. Reference: `packages/ispf-server/src/main/resources/bootstrap/mini-tec-mimic.json`.

---

## Импорт/экспорт

| Действие | Путь |
|--------|------|
| Export full diagram | Editor → Import/Export → copy `diagramJson` |
| CI re-export | `npx tsx src/scada/templates/exportTankFarmMimic.ts` |
| Bootstrap seed | `PUT /api/v1/mimics/by-path/diagram` |

`customSymbols` travels with `diagramJson` — backup mimics before bulk edits.

---

## CI/тесты

| Тест | Путь |
|------|------|
| Pack manifest load | `symbolPackLoader.test.ts` — ≥60 symbols, 8 categories |
| SVG sanitize / upload parse | `customSvg.test.ts` |
| Behavior engine | `symbolBehaviors.test.ts`, `svgSymbolEngine.test.ts` |

```bash
cd apps/web-console && npm run test -- src/scada/customSvg.test.ts src/scada/symbols/symbolPackLoader.test.ts
```

---

## Юридическая информация

- Пакет **ispf-pid-v1**: оригинальная работа Apache-2.0 ([ЛИЦЕНЗИЯ](license.md)).
- **Не** импортируйте изображения производителей SymbolFactory/TIA/P&ID, защищенные авторским правом.
- Legacy WMF importer (`tools/symbol-import/`) is **deprecated**.

---

## Поиск неисправностей

| Выпуск | Исправить |
|-------|-----|
| Custom symbol not in palette | Set `inUserLibrary: true` on `customSymbols[]` entry |
| Upload blank / broken | Ensure root `<svg>` with valid `viewBox`; avoid `<style>` blocks |
| Colors wrong in dark theme | Use `var(--text)`, `var(--border)` or pack CSS vars |
| Pack category empty | Run `npm run build:pid-symbols`; hard-refresh browser |
