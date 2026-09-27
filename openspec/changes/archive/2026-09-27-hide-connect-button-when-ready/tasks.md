# Tasks

## 1. Баннер

- [x] 1.1 `ConnectBanner`: без кнопки, когда весы готовы и подключиться ничто не мешает. Проверка: запись экрана эмулятора при переподключении виртуальных весов (`sim drop` → `sim back`) — «Подключено» без кнопки, пока баннер гаснет
- [x] 1.2 `./gradlew --no-watch-fs :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`, `openspec validate --all --strict`

## 2. Проверка

- [x] 2.1 На телефоне с весами: при подключении кнопка «Подключить» не мигает
