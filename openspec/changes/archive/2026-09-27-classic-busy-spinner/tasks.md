# Tasks

## 1. Спиннер

- [x] 1.1 `BusyIndicator` в `ui/components` на `CircularProgressIndicator` (24 dp, линия 3 dp); заменить `LoadingIndicator` в баннере подключения, заголовке экрана «Весы», строках весов и карточке весов; убрать упоминание из CLAUDE.md. Проверка: `grep LoadingIndicator` по коду пуст
- [x] 1.2 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — тесты зелёные, lint «No issues found»; эмулятор — спиннер в заголовке «Весы» во время поиска и в карточке подключающихся весов

## 2. Проверка

- [x] 2.1 (тебе) На телефоне: при подключении к весам в баннере главного экрана — классический спиннер, размер соразмерен тексту
