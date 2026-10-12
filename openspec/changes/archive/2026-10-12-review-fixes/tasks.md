# Tasks

## 1. Исправления

- [x] 1.1 `DataStoreSavedDeviceStore.clear`: удалять только ключи весов
- [x] 1.2 `ScaleSession.timer`: применять состояние таймера после записи без подтверждения. Проверка: `ScaleRepositoryTest` «reset reaches the scale once and is not repeated at zero»
- [x] 1.3 `ScaleSession.deviceCommand`: отказ и несостоявшаяся запись оставляют сессию; «Выключить» ждёт подтверждения. Проверка: `ScaleSessionTest` «rejected factory reset keeps the session and reports the refusal»
- [x] 1.4 `BrewViewModel.tareScale`: идущая тара засчитывается для «Старт» и «Тара» шага. Проверка: `BrewViewModelTest` «start right after tare waits for the zero of that tare»
- [x] 1.5 `FrameAssembler` и его использование в сессии. Проверка: `FrameCodecTest`, `ScaleSessionTest` «reply split across two notifications is assembled»
- [x] 1.6 `BrewActivity`: `autoConnect()` на `ON_START`
- [x] 1.7 `ScaleRepository.runConnection`: отмена наблюдателей в `finally`
- [x] 1.8 `BleJournal`: счётчик изменений вместо копии буфера на запись; `JournalActivity`, `BleJournalTest`

## 2. Проверка

- [x] 2.1 Полная проверка: `./gradlew --no-watch-fs :app:verifyRoborazziDebug :app:assembleDebug :app:lintDebug` и `openspec validate --all --strict`
- [ ] 2.2 Ручная проверка на телефоне с весами: «Забыть весы» не сбрасывает настройки приложения; после подключения к весам с идущим таймером пауза → сброс → сброс не обнуляет вес; «Тара» и сразу «Старт» на «Шагах» — ожидание пролива остаётся
- [ ] 2.3 Ручная проверка: во время варки свернуть приложение, выключить и включить весы, вернуться — весы подключаются без выхода на главный экран
- [x] 2.4 Виртуальные весы (эмулятор): `sim reject all` — «Сброс к заводским» и «Выключить» в карточке дают снэкбар об отказе, весы остаются подключёнными; без отказов «Выключить» отключает весы
- [x] 2.5 Виртуальные весы (эмулятор): повторный «Сброс» после переподключения к идущему таймеру не тарирует (вес 200 г остаётся), «Старт» → «Сброс» в пределах секунды сбрасывает таймер весов; «Тара» и сразу «Старт» на «Шагах» — ожидание пролива остаётся, пролив запускает время
