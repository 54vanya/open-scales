# Tasks

## 1. Ядро эмулятора (`sim/`, чистый Kotlin)

- [x] 1.1 `ScaleEmulator(nowMs)`: ответы на чтения handshake и настроек (модель `TES017`, имя «Virtual DOT», заряд, единицы, таймер, прочие настройки), подтверждение записей, отказы по группам `tare/timer/settings/all`, запись настроек меняет последующие чтения. Проверка: юнит-тест — ответы на `0x05/0x13/0x06/0x08/0x02/0x0C`, отказ тары при `reject tare`, запись имени читается обратно
- [x] 1.2 Вес и поток по времени: опорный вес, пролив (начало, длительность, прирост), тара, шум (по умолчанию 0), кадр `0x01` в граммах ×10 / унциях ×100, `unit` шлёт кадр `0x06`. Проверка: юнит-тест на виртуальном времени — `pour 250 30`: 125 г на 15 с, 250 г после 30 с, поток ≈ 8.3 г/с в середине и 0 после; тара обнуляет; 250 г в oz → 8.82
- [x] 1.3 Таймер весов: старт/пауза/сброс по `0x02`, секунды в кадре веса, сброс нулевого таймера работает как тара. Проверка: юнит-тест
- [x] 1.4 `FakeBleTransport` на ядре: прежнее API (`readResponses`, `rejectedWrites`, `silentCommands`, `emitFrame`, `emitWeight`, `disconnectFromDevice`, хуки подключения и bond) без тикера. Проверка: `./gradlew --no-watch-fs :app:testDebugUnitTest` — все прежние тесты зелёные без правок сценариев

## 2. Виртуальные весы в приложении

- [x] 2.1 `SimulatedBleTransport`: задержка подключения 300 мс, отказ `connect()` при `drop`, тикер 10 Гц после включения notify, остановка в `close()`, `Disconnected` текущему транспорту при `drop`. Проверка: юнит-тест с `ScaleRepository` в `backgroundScope` — READY, кадры идут, `drop` → переподключение не удаётся, `back` → READY
- [x] 2.2 `ScaleSimulator.execute(command)`: разбор всех команд таблицы спеки, ответ `status`, ошибки на неизвестную команду и неверный аргумент без изменения состояния, `reset`. Проверка: юнит-тест на разбор и `status`
- [x] 2.3 `OpenScalesApp`: `simulator` только при `BuildConfig.DEBUG`, `transportFactory` отдаёт виртуальный транспорт (через `LoggingBleTransport`) для `02:00:5C:A1:E0:01`, `isVirtual(address)`. Проверка: эмулятор — подключение попадает в журнал BLE с кадрами handshake
- [x] 2.4 Поиск: секция «Виртуальные весы» в `ScanScreen` над списком и над карточками разрешений/Bluetooth, касание подключает; строки в `values`/`values-ru`. `MainActivity` и баннер: проверка Bluetooth и разрешений не блокирует виртуальные весы. Проверка: эмулятор с отозванными разрешениями (`pm revoke`) — строка видна, подключение до READY, после перезапуска авто-подключение, баннер без «Bluetooth выключен»

## 3. Управление из терминала

- [x] 3.1 `SimControlReceiver` в `app/src/debug` и debug-манифесте (`exported`, action `dev.openscales.SIM`, extra `cmd`, ответ через `setResultData`). Проверка: `adb shell am broadcast -n dev.openscales/.debug.SimControlReceiver -a dev.openscales.SIM --es cmd status` печатает состояние
- [x] 3.2 `tools/dev.sh sim <команда>` печатает только ответ; `--no-watch-fs` во всех вызовах `gradlew` в `dev.sh`. Проверка: `ANDROID_SERIAL=emulator-5554 tools/dev.sh sim status`, `tools/dev.sh check` доходит до конца
- [x] 3.3 Сценарии спеки на эмуляторе: тара с 312 г, `pour 250 30` с потоком на главном экране, `unit oz`/`g`, `reject tare` → «Весы отклонили команду», `drop` → переподключение, `back` → READY, `sim weight abc` → ошибка. Проверка: скриншоты `tools/dev.sh shot`

## 4. Проверка и документация

- [x] 4.1 Release: `./gradlew --no-watch-fs :app:assembleRelease`, в APK нет классов `dev.openscales.sim` и `SimControlReceiver` (`apkanalyzer dex packages`, `apkanalyzer manifest print`)
- [x] 4.2 `CLAUDE.md`: пакет `sim/`, команды `dev.sh sim`, заметка про `--no-watch-fs`. Проверка: чтение файла
- [x] 4.3 `./gradlew --no-watch-fs :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — тесты зелёные, lint «No issues found»; `openspec validate --all --strict`
