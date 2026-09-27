# Proposal

## Why

Вид экранов до сих пор проверялся вручную: эмулятор, скриншот, взгляд. Одобренный однажды экран ничем не был защищён от случайной поломки при следующих правках. Пользователь попросил: как только вид экрана одобрен, на него пишется тест, который проверяет его сам.

## What Changes

- Скриншот-тесты на Roborazzi + Robolectric: экраны рисуются в JVM без эмулятора и сверяются с эталонами `app/src/test/screenshots/*.png`.
- Первый набор эталонов — одобренные экраны: вкладка «Рецепты»; редактор (раскрытый шаг, ошибки, узкий экран); «Зерно»; «Шаги» до старта, на проливе, на ожидании, на ожидании аэропресса и после конца; окна «Выйти из рецепта?» и «Сменить рецепт».
- Полная проверка проекта (`CLAUDE.md`, `tools/dev.sh check`) — `verifyRoborazziDebug` вместо `testDebugUnitTest`: те же юнит-тесты плюс сверка.
- Окно «Выйти из рецепта?» вынесено из `BrewActivity` в `LeaveRecipeDialog`, чтобы его можно было нарисовать в тесте. Поведение не меняется.

## Capabilities

### New Capabilities

### Modified Capabilities

Нет: поведение приложения не меняется, это инструмент разработки (`skip_specs`).

## Impact

- `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`: плагин и библиотеки Roborazzi, Robolectric, Compose UI test (только для тестов).
- `app/src/test/resources/robolectric.properties`: SDK 34.
- `app/src/test/java/dev/openscales/ui/screenshots/*`, `app/src/test/screenshots/*.png`.
- `ui/brew/RecipePicker.kt`, `BrewActivity.kt`: `LeaveRecipeDialog`.
- `CLAUDE.md`, `tools/dev.sh`: команды проверки и правило.
