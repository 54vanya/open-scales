# Open Scales

Открытое Android-приложение для весов Timemore Black Mirror (ESPRO TES015, Basic 3 TES016, DOT TES017, старые TES08).
BLE-протокол восстановлен из `blackmirror_2.5.0_build125_release_20260910.apk`.

- Kotlin, Jetpack Compose, Material 3 Expressive (`material3 1.5.0-alpha28`), minSdk 26, compile/targetSdk 37.
- Спеки и история изменений — `openspec/` (`openspec list`, `openspec list --specs`).

## Сборка

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

## Структура

- `protocol/` — кадры `A5 5A … CRC16/Modbus`, декодер, legacy TES08, реклама. Чистый Kotlin.
- `ble/` — `BleTransport` поверх `BluetoothGatt`, сканер.
- `session/` — очередь команд, `ScaleSession` (bond → MTU → notify → handshake → READY), `ScaleRepository` (авто-переподключение).
- `ui/` — главный экран, поиск, настройки.
