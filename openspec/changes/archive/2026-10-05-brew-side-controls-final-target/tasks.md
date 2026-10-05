# Tasks

## 1. Итог варки с целью

- [x] 1.1 Добавить `Recipe.finalTargetG(doseG)` и тесты в `RecipeTest`: Хоффман 15 г → 250, рецепт с шагом «Тара» → рубеж последней части, последняя часть без рубежей → `null`. Проверка: `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*RecipeTest'`
- [x] 1.2 Строки `brew_on_scale_time` и `brew_of_recipe` (`values-ru`, `values`), убрать неиспользуемые `brew_on_scale`/`brew_total_time`; в `PourBoard` итог — «на весах · время варки» и «из <цель> по рецепту» (без цели — пустая строка той же высоты). Проверка: превью `StepsFinishedPreview` показывает обе строки, `:app:lintDebug` без неиспользуемых строк

## 2. Кнопки сбоку в широком окне

- [x] 2.1 `StepsControls(vertical)`: общий блок состояний, кнопки рядом или столбцом, вес и время под кнопками; `StepsScreen` — `BoxWithConstraints` вокруг `Scaffold`, в широком окне без `bottomBar`, содержимое `Row` (табло | список | столбец кнопок), внутреннее деление по общему `wide`. Проверка: альбомное превью «Шагов» — кнопки справа, под табло пусто
- [x] 2.2 `BeansControls(vertical)` и та же раскладка в `BeansScreen` (содержимое с прокруткой слева, «Тара»/«Далее» столбцом справа). Проверка: альбомное превью «Зерна»
- [x] 2.3 Отступы столбца — от `Scaffold` (системные панели и вырез), прокрутка столбца при нехватке высоты; вырез проверить эмуляцией `adb shell cmd overlay enable com.android.internal.display.cutout.emulation.tall`. Проверка: на эмуляторе в альбомной ориентации (`adb shell settings put system user_rotation 1`) кнопки не под вырезом и панелью навигации, поворот во время пролива не сбрасывает время

## 3. Скриншоты и проверка

- [x] 3.1 В `ScreenshotTest` qualifier альбомного телефона (`ru-w914dp-h411dp-land-xxhdpi`); новые тесты `brew_steps_landscape` (до «Старт»), `brew_steps_landscape_pouring`, `brew_beans_landscape`; перезаписать `brew_finished` и `brew_finished_holding` при необходимости; эталоны просмотреть глазами. Проверка: `:app:verifyRoborazziDebug` зелёный
- [x] 3.2 Полная проверка: `./gradlew --no-watch-fs :app:verifyRoborazziDebug :app:assembleDebug :app:lintDebug` и `openspec validate --all --strict`
- [ ] 3.3 Ручная проверка на телефоне с весами: варка в альбомной ориентации (кнопки под большим пальцем, табло читается), итог показывает цель
