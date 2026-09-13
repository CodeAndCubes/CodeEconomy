## Что в выпуске

| Файл | Куда |
|---|---|
| `CodeEconomy-*-server.jar` | `mods/` сервера, после CodeCore |
| `CodeEconomy-*-client.jar` | `mods/` клиента, по желанию: он рисует баланс на экране |
| `codeeconomy-*-api.jar` | тем, кто пишет свой провайдер или мод с оплатой |
| `codeeconomy-*-dev.jar` | им же: deobf-версия для dev-запусков |

Сервер объявляет `acceptableRemoteVersions = "*"`, поэтому ванильный клиент заходит без вопросов.
Minecraft 1.7.10, Forge 10.13.4.1614, Java 8 или 17 и 21 под lwjgl3ify.
