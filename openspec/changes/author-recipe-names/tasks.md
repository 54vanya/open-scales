# Tasks

## 1. Строки

- [x] 1.1 `values-ru/strings.xml`: семь названий по таблице из спеки (`recipe_hoffmann_title`, `recipe_hoffmann_one_cup_title`, `recipe_kasuya_title`, `recipe_hedrick_title`, `recipe_kasuya_switch_title`, `recipe_hoffmann_aeropress_title`, `recipe_hoffmann_french_press_title`); после « ·» и между инициалом и фамилией — ` `. Проверка: тест 2.1
- [x] 1.2 `values/strings.xml`: те же семь названий с автором латиницей («J. Hoffmann», «T. Kasuya», «L. Hedrick»), тоже с ` `. Проверка: тест 2.1
- [x] 1.3 `recipe_hedrick_description` (ru и en): убрать префикс «One and Done:», описание начинается с «Два цветения и один пролив.» / «Two blooms and one pour.». Проверка: `grep -n "One and Done" app/src/main/res/values*/strings.xml` находит только строки названий
- [x] 1.4 Остальные названия (`recipe_rao_title`, `recipe_kalita_title`, `recipe_chemex_title`, `recipe_inverted_aeropress_title`, `recipe_wac*_title`, `recipe_wbrc*_title`, `recipe_fukahori_switch_title`) не тронуты. Проверка: `git diff` по строкам показывает только восемь ресурсов из 1.1–1.3 в каждом файле

## 2. Тесты

- [x] 2.1 В `BuiltInRecipesTest` тест «author names come first»: читает `values/strings.xml` и `values-ru/strings.xml` (как тест про граммы в строках) и сверяет семь названий с таблицей из спеки целиком, включая неразрывный пробел; авторская часть до « · » в ru и en совпадает. Проверка: тест падает до правок 1.1–1.2 и проходит после
- [x] 2.2 `EditorScreenshotTest`: название копии «V60 по Хоффману (копия)» заменить на «The Ultimate V60 Technique · Дж. Хоффман (копия)». Проверка: тест компилируется, эталоны редактора обновляются в 3.2
- [x] 2.3 `RecipesScreenshotTest`: добавить снимок списка рецептов на узком экране (`NARROW`, 320 dp) — `recipes_narrow`. Проверка: на эталоне длинные названия перенесены без обрезки, «Дж. Хоффман» не разорван

## 3. Эталоны скриншотов

- [x] 3.1 `:app:verifyRoborazziDebug` — убедиться, что падают только снимки, где видно название встроенного рецепта (список, выбор рецепта, экраны варки, редактор копии, README). Проверка: список упавших сверок совпадает с ожидаемым, посторонних нет
- [x] 3.4 `BrewTopBar`: название до двух строк (`maxLines = 2`) вместо одной с многоточием. Проверка: на эталонах `brew_beans`, `brew_steps_*` заголовок «The Ultimate V60 Technique / · Дж. Хоффман» целиком, подпись шага под ним, в альбомных — одна строка
- [x] 3.2 `:app:recordRoborazziDebug --tests '*ScreenshotTest'` и просмотреть глазами каждый изменившийся эталон в `app/src/test/screenshots` и `docs/screenshots`: отличается только название и перенос строк под ним; заголовок варки («Зерно», «Шаги», альбомная ориентация) не обрезан и не наезжает на кнопки. Проверка: `git status` показывает только ожидаемые png; если заголовок варки выглядит плохо — показать пользователю до продолжения
- [x] 3.3 README: если в тексте упомянуты прежние названия рецептов — привести в соответствие. Проверка: `grep -n -i "hoffmann\|kasuya\|hedrick" README.md` не находит старых названий

## 4. Проверка

- [x] 4.1 Полная проверка: `./gradlew --no-watch-fs :app:verifyRoborazziDebug :app:assembleDebug :app:lintDebug` и `openspec validate --all --strict`
- [x] 4.2 Эмулятор, виртуальные весы: на русском и английском (настройка «Язык») пройти список рецептов, выбор рецепта при смене до старта и заголовок варки для «The Ultimate French Press Technique» — самого длинного названия; скопировать «One and Done» и убедиться, что копия называется «One and Done · Л. Хедрик (копия)»
- [ ] 4.3 Ручная проверка на телефоне: список рецептов и заголовок экрана варки читаются, длинные названия не обрезаны
