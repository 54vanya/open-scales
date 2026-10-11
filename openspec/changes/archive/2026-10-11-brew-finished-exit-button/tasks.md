# Tasks

## 1. Кнопка

- [x] 1.1 `StepsControls`: в фазе `FINISHED` вместо `OutlinedButton` «Стоп» — заполненная `Button` «Выйти» (`brew_leave`) с тем же `onStop`. Проверка: сборка `:app:assembleDebug`
- [x] 1.2 Перезаписать эталоны `brew_finished.png` и `brew_finished_holding.png`, просмотреть глазами. Проверка: `:app:verifyRoborazziDebug`

## 2. Проверка

- [x] 2.1 Полная проверка: `./gradlew --no-watch-fs :app:verifyRoborazziDebug :app:assembleDebug :app:lintDebug` и `openspec validate --all --strict`
- [ ] 2.2 Ручная проверка на телефоне: доварить рецепт до «Готово» — внизу «Выйти» основного цвета, нажатие закрывает варку без диалога; в альбомной раскладке то же в столбце
