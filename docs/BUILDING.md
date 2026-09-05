# Сборка и установка

## Что нужно

| | Версия | Зачем |
|---|---|---|
| JDK | 21 | AGP 8.7 требует 17 и новее; проект компилируется под 21 |
| Android SDK | platform 35 | `compileSdk = 35` |
| Gradle | 8.11.1 | через `gradlew`, скачается сам |

Android Studio не обязателен — всё собирается из командной строки. Если он есть,
откройте проект как обычно; `local.properties` укажет на SDK (файл не в репозитории,
создайте свой: `sdk.dir=D:/Android/Sdk`).

## Команды

```sh
./gradlew test              # разбор плейлистов, домен — за секунды, без эмулятора
./gradlew assembleDebug     # APK в app/build/outputs/apk/debug/
./gradlew assembleRelease   # с R8: примерно вдесятеро меньше debug
./gradlew installDebug      # собрать и поставить на подключённые часы
```

---

## Особенность этой машины: кириллица в пути

На Windows, где имя пользователя содержит кириллицу, обычный `gradlew` падает ещё до
чтения настроек:

```
java.io.IOException: Unable to establish loopback connection
Caused by: java.net.SocketException: Invalid argument: connect
    at java.base/sun.nio.ch.UnixDomainSockets.connect0(Native Method)
```

Причина не в Gradle. С JDK 16 `Selector.open()` на Windows создаёт внутреннюю пару
сокетов через **AF_UNIX**, а путь к сокету берёт из `java.io.tmpdir`. По пути с
кириллицей реализация AF_UNIX привязывается, но не соединяется. Селектор не
открывается — а без него не работает ни демон Gradle, ни компилятор Kotlin.

Тот же путь ломает и запуск тестов, но иначе: воркер получает classpath внутрь
`GRADLE_USER_HOME`, тоже лежащего в профиле, и падает с
`Could not find or load main class GradleWorkerMain`.

Обе переменные должны быть заданы **до** старта JVM, чего `gradle.properties` не умеет.
Поэтому рядом лежит `gw.cmd` — он подставляет ASCII-пути и зовёт `gradlew`:

```sh
gw.cmd test
gw.cmd assembleDebug
```

На машине с ASCII-путями достаточно обычного `gradlew`.

---

## Установка на часы

### Подключение по Wi-Fi

На часах: **Настройки → Для разработчиков → Отладка по Wi-Fi**. Там же показан
адрес и порт.

```sh
adb connect 192.168.1.105:ПОРТ
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> [!warning] Порт меняется
> Wear OS закрывает порт отладки по таймауту, и при повторном включении выдаёт
> **новый**. Если `adb` пишет `device offline` или `отверг запрос на подключение` —
> включите отладку заново и посмотрите новый порт. Это происходит регулярно;
> при долгой отладке заново подключаться придётся несколько раз.

### Плитка

Добавляется через отладочный интерфейс, без хождения по меню:

```sh
adb shell am broadcast \
  -a com.google.android.wearable.app.DEBUG_SURFACE \
  --es operation add-tile \
  --ecn component com.pyradio.wear.debug/com.pyradio.wear.tile.RadioTileService
```

Ответ `Broadcast completed: result=1, data="Index=[0]"` — плитка встала на позицию 0.
Убрать: `--es operation remove-tile`.

### Комплик

Только руками: долгое нажатие на циферблате → **Настроить** → слот → **PyRadio →
«Что играет»**.

---

## Проверка на устройстве

```sh
# что с воспроизведением
adb shell dumpsys media_session | grep "state=PlaybackState"
#   state=3 — играет, state=2 — пауза, state=0 — остановлено

# системная громкость музыки
adb shell dumpsys audio | grep -A6 "^- STREAM_MUSIC"

# снимок экрана
adb exec-out screencap -p > screen.png
```

> [!tip] Экран гаснет через 30 секунд
> Автоматизировать нажатия мешает засыпание: тап по спящему экрану только будит его.
> Перед каждым `input tap` посылайте `input keyevent KEYCODE_WAKEUP`, либо поднимите
> таймаут на время отладки и **верните обратно**:
> ```sh
> adb shell settings get system screen_off_timeout   # запомните значение
> adb shell settings put system screen_off_timeout 300000
> # ... отладка ...
> adb shell settings put system screen_off_timeout 30000
> ```

### Чего проверить не получится

**Поворот короны.** Колесо — это `/dev/input/event0` с `REL_WHEEL`, но `sendevent`
без root отвечает `Permission denied`, а у `adb shell input` источника
`rotaryencoder` нет вовсе. Громкость колесом проверяется только рукой.

---

## Проверка плейлиста по сети

Отдельная диагностика, не тест — см. [PLAYLIST.md](PLAYLIST.md):

```sh
PYRADIO_SMOKE=1 ./gradlew :core:resolver:test --tests '*RealStationsSmokeTest*' -i
```
