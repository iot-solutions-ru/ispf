> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0012-visual-groups.md](../../en/decisions/0012-visual-groups.md).

# ADR-0012: Visual groups

## Статус

Принято (2026-06-23)

## Контекст

Пользователям нужно упорядочивать объекты в дереве explorer без создания структурных контейнеров и без дублирования paths. Один и тот же объект должен появляться в нескольких пользовательских коллекциях, сохраняя единственный канонический `path`.

## Решение

1. Ввести `ObjectType.VISUAL_GROUP` — обычный узел дерева, создаваемый везде, где разрешены children.
2. Хранить membership в зарезервированной переменной `@groupMembers` (JSON-массив `{ path, sortOrder }`).
3. `GET /api/v1/objects?parent=<group>` возвращает member objects с `groupRef: true` и `groupContextPath`; `path` остаётся каноническим path участника.
4. `GET /api/v1/objects?parent=<structural-parent>` не включает объекты, входящие в любую visual group; полный flat listing (`parent` опущен) без изменений для pickers и search.
5. Структурные children под `VISUAL_GROUP` запрещены; изменение membership — через `PUT /api/v1/objects/by-path/group-members`.
6. При удалении объекта все groups сканируются и stale member paths удаляются.
7. Explorer multi-select использует composite row keys (`groupPath::memberPath`) для group references; bulk delete работает по canonical paths; remove-from-group — отдельное контекстное действие.

## Последствия

- Groups сохраняются на server и доступны всем users/sessions.
- Tree lazy-loading обрабатывает visual groups как containers для expand/chevron.
- Binding rules и federation semantics без изменений — groups — UI-only organization.
