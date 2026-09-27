# Design

## Context

- Строки: один `res/values/strings.xml` (~105 строк, русские). Экраны — Compose, строки через `stringResource`; Activity — `ComponentActivity` (не AppCompat). `minSdk 26`, `targetSdk 37`, AGP 9.4.
- Ошибки идут в интерфейс строками. `ScaleSession.fail()`/`finish()` кладут в `ScaleState.error` сообщение исключения, а оно бывает русским (`BleException("сопряжение отклонено")`) или техническим английским из `AndroidBleTransport` (`"Connect timeout 15000ms"`, `"… failed, status=133"`). `ScaleViewModel.launchReporting` отправляет в снекбар `e.message` у `CommandException` (`"response timeout"`, `"device rejected command 0x02"`), `ScanViewModel` — сообщение `BleException("Bluetooth выключен")`.
- Единицы: `WeightUnit.symbol` (`"g"`/`"oz"`) из пакета `protocol` используется в интерфейсе напрямую, поток — через `flow_rate_unit = "%1$s/s"`. `ReadoutArea` меряет ширину колонки единиц по всем `WeightUnit` × (вес, поток).
- Строки в коде без ресурсов: `"Журнал BLE"` в `DashboardScreen`, весь `JournalActivity` (debug).

## Goals / Non-Goals

**Goals:**
- английский как базовый язык, русский как перевод; lint не пропускает строку без перевода;
- ни одна фраза из кода не попадает к пользователю напрямую;
- выбор языка в приложении, согласованный с системным языком приложения на Android 13+.

**Non-Goals:**
- другие языки, RTL;
- форматирование чисел по локали (разделитель — точка в обоих языках, по решению пользователя);
- перевод названий моделей (`ScaleModel.displayName`), имени весов, названия приложения;
- перевод экспорта журнала BLE (он для разработчика, пишется по-английски) и логов logcat.

## Decisions

### Ресурсы: английский базовый, русский — `values-ru`

`res/values/strings.xml` переписывается на английский, текущие русские строки (с исправлениями ниже) переезжают в `res/values-ru/strings.xml`. Строки журнала BLE — в `src/debug/res/values{,-ru}/strings.xml`: в release их нет вместе с экраном. Ключи строк не меняются, кроме новых и переименованных ниже.

`androidResources { generateLocaleConfig = true }` плюс `res/resources.properties` с `unqualifiedResLocale=en`: AGP сам соберёт `locales_config.xml` из папок `values-*` и пропишет `android:localeConfig`. Без этого Android 13+ не покажет приложение в системном выборе языка приложений. *Альтернатива:* писать `locales_config.xml` руками. Отклонена: его легко забыть обновить при добавлении языка.

Lint `MissingTranslation` уже error по умолчанию; проверка «lint No issues found» из CLAUDE.md его ловит.

### Выбор языка: системный язык приложения на 13+, свой на 8–12

Модель — `enum AppLanguage { SYSTEM, RUSSIAN, ENGLISH }` с тегами `""`, `"ru"`, `"en"`. Хранилище — интерфейс `AppLanguageStore` (`current(): AppLanguage`, `set(AppLanguage)`) с двумя реализациями:

- **Android 13+ (`LocaleManager`):** читает и пишет `applicationLocales`. Это та же настройка, что в системных «Приложения → Open Scales → Язык», поэтому выбор в системе и в приложении совпадают сами. Система сама пересоздаёт Activity при смене. Своего хранения нет, чтобы не было двух источников правды.
- **Android 8–12:** системного языка приложения нет. Выбор хранится в `SharedPreferences` (синхронное чтение нужно в `attachBaseContext`, а DataStore асинхронный), а язык применяется обёрткой контекста: у каждой Activity `attachBaseContext(base.withAppLanguage(store.current()))` через `createConfigurationContext`. После смены приложение пересоздаёт открытые Activity (`recreate()`), видимая пересоздаётся сразу, остальные — при возврате. Для этого базовый класс `OpenScalesActivity : ComponentActivity` с общим `attachBaseContext`, от него наследуются `MainActivity`, `ScanActivity`, `SettingsActivity`, `JournalActivity`.

*Альтернатива:* `AppCompatDelegate.setApplicationLocales` из AppCompat (сама хранит выбор на старых версиях). Отклонена: тянет AppCompat и требует перевести все Activity на `AppCompatActivity` ради одной настройки, а на 13+ делает то же, что `LocaleManager`.

Пересоздание Activity не трогает соединение и таймер: они живут в `ScaleRepository` (`appScope`), это уже проверено поворотом экрана.

Строка в настройках: раздел «Приложение», первая строка, `ChoiceRow` с тремя вариантами, как «Срабатывание кнопок». Подписи: «Системный»/«System» — на текущем языке (длинное «Как в системе» не помещалось в сегмент на 3 варианта и обрезалось); «Русский» и «English» — всегда на своём языке (не переводятся, `translatable="false"`).

### Ошибки: причина вместо фразы

- **Подключение:** `ScaleState.error: String?` → `error: ConnectionError?`, где `ConnectionError` — sealed-тип в `session`: `NotTimemore`, `PairingNotStarted`, `PairingRejected`, `PairingTimeout`, `NoModel`, `Lost`, `DroppedWhileConnecting`, `Failed(detail: String)`. Код, который сейчас бросает `BleException` с русским текстом, бросает исключение с причиной (`ConnectionException(reason)`). Прочие исключения транспорта превращаются в `Failed(detail = message)`. Текст исключения по-прежнему уходит в журнал и logcat.
- **Интерфейс:** `@Composable fun ConnectionError.text(): String` в `ui/components` — строка из ресурсов. У `Failed` — «Не удалось подключиться» / «Couldn't connect», техническую подробность не показываем: её видно в журнале, а пользователю фраза `status=133` ничего не даёт.
- **Команды:** `ScaleViewModel` отправляет в канал ошибок не строку, а `@StringRes Int` (или маленький sealed `UiError`), выбранный по `CommandException.Kind`: `TIMEOUT` — «Весы не ответили», `REJECTED` — «Весы отклонили команду», `TRANSPORT` — «Нет связи с весами», `CANCELLED` без подключения — «Весы не подключены», прочие исключения — «Не удалось выполнить команду». Строку из ресурса снекбар получает в Activity (`stringResource`/`getString`), поэтому она на текущем языке.
- **Поиск:** `ScanViewModel.state.error` — тоже причина: `BluetoothOff` или `ScanFailed`.

Сопоставление причин строкам — чистые функции `→ @StringRes Int`, покрываются юнит-тестом (каждая причина получает свою строку, `Failed` не протекает текстом исключения).

### Единицы из ресурсов

Новые строки: `unit_gram_symbol` (g / г), `unit_ounce_symbol` (oz / oz), `flow_unit_gram` (g/s / г/с), `flow_unit_ounce` (oz/s / oz/s). `flow_rate_unit = "%1$s/s"` удаляется: русская «г/с» не собирается из «г» + «/s». Функции `WeightUnit.symbolRes()` / `WeightUnit.flowSymbolRes()` в `ui/components` рядом с `labelRes`. `WeightUnit.symbol` в протоколе остаётся (логи, тесты). `ReadoutArea` меряет те же четыре строки на текущем языке; язык входит в ключ `remember` неявно — строки берутся через `stringResource`, а при смене языка Activity пересоздаётся.

### Вычитка русского

| Ключ | Было | Стало | Почему |
|---|---|---|---|
| `setting_standby` | Авто-отключение | Автоотключение | «авто-» пишется слитно |
| `overload` | Перегруз | Перегрузка | «перегруз» — разговорное |
| `setting_app_keep_screen_on` | Не гасить экран на главном | Не гасить экран на главном экране | оборванная фраза |
| `action_factory_reset` | Сброс к заводским | Сброс к заводским настройкам | оборванная фраза |
| `setting_unit` | Единицы | Единицы измерения | неполное название |
| `brightness_dark/medium/bright` | Тёмный / Средний / Яркий | Низкая / Средняя / Высокая | согласование с «Яркость» (ж. р.) |
| `phase_subscribing` | Подписка на данные… | Настройка приёма данных… | «подписка» — программистский жаргон |
| `scan_rssi` | %1$s · %2$d dBm | %1$s · %2$d дБм | русское обозначение единицы |
| `scan_saved` | Запомненные весы | Мои весы | «запомненные» звучит канцелярски (замечание пользователя); приложение помнит одни весы — ваши |
| единицы | g, g/s | г, г/с | русские обозначения (унции остаются oz) |

Остальные строки проверены и соответствуют нормам. Английские формулировки выбираются как естественный интерфейсный английский, например «Keep main screen awake», «Auto power-off», «Factory reset», «Buttons act on: Press / Release», «Connect the scale to change its settings», «My scale».

## Risks / Trade-offs

- [Смена `ScaleState.error` на тип затрагивает тесты сессии и репозитория, которые сравнивают текст ошибки] → тесты сравнивают причину; это надёжнее, чем текст.
- [Пересоздание Activity на Android 8–12 при смене языка теряет несохранённое состояние экрана настроек, например открытый диалог переименования] → смена языка — отдельное действие на том же экране, диалога в этот момент нет. Остальное состояние живёт во ViewModel и `ScaleRepository`.
- [На Android 8–12 системные диалоги (разрешения, включение Bluetooth) остаются на языке системы] → так ведёт себя любое приложение без поддержки со стороны системы. На 13+ это решает `LocaleManager`.
- [Английский становится базовым: у пользователя с системным языком не из двух поддерживаемых интерфейс переключится с русского на английский после обновления] → это и есть цель; при желании выбирается «Русский» в настройках.
- [Проверить Android 8–12 на реальном телефоне нечем] → эмулятор с API 30 в задачах.
