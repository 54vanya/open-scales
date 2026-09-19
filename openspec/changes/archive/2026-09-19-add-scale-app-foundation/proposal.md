# Proposal

## Why

Весы Timemore Black Mirror (TES015 ESPRO, TES016 Basic 3, TES017 DOT и старые двойные TES08) работают только через фирменное приложение `blackmirror 2.5.0`, которое тянет аккаунты, облако, аналитику и рецепты. Нужно открытое, лёгкое Android-приложение, которое делает только главное: подключается к весам по BLE, показывает вес и таймер, управляет тарой/таймером и настройками весов.

## What Changes

- Новый Android-проект `open-scales` (Kotlin, Jetpack Compose, Material 3 Expressive, minSdk 26, compileSdk/targetSdk 37).
- Реализация BLE-протокола весов, восстановленного из `blackmirror_2.5.0_build125_release_20260910.apk`:
  - протокол «2025» (сервис `FFF0`, notify `FFF1`, write `FFF2`, кадры `A5 5A` + CRC16/Modbus) для TES015/016/017;
  - legacy-протокол TES08 (сервис `181D`, indicate `2A9D`, команды в `553f4e49-…`).
- Поток подключения, повторяющий оригинал: сканирование → GATT → bonding → MTU → подписка на notify → handshake-чтения → READY; автоматическое переподключение к последнему устройству.
- Главный экран «как у весов»: текущий вес, скорость потока, таймер, батарея, кнопки «Тара», «Старт/Пауза», «Сброс».
- Экран поиска и подключения весов.
- Экран настроек весов: единицы, звук, авто-отключение, чувствительность, точность, яркость (ESPRO), имя, версия прошивки, выключение, сброс к заводским, забыть устройство.
- Модульные тесты кодека протокола и логики сессии на фейковом транспорте.

## Capabilities

### New Capabilities
- `scale-protocol`: формат кадров, CRC, команды и разбор ответов/уведомлений весов.
- `scale-connection`: поиск весов, подключение, сопряжение, handshake, состояние соединения и авто-переподключение.
- `scale-dashboard`: главный экран с весом, таймером и кнопками управления.
- `scale-settings`: чтение и изменение настроек подключённых весов.

### Modified Capabilities
<!-- нет -->

## Impact

- Новый код: весь Android-проект (`app/`), Gradle-обвязка, OpenSpec.
- Разрешения Android: `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_CONNECT`, для API ≤ 30 — `BLUETOOTH` / `BLUETOOTH_ADMIN` / `ACCESS_FINE_LOCATION`.
- Зависимости: AndroidX Compose BOM, Material 3 (Expressive API), Navigation Compose, DataStore, kotlinx-coroutines; тесты — JUnit4 + kotlinx-coroutines-test.
- Проверка на реальных весах вынесена в отдельный change `verify-real-scale-connection`.
