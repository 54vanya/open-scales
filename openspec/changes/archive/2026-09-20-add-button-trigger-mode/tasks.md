# Tasks

## 1. Реализация

- [x] 1.1 `rememberPressAction` в `DashboardScreen`: действие на `PressInteraction.Press`, `onClick` как резерв без двойного срабатывания, сброс на `Cancel`; проверено сборкой и lint
- [x] 1.2 `AppSettings.triggerOnPress` (по умолчанию `true`), DataStore-ключ `trigger_on_press`, in-memory store; юнит-тест значений по умолчанию обновлён
- [x] 1.3 Строка «Срабатывание кнопок» (При касании / При отпускании) первой в разделе «Приложение»; `MainActivity` передаёт режим в `DashboardScreen`; проверено на эмуляторе (411 dp)

## 2. Проверка

- [ ] 2.1 На Pixel 9 Pro XL с весами по журналу BLE: в режиме «При касании» TX `write TIMER` раньше отпускания пальца (`input motionevent DOWN` … `UP`); в режиме «При отпускании» — после; увод пальца в режиме «При отпускании» не отправляет команду; двойной тап TalkBack отправляет команду один раз
