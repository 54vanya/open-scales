# Tasks

## 1. Инструмент

- [x] 1.1 Подключить Roborazzi, Robolectric, Compose UI test; `robolectric.properties` (SDK 34); папка эталонов. Проверка: `recordRoborazziDebug` пишет `recipes.png`
- [x] 1.2 `ScreenshotTest`: окружение снимков (native-графика, простой `Application`, тема, русский, размеры телефона и узкого экрана). Проверка: снимки совпадают с видом на эмуляторе

## 2. Эталоны одобренных экранов

- [x] 2.1 Вынести `LeaveRecipeDialog` из `BrewActivity`. Проверка: поведение окна на эмуляторе прежнее
- [x] 2.2 Снимки: «Рецепты»; редактор (раскрытый шаг, ошибки, узкий); «Зерно»; «Шаги» до старта, пролив, ожидание, ожидание аэропресса, итог; окна выхода и смены рецепта. Каждый эталон просмотрен. Проверка: 12 эталонов в `app/src/test/screenshots`
- [x] 2.3 Сверка ловит поломку: лишний отступ в окне выхода роняет ровно `brew_leave_dialog` и даёт `*_compare.png`; после отката сверка зелёная

## 3. Процесс

- [x] 3.1 `CLAUDE.md` и `tools/dev.sh check`: полная проверка через `verifyRoborazziDebug`, правило про эталоны. Проверка: `./gradlew --no-watch-fs :app:verifyRoborazziDebug :app:assembleDebug :app:lintDebug`, `openspec validate --all --strict`
