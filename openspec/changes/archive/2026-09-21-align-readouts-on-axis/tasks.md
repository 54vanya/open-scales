# Tasks

## 1. Шрифт цифр

- [x] 1.1 `tools/make-digits-font.sh`: скачать `GoogleSansFlex[GRAD,ROND,opsz,slnt,wdth,wght].ttf` и `OFL.txt` из `google/fonts` по фиксированному коммиту, во временном venv с fontTools зафиксировать оси `wdth=100 GRAD=0 ROND=0 slnt=0`, урезать до `0-9 . : - − — пробел` с фичами `tnum,zero`, переименовать семейство в `Open Scales Digits`, положить в `app/src/main/res/font/readout_digits.ttf`. Проверка: скрипт печатает оси `opsz, wght`, фичи `tnum, zero`, размер ~23 КБ
- [x] 1.2 Положить `OFL.txt` в `app/src/main/assets/licenses/readout_digits_OFL.txt` и рядом со скриптом; добавить `make-digits-font` в список команд `tools/dev.sh` (или упомянуть в CLAUDE.md). Проверка: `unzip -l` собранного APK показывает шрифт и лицензию
- [x] 1.3 `Theme.kt`: два `FontFamily` на `R.font.readout_digits` (крупный — `opsz` 88, мелкий — `opsz` 32, `wght` 500); `WeightTextStyle` и `DigitsTextStyle` на них; функция, которая по флагу даёт `fontFeatureSettings` `"tnum, zero"` или `"tnum"`. Проверка: в превью дашборда цифры новым шрифтом, нули перечёркнуты

## 2. Настройка «Перечёркнутый ноль»

- [x] 2.1 `AppSettings.slashedZero = true`, ключ `slashed_zero`, `setSlashedZero` в `AppSettingsStore`, `DataStoreAppSettingsStore`, `InMemoryAppSettingsStore`; тесты в `AppSettingsTest`: по умолчанию включена, значение сохраняется — `./gradlew :app:testDebugUnitTest --tests '*AppSettingsTest'` зелёный
- [x] 2.2 Строка с `Switch` «Перечёркнутый ноль» в разделе «Приложение» `SettingsScreen` (строка в `strings.xml`, экшен в `SettingsActivity`/ViewModel по образцу `onKeepScreenOn`). Проверка: в превью настроек строка есть и переключается без подключённых весов
- [x] 2.3 Передать `slashedZero` в `DashboardScreen` параметром (как `triggerOnPress`) и применить к стилям таймера, веса и потока. Проверка: превью с `slashedZero = false` — нули без черты, числа на тех же местах

## 3. Геометрия

- [x] 3.1 Вынести расчёт в `ui/dashboard/ReadoutGeometry.kt`: чистая функция от измеренных px (шаблоны `000.0`/`00.00`/`00:00` крупным стилем, одна цифра, `oz` и `oz/s` стилями единиц, высоты подписей и потока, `unitGap`, `ReadoutGap`, ограничения) → масштаб крупного стиля, множитель единиц, `numberWidth`, `unitWidth`, `overhang`; проверить, что проект собирается
- [x] 3.2 Юнит-тест `ReadoutGeometryTest`: на ширине 320 dp и 412 dp блок плюс `2 * overhang` помещается; `numberWidth` не меньше ширины каждого шаблона; результат не зависит от показываемого значения и от текущей единицы (на вход идут только шаблоны); узкий экран ужимает масштаб, но не ниже 0.3 — `./gradlew :app:testDebugUnitTest --tests '*ReadoutGeometryTest'` зелёный

## 4. Вёрстка

- [x] 4.1 `ReadoutArea`/`ReadoutStyles`: мерить шаблоны через `TextMeasurer` стилями с текущим `slashedZero` (флаг в ключе `remember`), звать функцию из 3.1, отдавать стили и ширины колонок в `Dp`; удалить `WEIGHT_WIDTH_TEMPLATE = "0000.0"`, `UnitBaselineGap` и отдельный `flowScale` — проверка: `assembleDebug` без предупреждений о неиспользуемом
- [x] 4.2 Общий компонент строки на оси (числовая колонка `width(numberWidth).wrapContentWidth(End, unbounded = true)`, колонка единиц `unitGap + unitWidth`, выравнивание по базовой линии); перевести на него таймер (пустая колонка единиц), вес и поток, подписи «Таймер»/«Поток» — `textAlign = End` в числовой колонке. Проверка: в превью «подключено» правые края `1:15`, `18.3`, `2.4` и начала `g`, `g/s` на одних вертикалях
- [x] 4.3 Превью: добавить `1999.9 g` и режим унций (`70.55 oz`), прочерки без подключения, `slashedZero = false`; проверить в превью, что ведущая `1` выступает влево, ось на месте, ничего не обрезано, в том числе в `DashboardLargeFontPreview` и `DashboardSmallScreenPreview`
- [x] 4.4 Заглушка без значения — черты шириной в цифру (знак U+2212 из шрифта цифр): у веса 3 до точки, у потока 2, после точки — по знакам единицы (`−−−.−`, `−−.−`) вместо одного длинного тире; тест `FormatWeightTest` — `./gradlew :app:testDebugUnitTest --tests '*FormatWeightTest'` зелёный, на эмуляторе заглушки на оси
- [x] 4.5 Центрировать только колонку на 4 цифры: строки `side | numberWidth | side`, `side = max(unitGap + unitWidth, overhang)`, условие ширины в `readoutGeometry` и тест `ReadoutGeometryTest`. Проверка на Pixel: колонка x 312…1032, центр 672 при центре карточки 671,5; единицы кончаются у внутреннего края карточки
- [x] 4.6 Уменьшать стили в dp, а не умножать sp: с Android 14 крупный шрифт масштабируется нелинейно, и уменьшенный в sp текст рисовался крупнее рассчитанного — строка вылезала за колонки, её прижимало влево, поток обрезался снизу. Проверка: эмулятор 320/360/411/480 dp × шрифт 1.0/1.3/2.0 — ось совпадает до пикселя, центр колонки в пределах 1 dp, поток не обрезан

## 5. Проверка

- [x] 5.1 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — тесты зелёные, lint «No issues found»; `:app:assembleRelease` — R8 не выкидывает шрифт, размер APK вырос не больше чем на ~30 КБ
- [x] 5.2 Эмулятор `emu-narrow` (320 dp) через `tools/dev.sh`: скриншот главного экрана, группа помещается, значения не обрезаны, нули перечёркнуты
- [ ] 5.3 На телефоне с весами: вес растёт от `0.0` до сотен граммов, таймер переходит `9:59 → 10:00`, поток меняется — ось и единицы не сдвигаются; переключение г/унции и «Перечёркнутый ноль» не двигает ось; после перезапуска настройка сохранена
