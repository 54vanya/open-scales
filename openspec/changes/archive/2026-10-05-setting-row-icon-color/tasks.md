# Tasks

## 1. Исправление

- [x] 1.1 `SettingRow`: окраска значка `LocalContentColor.current` вместо `Color.Unspecified` (разрушительное действие — цвет ошибки, как раньше). Проверка: сборка `:app:assembleDebug`
- [x] 1.2 `SettingsScreenshotTest.settingsDark` и эталон `settings_dark.png`: значки всех строк видны на тёмном фоне; эталон просмотрен глазами. Проверка: `:app:verifyRoborazziDebug`

## 2. Проверка

- [x] 2.1 Полная проверка: `./gradlew --no-watch-fs :app:verifyRoborazziDebug :app:assembleDebug :app:lintDebug` и `openspec validate --all --strict`
- [ ] 2.2 Ручная проверка на телефоне в тёмной теме: настройки приложения и настройки весов (подключённых и нет), значок «Забыть весы» — цвета ошибки
