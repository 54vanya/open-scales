# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Open Scales — Android-приложение (Kotlin, Compose, Material 3 Expressive) для BLE-весов Timemore Black Mirror.
Протокол восстановлен из `blackmirror_2.5.0_build125_release_20260910.apk` (лежит в корне, в git не входит).
Пользователь общается по-русски; UI-строки, комментарии и артефакты OpenSpec — на русском.

## Команды

JDK 17 обязателен (системной Java нет), SDK — `/opt/homebrew/share/android-commandlinetools` (`local.properties`).
Gradle запускать с `--no-watch-fs`: иначе демон намертво виснет в нативном вотчере файлов (`tools/dev.sh` флаг уже добавляет).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew --no-watch-fs :app:verifyRoborazziDebug :app:assembleDebug :app:lintDebug  # полная проверка: юнит-тесты + сверка скриншотов
./gradlew --no-watch-fs :app:recordRoborazziDebug --tests '*ScreenshotTest'        # перезаписать эталоны скриншотов
./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*ScaleSessionTest'        # один класс; '*ScaleSessionTest.link*' — один тест
./gradlew --no-watch-fs :app:assembleRelease                                      # R8, подпись debug-ключом, ~1.8 МБ
```

`tools/dev.sh` — харнес для устройства: `install [debug|release]`, `start`, `grant`, `stayon`, `shot <name>`,
`tap x y`, `find "<text>"` (bounds через uiautomator), `ble` (фильтрованный logcat TX/RX), `scan-log`,
`emu` / `emu-narrow` / `emu-kill` (AVD `openscales_phone`, API 36).
`sim <команда>` — виртуальные весы (см. ниже). Без аргументов печатает список. Устройство выбирается по
`$ANDROID_SERIAL`, иначе первый телефон — а без телефона это может оказаться TV в adb, поэтому на эмуляторе
`export ANDROID_SERIAL=emulator-5554`.

## Архитектура

```
protocol/  чистый Kotlin, без Android: Frame/FrameCodec (A5 5A + CRC16), MessageDecoder → ScaleMessage,
           LegacyCodec (TES08), Advertisement, Cmd/ScaleModel
ble/       BleTransport (интерфейс) ← AndroidBleTransport (BluetoothGatt, одна GATT-операция в полёте через Mutex),
           ScaleScanner, BleJournal + LoggingBleTransport (журнал, только debug)
session/   CommandQueue → ScaleSession (одна попытка подключения: фазы, bond, MTU, notify, handshake, команды)
           → ScaleRepository (синглтон: текущая сессия, запомненные весы, авто-переподключение)
data/      DataStore «scale»: SavedDeviceStore (весы) и AppSettingsStore (звук, режим срабатывания кнопок)
recipe/    чистый Kotlin: рецепт, встроенный рецепт, ход шагов, распределение воды, детектор пролива
sim/       чистый Kotlin: ScaleEmulator (весы протокола 2025 без радио, общий с тестовым FakeBleTransport),
           SimulatedBleTransport, ScaleSimulator (виртуальные весы debug, разбор команд из терминала)
sound/     BeepPcm → AudioTrackBeeper (MODE_STATIC) ← ButtonSound (решает, звучать ли)
ui/        Activity на каждый экран: MainActivity (вкладки «Весы»/«Рецепты»), ScanActivity, SettingsActivity,
           BrewActivity (варка по рецепту); debug/JournalActivity, debug/SimControlReceiver
```

- Зависимости собираются вручную в `OpenScalesApp` (без DI). `appScope` — `Dispatchers.Main.immediate`: вся мутация
  состояния сессии/репозитория однопоточна, это допущение используется в коде.
- Экраны — отдельные Activity намеренно: так системный предиктивный жест «назад» работает без собственной анимации
  (Navigation Compose давал «странный фейд»). Состояние общее через `ScaleRepository`, у каждой Activity своя
  `ScaleViewModel`. Долгие операции, которые должны пережить закрытие экрана (`forget`), запускаются в `app.appScope`.
- Кнопки управления оптимистичны: состояние таймера меняется до ответа весов и откатывается при отказе;
  срабатывание по касанию — `rememberPressAction` в `DashboardScreen` (настройка «При касании/При отпускании»).
- Debug-only код: журнал включается по `BuildConfig.DEBUG`, `JournalActivity` и её манифест — в `app/src/debug`.
  Логи TX/RX и сканера тоже только в debug. В журнал в debug также пишутся тики секундомера (`TIMER`) и сторож
  главного потока `MainThreadWatchdog` (`STALL`, если поток занят дольше 200 мс),
  паузы в потоке кадров дольше 300 мс (`GAP`). Важные события (всё, кроме TX/RX и обычных тиков) дублируются в отдельный
  буфер на 500 записей, который поток кадров не вытесняет — экран журнала переключается «Всё/Важное».
- Material 3 — `1.5.0-alpha28` (Expressive API: `ButtonGroup`, `ToggleButton`, `SegmentedListItem`,
  flexible top app bars; индикатор занятости — классический `CircularProgressIndicator` через `BusyIndicator`). Opt-in в `app/build.gradle.kts`. Эти библиотеки требуют compileSdk 37.

## Протокол и проверка на железе

Справка с байтами и таймингами — `docs/protocol.md` (и `docs/firmware.md`), декомпиляция APK — `tools/decompile.sh`: только локально, в `.gitignore`, в git не входят. Главное, что видно только
на реальных весах: CRC в кадрах от весов нулевой (строго не проверять), вес приходит сам кадрами `type=0x01`,
процент батареи во втором байте ответа `0x05`.

**Виртуальные весы (debug).** В поиске есть «Виртуальные весы» (Virtual DOT, адрес `02:00:5C:A1:E0:01`): полный
handshake через настоящую сессию, кадры веса 10 Гц, тара, таймер, журнал BLE; Bluetooth и разрешения не нужны —
работает на эмуляторе Android. Управление: `tools/dev.sh sim weight 15 | pour 250 30 | noise 0.1 | unit oz |
battery 20 | drop | back | reject tare|timer|settings|all|none | status | reset` (вес всегда в граммах). Эмулятор живёт
в процессе: после `am force-stop` состояние сбрасывается. В release код вырезается R8.

Тесты сессии/репозитория идут на `FakeBleTransport` (эмулятор весов в `app/src/test/.../session`) в `backgroundScope`;
`advanceUntilIdle()` фоновые корутины не прокручивает — используйте `settle()` из `TestTime.kt`.
Регрессии по реальным кадрам добавляйте байтами из журнала BLE.

## Скриншот-тесты одобренных экранов

Экран, который пользователь одобрил, фиксируется скриншот-тестом (Roborazzi + Robolectric, `app/src/test/.../ui/screenshots`):
Compose рисуется в JVM без эмулятора, эталоны — `app/src/test/screenshots/*.png` в репозитории. Новый или изменённый
одобренный вид — тест и эталон в том же чейндже; перед записью эталон просмотреть глазами. Упавшая сверка кладёт
`*_compare.png` (эталон / разница / новое) в `app/build/outputs/roborazzi`. Экраны рисуются напрямую (без Activity),
с простым `Application`, светлой темой и собственными цветами, по-русски, 411×914 dp (узкий — 320 dp). Robolectric — SDK
34: SDK 35+ требует Java 21, проект на Java 17.

## OpenSpec

Изменения поведения оформляются через OpenSpec (`/opsx:propose`, `/opsx:apply`, `/opsx:archive`; `openspec list`,
`openspec validate --all --strict`). Артефакты на русском, заголовки и SHALL/MUST — на английском.
MODIFIED-требование заменяет блок целиком: копируйте все существующие сценарии (валидатор ругается на пропавшие).
Незакрытые задачи чейнджей — в основном ручные проверки на телефоне с весами.
