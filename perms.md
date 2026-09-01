# Права CodeEconomy

Полный перечень нод прав мода. Источник истины при расхождении: класс `EconomyNodes`
(`internal/EconomyNodes.java`). Сюда же вносятся новые ноды тем же коммитом, что и код.

Ключи перевода живут в соседней ветке имён и нодами не являются: подсказки под
`codeeconomy.command.*`, сообщения под `codeeconomy.message.*`. Выдавать по маске
`codeeconomy.admin.*` можно не опасаясь зацепить строки перевода.

## Деньги игрока

| Нода | Команда | Что даёт |
|---|---|---|
| `codeeconomy.balance` | `/balance`, `/bal`, `/money` | посмотреть свой баланс |
| `codeeconomy.balance.other` | `/balance <ник>` | посмотреть чужой баланс |
| `codeeconomy.pay` | `/pay <ник> <сумма>` | перевод другому игроку |
| `codeeconomy.baltop` | `/baltop [страница]` | топ по валюте |
| `codeeconomy.history` | `/history [страница]` | свои последние операции |

## Администрирование денег

| Нода | Команда | Что даёт |
|---|---|---|
| `codeeconomy.admin.give` | `/eco give <ник> <сумма>` | выдать деньги |
| `codeeconomy.admin.take` | `/eco take <ник> <сумма>` | снять деньги |
| `codeeconomy.admin.set` | `/eco set <ник> <сумма>` | установить баланс |
| `codeeconomy.admin.reset` | `/eco reset <ник>` | вернуть стартовый баланс |
| `codeeconomy.admin.history` | `/eco history <ник>` | операции любого игрока |
| `codeeconomy.admin.freeze` | `/eco freeze <ник> <on\|off>` | заморозить счёт в обе стороны |

## Обслуживание хранилища

| Нода | Команда | Что даёт |
|---|---|---|
| `codeeconomy.admin.verify` | `/eco verify` | сверка журнала со счетами, отчёт в лог сервера |
| `codeeconomy.admin.checkpoint` | `/eco checkpoint` | принудительный снимок счетов |
| `codeeconomy.admin.compact` | `/eco compact` | свежий снимок и обрезка журнала |
| `codeeconomy.admin.unlock` | `/eco unlock` | снять карантин носителя и открыть операции |
| `codeeconomy.admin.import` | `/eco import <формат> [файл] [--apply]` | перенос балансов из чужого формата |

`/eco unlock` снимает признак, который мод ставит сам, когда журнал ушёл в карантин. Пока признак
стоит, мод поднимается только для чтения при любом числе перезапусков: сначала разберитесь с
`journal.jsonl.quarantine` и потерянным диапазоном `seq` из лога, потом снимайте.

## Обход правил

| Нода | Где действует | Что даёт |
|---|---|---|
| `codeeconomy.bypass.minbalance` | `/pay`, `/eco take`, `/eco set` | увести счёт ниже `minBalance` вплоть до `negativeFloor` валюты |

Ноду спрашивают у автора операции. Вызов из чужого мода без автора обхода не получает: пустое поле
`actor` проставляет кто угодно, и правом это не считается.

## Ключи меты

Не ноды: значения через `PermissionService` ядра, читаются у группы игрока. Кривое значение
трактуется как отсутствующее с записью в лог.

| Ключ | Смысл |
|---|---|
| `codeeconomy.starting` | стартовый баланс группы в мажорных единицах вместо `startBalance` валюты |
| `codeeconomy.paylimit` | личный потолок одного перевода вместо `limits.maxTransfer` |
