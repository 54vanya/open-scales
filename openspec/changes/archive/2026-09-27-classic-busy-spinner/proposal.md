# Proposal

## Why

Индикатор занятости — выразительный `LoadingIndicator` Material 3 Expressive: фигура, которая всё время меняет форму. Пользователь попросил более классический вид — обычный вращающийся спиннер, тоже из библиотеки Material.

## What Changes

- Во всех местах, где показывается занятость (баннер подключения на главном экране, заголовок экрана «Весы» во время поиска, строки запомненных и найденных весов, карточка подключающихся весов), вместо `LoadingIndicator` — круговой `CircularProgressIndicator` Material 3.
- Один общий компонент `BusyIndicator` (24 dp, толщина линии 3 dp), чтобы спиннер везде был одного размера.
- Требования не меняются: спеки говорят об «индикаторе занятости», не о его форме (`skip_specs`).

## Capabilities

### New Capabilities

_Нет._

### Modified Capabilities

_Нет._

## Impact

- `ui/components/Common.kt` — `BusyIndicator`; `DashboardScreen`, `ScanScreen`, `ScaleDetailsScreen` — замена `LoadingIndicator`.
- `CLAUDE.md` — `LoadingIndicator` убран из списка используемых Expressive-компонентов.
