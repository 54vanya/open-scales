# Design

## Context

- `OpenScalesTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = true)` в `ui/theme/Theme.kt`: динамические цвета на Android 12+, иначе своя «кофейная» светлая и тёмная палитра. `isSystemInDarkTheme()` читает `uiMode` текущей конфигурации.
- XML-тема окна `Theme.OpenScales` — наследник `android:Theme.Material.Light.NoActionBar`, варианта для ночи нет.
- Все Activity вызывают `enableEdgeToEdge()` без параметров: стиль значков системных панелей там выбирается по `uiMode` конфигурации (`SystemBarStyle.auto`).
- `add-english-localization` вводит базовый `OpenScalesActivity` с общим `attachBaseContext` (обёртка контекста языком на Android < 13) и хранилище выбора по образцу «системный API на новых версиях, своё хранилище на старых».

## Goals / Non-Goals

**Goals:** одна точка правды о теме — `uiMode` конфигурации; тогда Compose, фон окна и системные панели следуют выбору без отдельного кода в каждом месте.

**Non-Goals:**
- отдельная «AMOLED»/чёрная тема, выбор произвольного акцентного цвета;
- смена темы по расписанию внутри приложения (есть у системы, «Как в системе» её подхватывает).

## Decisions

### Выбор темы меняет `uiMode` конфигурации, а не параметр `OpenScalesTheme`

Модель — `enum ThemeMode { SYSTEM, LIGHT, DARK }` (имя `AppTheme` уже занято обёрткой темы из раздела о цветах), хранилище — `ThemeModeStore` по образцу `AppLanguageStore`:

- **Android 12+ (`UiModeManager.setApplicationNightMode`):** `MODE_NIGHT_AUTO`/`NO`/`YES`. Это ночной режим, который система хранит для приложения: она сама пересоздаёт Activity, сохраняет выбор и применяет его до первого кадра (в том числе к splash-экрану Android 12). Метода чтения в публичном API нет (выяснилось при реализации), поэтому выбор для показа в настройках дополнительно хранится в `SharedPreferences`; источником правды это не становится, потому что менять ночной режим приложения может только само приложение — в системных настройках такого пункта нет.
- **Android 8–11:** выбор — в `SharedPreferences` (синхронное чтение в `attachBaseContext`); тот же `attachBaseContext` в `OpenScalesActivity`, что меняет язык, подменяет в конфигурации биты `UI_MODE_NIGHT_MASK` (`NO`/`YES`, при `SYSTEM` — не трогает). После смены — `recreate()` открытых Activity, как для языка.

Раз выбор уже в конфигурации, `OpenScalesTheme` не меняется: `isSystemInDarkTheme()` отражает выбор. *Альтернатива:* передавать `darkTheme` из настроек в `OpenScalesTheme` в каждой Activity. Отклонена: не исправляет фон окна при запуске и значки системных панелей, и выбор вычитывается из DataStore асинхронно — первый кадр был бы в теме системы.

*Альтернатива:* `AppCompatDelegate.setDefaultNightMode`. Отклонена по той же причине, что и для языка: требует AppCompat и `AppCompatActivity`.

### Тема окна — двухрежимная

`values/themes.xml`: `Theme.OpenScales` → наследник `android:Theme.Material.Light.NoActionBar` с `android:windowBackground` = фон светлой схемы; `values-night/themes.xml`: наследник `android:Theme.Material.NoActionBar` с фоном тёмной схемы. Ресурсы `-night` выбираются по `uiMode`, поэтому окно следует выбору на всех версиях. Цвета фона — близкие к `surface` запасной палитры, чтобы при динамических цветах переход к Compose был почти незаметен.

### Системные панели

`enableEdgeToEdge()` по умолчанию выбирает значки по `uiMode` ресурсов Activity, а он уже подменён, поэтому отдельный стиль не нужен. Если на эмуляторе окажется, что `SystemBarStyle.auto` смотрит на системный, а не на контекст Activity, передаём явный `SystemBarStyle.light/dark` по `resources.configuration.uiMode` в `OpenScalesActivity`.

### «Собственные цвета приложения»

`OpenScalesTheme` уже умеет оба варианта через `dynamicColor`. Значение приходит из настройки: общая обёртка `AppTheme { }` в `ui/theme` читает `StateFlow<Boolean>` хранилища оформления и передаёт `dynamicColor = !ownColors`; все Activity вызывают её вместо `OpenScalesTheme`. Превью по-прежнему `OpenScalesTheme(dynamicColor = false)`.

Хранение — `SharedPreferences` («appearance»), а не DataStore настроек приложения: значение нужно синхронно к первому кадру, иначе при выключенной настройке запуск на миг показывал бы собственную палитру, а потом перекрашивался. Хранилище оформления — `AppearanceStore` с функциями чтения и записи, как `LocalAppLanguageStore`; при записи обновляет `StateFlow`, поэтому открытые экраны перекрашиваются сразу, без пересоздания. Выбор темы на Android 8–11 ляжет в то же хранилище.

Строка — `Switch` с подсказкой «Иначе цвета берутся из обоев телефона»; показывается только при `SDK_INT >= 31`.

### Строка в настройках

Раздел «Приложение», сразу после «Язык»: `ChoiceRow` «Тема» с тремя вариантами «Системная»/«Светлая»/«Тёмная» (en: «System»/«Light»/«Dark»), в узком виде, как «Язык»: длинное «Как в системе» не помещается в сегмент. Строки — в `values` и `values-ru`, в духе `add-english-localization`.

## Risks / Trade-offs

- [`setApplicationNightMode` на Android 12+ пересоздаёт Activity сразу, в том числе экран настроек — пользователь видит мигание экрана] → это системное поведение, как при смене темы системы; состояние живёт во ViewModel и `ScaleRepository`.
- [Динамические цвета: фон окна в XML не совпадает с `surface` динамической схемы] → на один кадр при запуске оттенок фона может чуть отличаться; но светлота совпадает, вспышки нет.
- [Зависимость от `add-english-localization`] → реализуется после него; общий `attachBaseContext` меняет и язык, и `uiMode` одной конфигурацией.
