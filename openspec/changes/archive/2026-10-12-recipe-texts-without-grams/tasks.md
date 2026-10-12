# Tasks

## 1. Рецепт

- [x] 1.1 `BuiltInRecipes.littleAeropress`: вместо подсказки — `tare(10, recipe_note_remove_aeropress)` и шаг «Разбавьте» 15 с с рубежом 92 и новой заметкой; в комментарии — откуда 92 (150 г напитка минус ~58 г концентрата). Проверка: `:app:assembleDebug`
- [x] 1.2 Строки ru и en: новая `recipe_note_hot_water` («Горячей водой» / «With hot water»), из `recipe_wac2022_description` убрать фразу про 150 г, удалить `recipe_hint_dilute_150`. Проверка: `grep -rn "150" ` по строкам рецепта ничего не находит, `:app:lintDebug` без неиспользуемых ресурсов

## 2. Граммы в текстах других рецептов

- [x] 2.1 `BuiltInRecipes.hedrickV60`: у «Второго цветения» заметка `recipe_note_spiral_no_swirl`; строку `recipe_note_no_swirl` удалить (ru, en). Проверка: `:app:lintDebug` без неиспользуемых ресурсов
- [x] 2.2 Строки ru и en: `recipe_note_pulses` — «Небольшими порциями на светлые пятна» / «Small pulses onto the lighter spots»; `recipe_hoffmann_one_cup_description` — «Пять одинаковых проливов. …» / «Five equal pours. …»; `recipe_wbrc2022_description` — «Четыре одинаковых пролива каждые 30 секунд: …» / «Four equal pours every 30 seconds: …». Проверка: тест 3.2
- [x] 2.3 Поискать упоминания этих текстов в `openspec/specs` и README и привести в соответствие. Проверка: `grep -rn "25–50\|Ещё 45\|по 50 г" openspec/specs README.md` пуст

## 3. Тесты и эталоны

- [x] 3.1 `BuiltInRecipesTest`: ожидания Литтл — вода 186 г, 155 с; рубежи `listOf(94, 92)`; новый тест «little dilutes after a tare» (части, `targetsByPart` при дозе 20 г — 104 и 102, начало тары на 130 с). Проверка: `:app:testDebugUnitTest --tests '*BuiltInRecipesTest'`
- [x] 3.2 Тест «recipe strings carry no gram amounts»: в строках `recipe_*` файлов `values/strings.xml` и `values-ru/strings.xml` нет числа с «г»/«g». Проверка: тест падает до правок 1.2 и 2.x и проходит после
- [x] 3.3 `:app:verifyRoborazziDebug`; эталоны, где видны изменённые тексты или карточка Литтл (список рецептов, экраны варки, README), перезаписать и просмотреть глазами

## 4. Проверка

- [x] 4.1 Полная проверка: `./gradlew --no-watch-fs :app:verifyRoborazziDebug :app:assembleDebug :app:lintDebug` и `openspec validate --all --strict`
- [x] 4.2 Виртуальные весы (эмулятор): пройти рецепт Литтл — после «Прожмите» табло просит тару, после тары показывает «осталось до 92», `sim pour 92 10` доводит до цели, в конце «Готово» с итогом против 92 г
- [ ] 4.3 Ручная проверка на телефоне с весами: сварить рецепт, после долива 92 г в чашке около 150 г напитка; если расхождение больше ~10 г — поправить рубеж
