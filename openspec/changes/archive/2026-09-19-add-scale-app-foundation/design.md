# Design

## Context

Исходник — только APK `blackmirror_2.5.0_build125`: протокол восстановлен ради совместимости с весами. Мы переносим **механизм** (UUID, кадры, CRC, порядок фаз, тайминги, очередь команд), а не код.

## Goals / Non-Goals

**Goals:**
- Протокол как чистый Kotlin без Android-зависимостей — покрывается JVM-юнит-тестами.
- Сессия подключения тестируется на фейковом транспорте, без реального Bluetooth.
- UI целиком на Compose + Material 3 Expressive.

**Non-Goals:**
- Рецепты, режимы заваривания (ESPRO-стадии `0x08` пишем только на чтение), облако, аккаунты, обновление прошивки (OTA), Wi-Fi-команды TES08.
- Фоновая работа с экраном выключенным (foreground service) — соединение живёт, пока жив процесс приложения.
- Проверка на реальном железе — change `verify-real-scale-connection`.

## Decisions

### 1. Слои
```
ui (Compose screens + ViewModels)
  └─ ScaleRepository  — синглтон приложения: StateFlow<ScaleUiState>, сохранённое устройство, авто-переподключение
       └─ ScaleSession — одна попытка подключения: фазы, bonding, MTU, notify, handshake, очередь команд
            ├─ BleTransport (interface) ← AndroidBleTransport (BluetoothGatt) / FakeBleTransport (тесты)
            └─ protocol: Crc16Modbus, FrameCodec, ScaleMessage, LegacyCodec
```
*Альтернатива:* перенести FastBle как в оригинале. Отказались: библиотека заброшена, не знает про API 33+ (`writeCharacteristic(..., value, type)`), и прячет очередь GATT-операций, которую нам всё равно нужно контролировать.

### 2. Транспорт — тонкая обёртка над BluetoothGatt с suspend-операциями
`BleTransport` предоставляет `connect()`, `discoverServices()`, `bondState/createBond()`, `requestMtu()`, `enableNotifications(service, char, indicate)`, `write(service, char, bytes)`, и `events: Flow<TransportEvent>` (notify-данные, разрыв, bond-изменения). Все GATT-операции сериализуются мьютексом (Android допускает только одну in-flight GATT-операцию). Каждая операция — `withTimeout`.

### 3. Сессия — корутинный конечный автомат
Последовательность и тайминги повторяют официальное приложение:
1. GATT connect → discover services. Есть `181D` → legacy-ветка (indicate `2A9D`, READY).
2. Иначе bond: `BOND_NONE` → `createBond()`; ждём `BONDED` (10 с на старт `BONDING`, 35 с на завершение). Если во время bonding GATT «сломался» — один rebuild GATT (оригинал делает до 2).
3. Пауза 500 мс → `requestMtu(247)` с таймаутом 2 с (ошибка не фатальна).
4. Задержка notify 1200 мс (1500 мс, если bond создавался сейчас) → CCCD на `FFF1`. Если данные пришли раньше колбэка CCCD — считаем канал готовым (так делает оригинал).
5. +500 мс → handshake: чтения `0x05` (обязательное), `0x13` (обязательное, если модель не известна из рекламы), `0x08`, `0x02`, `0x0C`. READY после успеха обязательных.

### 4. Очередь команд
Одна команда в полёте, ожидание ответа с совпадающими `(type, cmd)` 1500 мс, retry: 1 для чтений, 0 для записей. Coalescing: ключ `tare` (дубликат отбрасывается), `timer` (новая вытесняет ожидающие). `0x1C`/`0x0B`/`0x1A` — fire-and-forget (весы сразу рвут связь). Все незавершённые команды отменяются при смене сессии.

### 5. UI: Material 3 Expressive
`MaterialExpressiveTheme` + `MotionScheme.expressive()`, dynamic color на Android 12+. Главный экран: крупные цифры веса (`displayLarge`, tabular figures), `ButtonGroup` с тремя кнопками (Тара / Старт-Пауза `ToggleButton` / Сброс), `LoadingIndicator` в фазах подключения, `ListItem`-карточки на экранах поиска и настроек, `ModalBottomSheet`/диалоги для выбора. Навигация — Navigation Compose (три экрана: dashboard, scan, settings).

### 6. Хранение
DataStore Preferences: адрес, имя и модель последних весов. Больше ничего не храним — единицы и настройки всегда читаются с весов.

## Risks / Trade-offs

- [Точные значения батареи `0x05` (второй байт — зарядка?) и перегруза не подтверждены] → парсим как в оригинале, проверяем в `verify-real-scale-connection`.
- [Bonding на разных прошивках Android ведёт себя по-разному (двойной запрос пары, «тихий» BONDED без broadcast)] → опрос `bondState` по таймауту как в оригинале, один rebuild GATT.
- [Material 3 Expressive API помечены `@ExperimentalMaterial3ExpressiveApi`] → изолируем их в `ui/`, opt-in на уровне модуля.
- [Без foreground service соединение рвётся, когда система убивает процесс] → осознанный non-goal, авто-переподключение при следующем запуске.
