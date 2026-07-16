> **Язык:** русская версия (вычитка). Канонический английский: [en/pid-symbols-legal.md](../en/pid-symbols-legal.md).

# Пакет символов P&ID — правовой статус

**Статус:** Опция **B** реализована (2026-06) — исходное функциональное оформление ISA/ISO.

Пакет: `apps/web-console/src/scada/symbols/packs/ispf-pid-v1/`  
Генератор: [`tools/symbol-pack-isa`](../../tools/symbol-pack-isa)  
Лицензия: **Apache-2.0** (основные участники ISPF)

## Текущая модель

| Товар | Статус |
|------|--------|
| Vendor WMF/SymbolFactory import | **Removed** — `tools/symbol-import/` deprecated |
| Symbol geometry | **Original** — drawn in `tools/symbol-pack-isa/src/symbols.ts` (64×64, CSS vars) |
| Конвенции | Бирки приборов ISA-5.1 (PI, TI, FT, …), функциональные формы клапанов/оборудования |
| Граф | ~58 символов (расширение с помощью генератора редактирования, а не импорта поставщика) |

Регенерировать:

```bash
cd tools/symbol-pack-isa && npm install && npm run build
```

## Почему это меньший риск, чем импорт поставщика

Функциональные/стандартные формы P&ID (клин задвижки, пузырек прибора, контур резервуара)
выражать **значение процесса**, а не декоративное произведение конкретного поставщика. Символы ISPF
**авторизован на основе соглашений**, а не на основе путей Siemens WMF.

Адвокат может по-прежнему учитывать, что конкретные изображения не очень точно симулируют патентованный каталог.

## Контрольный список для консультанта (необязательно)

- [ ] Пример обзора: задвижка, регулирующий клапан, PI-баллон, вертикальный бак, теплообменник.
- [ ] Подтверждено отсутствие поставщика WMF/SVG в истории git для текущих файлов пакета (после регенерации).
- [ ] Approved Apache-2.0 attribution in `LICENSE.md` and release bundle.
- [ ] Утвержденная формулировка для клиента, если Enterprise EULA ссылается на символы SCADA.

**Подписание:** _______________ Дата: ___________

## Устарело (не использовать)

`tools/symbol-import/` — WMF pipeline from WinCC/TIA. **Forbidden** for ISPF releases.

## Связанный

- [license](license.md)
- [license-compliance](license-compliance.md)
- [tools/symbol-pack-isa/README.md](readme.md)
