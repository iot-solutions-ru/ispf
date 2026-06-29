# ISPF Marketing Landing

Исходник для редактирования — **`index.html`** со ссылками на PNG в `assets/screenshots/`.

## Редактирование

1. Меняйте текст и разметку в `index.html`.
2. Картинки кладите в `assets/screenshots/` (имена как в `src="assets/screenshots/..."`).

## Сборка standalone (один файл для рассылки)

```powershell
cd marketing/landing
python build.py
```

Результат: **`dist/landing.html`** (~2 MB) — все скриншоты встроены как base64, можно открыть или отправить одним файлом.

## Просмотр исходника локально

Откройте `index.html` в браузере из папки `marketing/landing` (относительные пути к assets должны резолвиться).

## Скриншоты

| Файл | Назначение |
|------|------------|
| `hero-dashboard.png` | Hero · SNMP SCADA |
| `operator-hmi.png` | Мини-ТЭЦ · operator HMI |
| `explorer-object-tree.png` | Object tree |
| `dashboard-builder.png` | HMI builder |
| `bpmn-workflow.png` | BPMN / MES |
| `alert-automation.png` | Alert rules |
| `application-deploy.png` | MES bundle |
| `federation.png` | Federation |
| `ai-agent.png` | Operator AI knowledge base |
