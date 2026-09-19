# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Open Scales — Android-приложение (Kotlin, Compose, Material 3 Expressive) для BLE-весов Timemore Black Mirror.
Протокол восстановлен из `blackmirror_2.5.0_build125_release_20260910.apk` (лежит в корне, в git не входит).
Пользователь общается по-русски; UI-строки, комментарии и артефакты OpenSpec — на русском.

## Команды

JDK 17 обязателен (системной Java нет), SDK — `/opt/homebrew/share/android-commandlinetools` (`local.properties`).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug      # полная проверка (lint должен быть «No issues found»)
./gradlew :app:testDebugUnitTest --tests '*ScaleSessionTest'           # один класс; '*ScaleSessionTest.link*' — один тест
./gradlew :app:assembleRelease                                         # R8, подпись debug-ключом, ~1.7 МБ
```

`tools/dev.sh` — харнес для устройства: `install [debug|release]`, `start`, `grant`, `stayon`, `shot <name>`,
`tap x y`, `find "<text>"` (bounds через uiautomator), `ble` (фильтрованный logcat TX/RX), `scan-log`,
`emu` / `emu-narrow` / `emu-kill` (AVD `openscales_phone`, API 36), `decompile` (jadx исходного APK).
Без аргументов печатает список. Устройство выбирается по `$ANDROID_SERIAL`, иначе первый телефон.

## Архитектура

```
protocol/  чистый Kotlin, без Android: Frame/FrameCodec (A5 5A + CRC16), MessageDecoder → ScaleMessage,
           LegacyCodec (TES08), Advertisement, Cmd/ScaleModel
ble/       BleTransport (интерфейс) ← AndroidBleTransport (BluetoothGatt, одна GATT-операция в полёте через Mutex),
           ScaleScanner, BleJournal + LoggingBleTransport (журнал, только debug)
session/   CommandQueue → ScaleSession (одна попытка подключения: фазы, bond, MTU, notify, handshake, команды)
           → ScaleRepository (синглтон: текущая сессия, запомненные весы, авто-переподключение)
data/      DataStore «scale»: SavedDeviceStore (весы) и AppSettingsStore (звук, режим срабатывания кнопок)
sound/     BeepPcm → AudioTrackBeeper (MODE_STATIC) ← ButtonSound (решает, звучать ли)
ui/        Activity на каждый экран: MainActivity (dashboard), ScanActivity, SettingsActivity; debug/JournalActivity
```

- Зависимости собираются вручную в `OpenScalesApp` (без DI). `appScope` — `Dispatchers.Main.immediate`: вся мутация
  состояния сессии/репозитория однопоточна, это допущение используется в коде.
- Экраны — отдельные Activity намеренно: так системный предиктивный жест «назад» работает без собственной анимации
  (Navigation Compose давал «странный фейд»). Состояние общее через `ScaleRepository`, у каждой Activity своя
  `ScaleViewModel`. Долгие операции, которые должны пережить закрытие экрана (`forget`), запускаются в `app.appScope`.
- Кнопки управления оптимистичны: состояние таймера меняется до ответа весов и откатывается при отказе;
  срабатывание по касанию — `rememberPressAction` в `DashboardScreen` (настройка «При касании/При отпускании»).
- Debug-only код: журнал включается по `BuildConfig.DEBUG`, `JournalActivity` и её манифест — в `app/src/debug`.
  Логи TX/RX и сканера тоже только в debug.
- Material 3 — `1.5.0-alpha28` (Expressive API: `ButtonGroup`, `ToggleButton`, `SegmentedListItem`,
  `LoadingIndicator`, flexible top app bars). Opt-in в `app/build.gradle.kts`. Эти библиотеки требуют compileSdk 37.

## Протокол и проверка на железе

Справка с байтами, таймингами и местами в декомпилированном APK — `docs/protocol.md`. Главное, что видно только
на реальных весах: CRC в кадрах от весов нулевой (строго не проверять), вес приходит сам кадрами `type=0x01`,
процент батареи во втором байте ответа `0x05`.

Тесты сессии/репозитория идут на `FakeBleTransport` (эмулятор весов в `app/src/test/.../session`) в `backgroundScope`;
`advanceUntilIdle()` фоновые корутины не прокручивает — используйте `settle()` из `TestTime.kt`.
Регрессии по реальным кадрам добавляйте байтами из журнала BLE.

## OpenSpec

Изменения поведения оформляются через OpenSpec (`/opsx:propose`, `/opsx:apply`, `/opsx:archive`; `openspec list`,
`openspec validate --all --strict`). Артефакты на русском, заголовки и SHALL/MUST — на английском.
MODIFIED-требование заменяет блок целиком: копируйте все существующие сценарии (валидатор ругается на пропавшие).
Незакрытые задачи чейнджей — в основном ручные проверки на телефоне с весами.
