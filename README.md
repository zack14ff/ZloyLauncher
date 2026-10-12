# ZloyLauncher

![ZloyLauncher](src/main/resources/icons/logo.png)

Лаунчер старых версий Minecraft (от Pre-Classic до 1.5.2) в старом добром виде: фон из земли, кнопки на своих местах, тёмная лента новостей. Форк [BetaCraft Launcher](https://github.com/betacraftuk/betacraft-launcher).

## Что добавлено по сравнению с BetaCraft
- **Несколько аккаунтов**: лицензия (вход через Microsoft) и сколько угодно пиратских.
  - кнопка **Аккаунты** на главном экране: быстрый выбор, добавление лицензии или пиратки;
  - окно **Управление аккаунтами**: выбрать, добавить, переименовать, удалить;
  - ник пиратского аккаунта можно вписать прямо в поле на главном экране;
  - если войти в лицензию не получилось (например, нет интернета), она остаётся в списке, а игра идёт с пиратского аккаунта.
- **Связка с BetaCraft Launcher**: можно работать в папке BetaCraft (`.betacraft`) с общими версиями, сборками и аккаунтами.
  - при первом запуске лаунчер сам предложит это, если найдёт BetaCraft;
  - потом это меняется в меню **Папки** (своя папка / папка BetaCraft / любая другая), после выбора лаунчер перезапускается.
- Меню **Папки**: открыть папку сборки или папку лаунчера.
- Встроенный русский язык, работает без интернета.
- Встроенная страница новостей в стиле старого списка изменений BetaCraft.
- Новое имя, иконка и логотип. Своя папка данных: `%APPDATA%\.zloylauncher` (Windows), `~/.zloylauncher` (Linux), `~/Library/Application Support/zloylauncher` (macOS).
- Автообновление с серверов BetaCraft отключено (иначе оно заменило бы лаунчер на BetaCraft).

Всё остальное осталось от BetaCraft: прокси скинов и звуков, исправления для старых версий, список серверов, каталог модов, дополнения, Discord RPC, настройки сборок.

## Сборка
Нужен JDK 17 или 21 (Gradle 8.14 не запускается на Java 25):

```
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradlew build
```

Готовые файлы в `build/libs`:
- `ZloyLauncher.exe` — запуск двойным кликом, с иконкой. Лаунчер уже внутри: при запуске он распаковывается в `%LOCALAPPDATA%\ZloyLauncher` и запускается через найденную Java (сначала ищется Java 8). Собирается только на Windows (задача `gradlew exe`, используется компилятор C#, встроенный в Windows).
- `ZloyLauncher.jar` — то же самое для `java -jar ZloyLauncher.jar` (Linux, macOS).

Сборка приложения для macOS: `gradlew osxBuildApp`.

## Статус в Discord
Название «Играет в …» и картинку задаёт приложение Discord, а не лаунчер. По умолчанию используется приложение BetaCraft, поэтому там написано «Betacraft Launcher». Чтобы было «ZloyLauncher»:
1. На https://discord.com/developers/applications создайте приложение с именем **ZloyLauncher**.
2. В разделе Rich Presence → Art Assets загрузите [discord/logo.png](discord/logo.png) под именем `logo`.
3. Скопируйте Application ID и допишите в `launcher/launcher.settings` (в папке данных) строку `discordAppId:ВАШ_ID`, либо поменяйте `DISCORD_APP_ID` в `BC.java`.

## Откуда что качается
Версии игры, библиотеки, языки, список серверов и моды по-прежнему загружаются с серверов BetaCraft (`files.betacraft.uk`, `api.betacraft.uk`). Вход в лицензию использует Microsoft-приложение BetaCraft, поэтому на странице входа Microsoft будет написано BetaCraft.

## Лицензия
GPL-3.0, как и у BetaCraft Launcher (см. [LICENSE](LICENSE)). Оригинальный код: https://github.com/betacraftuk/betacraft-launcher
