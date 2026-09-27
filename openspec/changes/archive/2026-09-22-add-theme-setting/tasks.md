# Tasks

## 1. Выбор темы

- [x] 1.1 `ThemeMode` (SYSTEM/LIGHT/DARK) и `ThemeModeStore`: на Android 12+ — `UiModeManager.setApplicationNightMode` плюс выбор в `SharedPreferences` для показа, на 8–11 — только `SharedPreferences`; юнит-тест преобразования `AppTheme` ↔ режим ночи системы ↔ биты `uiMode` и хранилища на подделке. Проверка: тест зелёный
- [x] 1.2 В `attachBaseContext` базового `OpenScalesActivity` (из `add-english-localization`) на Android < 12 подменять `UI_MODE_NIGHT_MASK` по выбору; после смены — `recreate()` открытых Activity. Проверка: `assembleDebug`, приложение запускается
- [x] 1.3 Строка «Тема» в разделе «Приложение» после «Язык» (`ChoiceRow`: «Системная»/«Светлая»/«Тёмная», en: System/Light/Dark), строки в `values`/`values-ru`, экшен в `SettingsActivity`/ViewModel. Проверка: превью настроек

## 2. Окно и системные панели

- [x] 2.1 `values/themes.xml` с фоном окна светлой схемы и `values-night/themes.xml` на `android:Theme.Material.NoActionBar` с фоном тёмной схемы. Проверка: запуск в тёмной теме на эмуляторе — первый кадр тёмный (запись экрана, первые кадры)
- [x] 2.2 Значки системных панелей следуют выбранной теме: проверить `enableEdgeToEdge()` при теме приложения, отличной от системной; если значки не меняются — явный `SystemBarStyle` по `uiMode` в `OpenScalesActivity`. Проверка: скриншоты «система светлая, приложение тёмное» и наоборот — значки читаются

## 3. Цвета приложения

- [x] 3.1 `AppearanceStore` на `SharedPreferences` («appearance»): `ownColors: StateFlow<Boolean>` (по умолчанию `true`), `setOwnColors`; юнит-тест на подделке чтения/записи. Проверка: тест зелёный
- [x] 3.2 Обёртка `AppTheme { }` (`dynamicColor = !ownColors`), все Activity на ней вместо `OpenScalesTheme`. Проверка: `assembleDebug`
- [x] 3.3 Строка «Собственные цвета приложения» с подсказкой в разделе «Приложение» (только Android 12+), строки в `values`/`values-ru`. Проверка: эмулятор API 36 — по умолчанию «кофейная» палитра, выключение сразу перекрашивает в цвета обоев, после перезапуска с первого кадра; эмулятор API 30 — строки нет

## 4. Проверка

- [x] 4.1 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — тесты зелёные, lint «No issues found»
- [x] 4.2 Эмулятор API 36: «Тёмная» при светлой системе и «Светлая» при тёмной применяются сразу и переживают перезапуск; «Системная» следует переключению системной темы (`adb shell cmd uimode night yes/no`); ось показаний и цвета карточки читаются в обеих темах
- [x] 4.3 Эмулятор API 30: то же через собственное хранилище; открытые экраны после возврата в новой теме
- [ ] 4.4 (тебе) На телефоне с весами: смена темы во время пролива — соединение и таймер не сбрасываются
